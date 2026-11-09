package com.mauforge.paintlife.exercise

import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.SessionClock
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimerCheckpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What Finish did. */
sealed interface FinishOutcome {
    data class Finished(val session: ExerciseSessionEntity) : FinishOutcome

    /** Nothing was counted: the screen offers to discard instead. Nothing changed. */
    data object NothingCounted : FinishOutcome

    /** No session was open (a repeated tap). */
    data object NothingOpen : FinishOutcome
}

/**
 * A timed challenge that just reached 1,000. Its result is saved but not yet applied; for
 * [ExerciseSessionController.UNDO_WINDOW_MS] Undo can reopen the attempt as [reopen].
 */
data class FinishWindow(val finished: ExerciseSessionEntity, val reopen: ExerciseSessionController.Live)

/** A timed challenge whose result has been applied (best time and any unlock). */
data class TimedResult(val sessionId: String, val level: Int)

/**
 * The one open exercise session, for the whole app process. It outlives screens, so leaving the
 * counter, switching tabs or turning the screen off never stops or loses a session.
 *
 * - Count, pause and resume change the in-memory state at once (taps feel instant) and queue a
 *   save. Saves run one at a time in order, always writing the newest state, and a save can never
 *   touch a session that has already ended.
 * - While a session's clock runs, its timer is checkpointed every [CHECKPOINT_INTERVAL_MS] and
 *   whenever the app goes to the background ([checkpoint]), so a reboot loses at most that much.
 * - Creating the controller is "the app process started": open sessions are recovered (see
 *   [ExerciseRepository.recoverOpenSessions]) and timed results are reconciled
 *   ([ExerciseRepository.applyTimedResults]), which applies a result whose undo window was cut
 *   short by the app being closed.
 *
 * Timed challenges: pausing only stops counting, the challenge clock keeps running. The tap that
 * reaches 1,000 stops the clock and saves the run at once; its best time and any unlock are
 * applied after a 5-second window in which Undo can take that tap back.
 *
 * Timing uses [SessionClock] (monotonic); screens only read it to draw the clock.
 */
