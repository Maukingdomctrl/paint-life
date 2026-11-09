package com.mauforge.paintlife.exercise

import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.SessionClock
import com.mauforge.paintlife.core.time.SessionTimer
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
 * The one open exercise session, for the whole app process. It outlives screens, so leaving the
 * counter, switching tabs or turning the screen off never stops or loses a session.
 *
 * - Count, pause and resume change the in-memory state at once (taps feel instant) and queue a
 *   save. Saves run one at a time in order, always writing the newest state, and a save can never
 *   touch a session that has already ended.
 * - While a session runs, its timer is checkpointed every [CHECKPOINT_INTERVAL_MS] and whenever
 *   the app goes to the background ([checkpoint]), so a reboot loses at most that much time.
 * - Creating the controller is "the app process started": a session left running is restored as
 *   paused from its last checkpoint (see [ExerciseRepository.recoverOpenSessions]).
 *
 * Timing uses [SessionClock] (monotonic); the screen only reads it to draw the clock.
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
    }

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until the stored session (if any) has been recovered and loaded. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val mutex = Mutex()
    private val pendingSave = Channel<ExerciseSessionEntity>(Channel.CONFLATED)

    init {
        scope.launch {
            mutex.withLock {
                _live.value = repository.recoverOpenSessions()?.let { Live(it, CounterState(it.repetitions)) }
            }
            _loaded.value = true
            launch {
                for (session in pendingSave) mutex.withLock { repository.saveProgress(session) }
            }
            launch { checkpointWhileRunning() }
        }
    }

    /** Active time of [live] now, from the monotonic clock. */
    fun activeMillis(live: Live): Long = SessionTimer.activeMillis(live.session.timer, clock.elapsedRealtime(), clock.bootCount())

    /** Starts a step-up session, or returns the one already open. */
    suspend fun startVolume(): Live {
        _loaded.first { it }
        return mutex.withLock {
            _live.value ?: run {
                val timer = SessionTimer.started(clock.elapsedRealtime(), clock.bootCount())
                val session = repository.startVolumeSession(timer)
                Live(session, CounterState(session.repetitions)).also { _live.value = it }
            }
        }
    }

    /** Adds [amount] reps. Ignored while paused (the buttons are disabled then too). */
    fun add(amount: Int) = change { live, now, boot ->
        if (live.isPaused) return@change null
        live.withCounter(SessionCounter.add(live.counter, amount), now, boot)
    }

    /** Reverts the last count change. */
    fun undo() = change { live, now, boot ->
        if (!live.counter.canUndo) return@change null
        live.withCounter(SessionCounter.undo(live.counter), now, boot)
    }

    /** Sets the count by hand. False (and nothing changes) when [reps] is out of range. */
    fun correct(reps: Int): Boolean {
        if (reps !in 0..SessionCounter.MAX_REPS) return false
        change { live, now, boot -> live.withCounter(SessionCounter.correct(live.counter, reps), now, boot) }
        return true
    }

    fun pause() = change { live, now, boot ->
        if (live.isPaused) return@change null
        live.copy(session = live.session.copy(status = SessionStatus.PAUSED, timer = SessionTimer.paused(live.session.timer, now, boot)))
    }

    fun resume() = change { live, now, boot ->
        if (!live.isPaused) return@change null
        live.copy(session = live.session.copy(status = SessionStatus.RUNNING, timer = SessionTimer.resumed(live.session.timer, now, boot)))
    }

    /** Count and clock back to zero; the same session carries on (running or paused as it was). */
    fun reset() = change { live, now, boot ->
        val timer = if (live.isPaused) SessionTimer.stopped(boot) else SessionTimer.started(now, boot)
        Live(live.session.copy(repetitions = 0, timer = timer), SessionCounter.reset())
    }

    /** Saves the current active time (called regularly and when the app goes to the background). */
    fun checkpoint() = change { live, now, boot ->
        if (live.isPaused) return@change null
        live.copy(session = live.session.copy(timer = SessionTimer.checkpoint(live.session.timer, now, boot)))
    }

    /** Ends the open session as completed, unless nothing was counted. */
    suspend fun finish(): FinishOutcome {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value ?: return@withLock FinishOutcome.NothingOpen
            if (live.counter.reps == 0) return@withLock FinishOutcome.NothingCounted
            val timer = SessionTimer.paused(live.session.timer, clock.elapsedRealtime(), clock.bootCount())
            val result = repository.finish(live.session.id, live.counter.reps, timer)
            clearIf(live.session.id)
            result?.let(FinishOutcome::Finished) ?: FinishOutcome.NothingOpen
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
        _live.map { it != null && !it.isPaused }.distinctUntilChanged().collectLatest { running ->
            while (running) {
                delay(CHECKPOINT_INTERVAL_MS)
                checkpoint()
            }
        }
    }

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
    }
}