@Singleton
class ExerciseSessionController @Inject constructor(
    private val repository: ExerciseRepository,
    private val clock: SessionClock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** The open session and its in-memory count with undo history. */
    data class Live(val session: ExerciseSessionEntity, val counter: CounterState) {
        val isPaused: Boolean get() = session.status == SessionStatus.PAUSED
        val isTimed: Boolean get() = session.type == ExerciseSessionType.TIMED_CHALLENGE

        /** Timed challenges keep their clock running while paused. */
        val clockRunning: Boolean get() = isTimed || !isPaused
    }

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until stored sessions have been recovered and results reconciled. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _finishWindow = MutableStateFlow<FinishWindow?>(null)

    /** Set during the undo window after a timed challenge reached 1,000. */
    val finishWindow: StateFlow<FinishWindow?> = _finishWindow.asStateFlow()

    private val _lastResult = MutableStateFlow<TimedResult?>(null)

    /** The timed result most recently applied in this process. */
    val lastResult: StateFlow<TimedResult?> = _lastResult.asStateFlow()

    /** Length of the undo window; tests shorten it. */
    internal var undoWindowMillis: Long = UNDO_WINDOW_MS

    private val mutex = Mutex()
    private val pendingSave = Channel<ExerciseSessionEntity>(Channel.CONFLATED)

    init {
        scope.launch {
            mutex.withLock {
                val open = repository.recoverOpenSessions(clock.elapsedRealtime(), clock.bootCount())
                repository.applyTimedResults()
                _live.value = open?.let { Live(it, CounterState(it.repetitions)) }
            }
            _loaded.value = true
            launch {
                for (session in pendingSave) mutex.withLock { repository.saveProgress(session) }
            }
            launch { checkpointWhileRunning() }
        }
    }

    /** Active time of [live] now, from the monotonic clock. Timed: wall time since the start. */
    fun activeMillis(live: Live): Long = SessionTimer.activeMillis(live.session.timer, clock.elapsedRealtime(), clock.bootCount())

    /** Starts a step-up session, or returns the one already open. */
    suspend fun startVolume(): Live? = start { timer -> repository.startVolumeSession(timer) }

    /**
     * Starts a timed challenge against [level], or returns the session already open. Null when
     * the level is locked (or a just-finished challenge is still in its undo window).
     */
    suspend fun startTimed(level: Int): Live? = start { timer -> repository.startTimedSession(level, timer) }

    private suspend fun start(create: suspend (timer: TimerCheckpoint) -> ExerciseSessionEntity?): Live? {
        _loaded.first { it }
        return mutex.withLock {
            _live.value?.let { return@withLock it }
            if (_finishWindow.value != null) return@withLock null
            val session = create(SessionTimer.started(clock.elapsedRealtime(), clock.bootCount())) ?: return@withLock null
            Live(session, CounterState(session.repetitions)).also { _live.value = it }
        }
    }

    /** Adds [amount] reps. Ignored while paused. A timed challenge stops at 1,000 and finishes. */
    fun add(amount: Int) = count { live -> if (live.isPaused) null else SessionCounter.add(live.counter, amount) }

    /** Reverts the last count change. */
    fun undo() = count { live -> if (live.counter.canUndo) SessionCounter.undo(live.counter) else null }

    /**
     * Sets the count by hand. False (and nothing changes) when [reps] is out of range: 0 to
     * 10,000 for step-ups, 0 to 1,000 for a timed challenge (where 1,000 finishes it).
     */
    fun correct(reps: Int): Boolean {
        val live = _live.value ?: return false
        if (reps !in 0..maxReps(live)) return false
        count { SessionCounter.correct(it.counter, reps) }
        return true
    }

    private fun maxReps(live: Live): Int = if (live.isTimed) TIMED_TARGET else SessionCounter.MAX_REPS

    /** Applies a count change; reaching 1,000 in a timed challenge starts its finish instead. */
    private fun count(next: (Live) -> CounterState?) {
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        var before: Live? = null
        var after: Live? = null
        _live.update { current ->
            before = current
            val counter = current?.let(next)?.let { capped(current, it) }
            after = counter?.let { current.withCounter(it, now, boot) }
            after ?: current
        }
        val changed = after ?: return
        if (changed.isTimed && changed.counter.reps >= TIMED_TARGET) {
            beginTimedFinish(previous = before!!, reached = changed, now = now, boot = boot)
        } else {
            pendingSave.trySend(changed.session)
        }
    }

    private fun capped(live: Live, counter: CounterState): CounterState =
        if (live.isTimed && counter.reps > TIMED_TARGET) counter.copy(reps = TIMED_TARGET) else counter

    fun pause() = change { live, now, boot ->
        if (live.isPaused) return@change null
        val timer = if (live.isTimed) live.session.timer else SessionTimer.paused(live.session.timer, now, boot)
        live.copy(session = live.session.copy(status = SessionStatus.PAUSED, timer = timer))
    }

    fun resume() = change { live, now, boot ->
        if (!live.isPaused) return@change null
        val timer = if (live.isTimed) live.session.timer else SessionTimer.resumed(live.session.timer, now, boot)
        live.copy(session = live.session.copy(status = SessionStatus.RUNNING, timer = timer))
    }

    /**
     * Count and clock back to zero; the same session carries on. A timed challenge restarts the
     * attempt with its clock running; a paused step-up session stays paused.
     */
    fun reset() = change { live, now, boot ->
        val timer = if (live.isPaused && !live.isTimed) SessionTimer.stopped(boot) else SessionTimer.started(now, boot)
        Live(live.session.copy(repetitions = 0, timer = timer), SessionCounter.reset())
    }

    /** Saves the current active time (called regularly and when the app goes to the background). */
    fun checkpoint() = change { live, now, boot ->
        if (!live.clockRunning) return@change null
        live.copy(session = live.session.copy(timer = SessionTimer.checkpoint(live.session.timer, now, boot)))
    }

    /** Ends the open step-up session as completed, unless nothing was counted. */
    suspend fun finish(): FinishOutcome {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value?.takeIf { !it.isTimed } ?: return@withLock FinishOutcome.NothingOpen
            if (live.counter.reps == 0) return@withLock FinishOutcome.NothingCounted
            val timer = SessionTimer.paused(live.session.timer, clock.elapsedRealtime(), clock.bootCount())
            val result = repository.finish(live.session.id, live.counter.reps, timer)
            clearIf(live.session.id)
            result?.let(FinishOutcome::Finished) ?: FinishOutcome.NothingOpen
        }
    }

    /**
     * "Stop challenge" (after confirmation): the timed attempt ends without a time. Its reps
     * still count as step-ups. False if no timed challenge was open.
     */
    suspend fun stopChallenge(): Boolean {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value?.takeIf { it.isTimed } ?: return@withLock false
            val timer = SessionTimer.paused(live.session.timer, clock.elapsedRealtime(), clock.bootCount())
            val stopped = repository.stopTimed(live.session.id, live.counter.reps, timer)
            clearIf(live.session.id)
            stopped != null
        }
    }

    /**
     * The tap that reached 1,000: the clock stops on it and the run is saved straight away. Its
     * best time and any unlock wait [UNDO_WINDOW_MS], so Undo can still take the tap back.
     */
    private fun beginTimedFinish(previous: Live, reached: Live, now: Long, boot: Int?) {
        val finished = reached.session.copy(timer = SessionTimer.paused(reached.session.timer, now, boot))
        val window = FinishWindow(finished, previous)
        _finishWindow.value = window
        _live.update { if (it === reached) null else it }
        scope.launch {
            // Undo may already have taken the tap back before this save got its turn.
            mutex.withLock { if (_finishWindow.value === window) repository.finish(finished.id, TIMED_TARGET, finished.timer) }
            delay(undoWindowMillis)
            mutex.withLock {
                if (_finishWindow.value !== window) return@withLock
                repository.applyTimedResults()
                // Result first, then close the window, so a screen never sees neither.
                _lastResult.value = TimedResult(finished.id, finished.challengeLevel ?: 0)
                _finishWindow.value = null
            }
        }
    }

    /**
     * Undo during the finish window: the attempt is open again as it was before the last tap,
     * and its clock (which never really stopped) carries on. False once the window has closed.
     */
    suspend fun undoFinish(): Boolean {
        val window = _finishWindow.value ?: return false
        return mutex.withLock {
            if (_finishWindow.value !== window) return@withLock false
            val reopen = window.reopen
            val reopened = repository.reopenTimed(reopen.session)
            if (reopened) {
                _finishWindow.value = null
                _live.value = reopen
                pendingSave.trySend(reopen.session)
            }
            reopened
        }
    }

    /** Removes the open session if it has no reps. False if there was nothing to discard. */
    suspend fun discard(): Boolean {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value ?: return@withLock false
            if (live.counter.reps != 0) return@withLock false
            repository.saveProgress(live.session)
            val removed = repository.discardEmpty(live.session.id)
            if (removed) clearIf(live.session.id)
            removed
        }
    }

    /** Writes the current state now (tests; the app relies on the queued saves). */
    suspend fun flush() {
        _loaded.first { it }
        mutex.withLock { _live.value?.let { repository.saveProgress(it.session) } }
    }

    private fun Live.withCounter(counter: CounterState, now: Long, boot: Int?): Live = Live(
        session = session.copy(repetitions = counter.reps, timer = SessionTimer.checkpoint(session.timer, now, boot)),
        counter = counter,
    )

    /** Applies [transform] to the open session (null result = no change) and queues a save. */
    private fun change(transform: (live: Live, now: Long, boot: Int?) -> Live?) {
        val now = clock.elapsedRealtime()
        val boot = clock.bootCount()
        var changed: Live? = null
        _live.update { current ->
            changed = current?.let { transform(it, now, boot) }
            changed ?: current
        }
        changed?.let { pendingSave.trySend(it.session) }
    }

    private fun clearIf(id: String) {
        _live.update { if (it?.session?.id == id) null else it }
    }

    private suspend fun checkpointWhileRunning() {
        _live.map { it?.clockRunning == true }.distinctUntilChanged().collectLatest { running ->
            while (running) {
                delay(CHECKPOINT_INTERVAL_MS)
                checkpoint()
            }
        }
    }

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
        const val UNDO_WINDOW_MS = 5_000L
        const val TIMED_TARGET = ExerciseSessionUiState.TIMED_TARGET_REPS
    }
}
