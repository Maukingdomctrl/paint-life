package com.mauforge.paintlife.quiet

import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.SessionClock
import com.mauforge.paintlife.core.time.SessionTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What Finish did. */
sealed interface QuietFinish {
    /** Saved as a shorter session: its minutes count, it is not a completed session. */
    data class EndedEarly(val session: QuietSessionEntity) : QuietFinish

    /** The target had already been reached: saved as completed. */
    data class Completed(val session: QuietSessionEntity) : QuietFinish

    /** Less than a second of quiet: nothing was saved. */
    data object NothingSaved : QuietFinish

    /** No session was open (a repeated tap). */
    data object NothingOpen : QuietFinish
}

/** A cancelled session that Undo can still put back (paused, as it was). */
data class CancelledSession(val session: QuietSessionEntity)

/**
 * The one open quiet session, for the whole app process. It outlives screens, and the
 * foreground service ([QuietTimerService]) keeps the process alive while a session is open.
 *
 * - Timing uses the monotonic [SessionClock] through [SessionTimer]: paused time is never added,
 *   and screen-off or background time is counted exactly.
 * - While running, the timer is checkpointed every [CHECKPOINT_INTERVAL_MS] and when the app goes
 *   to the background, and a watcher completes the session the moment it reaches its target.
 * - A session paused for [QuietRules.PAUSE_LIMIT_MILLIS] is saved as ended early (or removed, if it
 *   had less than a second of quiet, exactly as Finish would).
 * - Creating the controller is "the app process started": any session still open was left by a
 *   process that died, and is saved as incomplete ([QuietRepository.recoverOpenSessions]).
 */
@Singleton
class QuietSessionController @Inject constructor(
    private val repository: QuietRepository,
    private val clock: SessionClock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** The open session, and the monotonic reading at which it was paused (null while running). */
    data class Live(val session: QuietSessionEntity, val pausedAtElapsed: Long?) {
        val isPaused: Boolean get() = session.status == SessionStatus.PAUSED
    }

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** False until sessions left open by an earlier process have been saved as incomplete. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _completed = MutableStateFlow<QuietSessionEntity?>(null)

    /**
     * The session that just reached its target, until the user taps Done. Set before the session
     * leaves [live], so an observer of both never sees the session end without its completion
     * (the service posts the 5:00 alert from this).
     */
    val completed: StateFlow<QuietSessionEntity?> = _completed.asStateFlow()

    private val mutex = Mutex()
    private val pendingSave = Channel<QuietSessionEntity>(Channel.CONFLATED)

    init {
        scope.launch {
            mutex.withLock { repository.recoverOpenSessions() }
            _loaded.value = true
            launch {
                for (session in pendingSave) mutex.withLock { repository.saveProgress(session) }
            }
            launch { watchDeadlines() }
        }
    }

    /** Active time of [live] now, from the monotonic clock. */
    fun activeMillis(live: Live): Long = SessionTimer.activeMillis(live.session.timer, clock.elapsedRealtime(), clock.bootCount())

    /** Time left of [live]'s target. */
    fun remainingMillis(live: Live): Long = (live.session.targetDurationMillis - activeMillis(live)).coerceAtLeast(0L)

    /** Starts a session, or returns the one already open. */
    suspend fun start(): Live {
        _loaded.first { it }
        return mutex.withLock {
            _live.value?.let { return@withLock it }
            _completed.value = null
            val session = repository.start(SessionTimer.started(clock.elapsedRealtime(), clock.bootCount()))
            val paused = if (session.status == SessionStatus.PAUSED) clock.elapsedRealtime() else null
            Live(session, paused).also { _live.value = it }
        }
    }

    fun pause() = change { live, now, boot ->
        if (live.isPaused) return@change null
        Live(live.session.copy(status = SessionStatus.PAUSED, timer = SessionTimer.paused(live.session.timer, now, boot)), pausedAtElapsed = now)
    }

    /** Resumes a paused session. One paused past the limit is ended early instead. */
    fun resume() {
        var tooLong = false
        change { live, now, boot ->
            if (!live.isPaused) return@change null
            if (pausedTooLong(live, now)) {
                tooLong = true
                return@change null
            }
            Live(live.session.copy(status = SessionStatus.RUNNING, timer = SessionTimer.resumed(live.session.timer, now, boot)), pausedAtElapsed = null)
        }
        if (tooLong) scope.launch { checkDeadlines() }
    }

    /** Saves the current active time (called regularly and when the app goes to the background). */
    fun checkpoint() = change { live, now, boot ->
        if (live.isPaused) return@change null
        live.copy(session = live.session.copy(timer = SessionTimer.checkpoint(live.session.timer, now, boot)))
    }

    /** Finish: ends the open session early, keeping its minutes. */
    suspend fun finish(): QuietFinish {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value ?: return@withLock QuietFinish.NothingOpen
            val now = clock.elapsedRealtime()
            val boot = clock.bootCount()
            val timer = SessionTimer.paused(live.session.timer, now, boot)
            when {
                timer.activeDurationMillis >= live.session.targetDurationMillis ->
                    completeLocked(live, now, boot)?.let(QuietFinish::Completed) ?: QuietFinish.NothingOpen
                timer.activeDurationMillis < MIN_SAVED_MILLIS -> {
                    repository.discard(live.session.id)
                    clearIf(live.session.id)
                    QuietFinish.NothingSaved
                }
                else -> {
                    val ended = repository.endEarly(live.session.id, timer)
                    clearIf(live.session.id)
                    ended?.let(QuietFinish::EndedEarly) ?: QuietFinish.NothingOpen
                }
            }
        }
    }

    /** Cancel: the open session is removed at once. Returns it for Undo; null if nothing was open. */
    suspend fun cancel(): CancelledSession? {
        _loaded.first { it }
        return mutex.withLock {
            val live = _live.value ?: return@withLock null
            val timer = SessionTimer.paused(live.session.timer, clock.elapsedRealtime(), clock.bootCount())
            val removed = repository.discard(live.session.id)
            clearIf(live.session.id)
            removed?.let { CancelledSession(it.copy(status = SessionStatus.PAUSED, timer = timer)) }
        }
    }

    /**
     * Undo of Cancel: the session is open again, paused with the time it had. False if another
     * session has been started since.
     */
    suspend fun undoCancel(cancelled: CancelledSession): Boolean {
        _loaded.first { it }
        return mutex.withLock {
            if (_live.value != null) return@withLock false
            val restored = repository.restore(cancelled.session, cancelled.session.timer) ?: return@withLock false
            _completed.value = null
            _live.value = Live(restored, pausedAtElapsed = clock.elapsedRealtime())
            true
        }
    }

    /** Done on the completion screen. */
    fun acknowledgeCompleted() {
        _completed.value = null
    }

    /**
     * Completes a running session that has reached its target, and ends one paused past the
     * limit. Runs from the deadline watcher, and whenever the phone may have slept through it.
     */
    suspend fun checkDeadlines() {
        _loaded.first { it }
        withContext(NonCancellable) {
            mutex.withLock {
                val live = _live.value ?: return@withLock
                val now = clock.elapsedRealtime()
                val boot = clock.bootCount()
                if (!live.isPaused && activeMillis(live) >= live.session.targetDurationMillis) {
                    completeLocked(live, now, boot)
                } else if (pausedTooLong(live, now)) {
                    // As with Finish: under a second of quiet leaves nothing behind.
                    if (live.session.timer.activeDurationMillis < MIN_SAVED_MILLIS) {
                        repository.discard(live.session.id)
                    } else {
                        repository.endEarly(live.session.id, live.session.timer)
                    }
                    clearIf(live.session.id)
                }
            }
        }
    }

    /** Writes the current state now (tests; the app relies on the queued saves). */
    suspend fun flush() {
        _loaded.first { it }
        mutex.withLock { _live.value?.let { repository.saveProgress(it.session) } }
    }

    private suspend fun completeLocked(live: Live, now: Long, boot: Int?): QuietSessionEntity? {
        val timer = SessionTimer.paused(live.session.timer, now, boot)
        val overshoot = timer.activeDurationMillis - live.session.targetDurationMillis
        val done = repository.complete(live.session.id, timer, overshoot)
        if (done != null) _completed.value = done
        clearIf(live.session.id)
        return done
    }

    private fun pausedTooLong(live: Live, now: Long): Boolean {
        val pausedAt = live.pausedAtElapsed ?: return false
        return live.isPaused && now - pausedAt >= QuietRules.PAUSE_LIMIT_MILLIS
    }

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

    /**
     * Sleeps until the next thing that can happen to the open session (its target, the pause
     * limit, or the next checkpoint), then acts. Restarted whenever the session starts, pauses,
     * resumes or ends; checkpoints alone don't restart it.
     */
    private suspend fun watchDeadlines() {
        _live.map { live -> live?.let { Triple(it.session.id, it.isPaused, it.pausedAtElapsed) } }
            .distinctUntilChanged()
            .collectLatest { key ->
                if (key == null) return@collectLatest
                while (true) {
                    val live = _live.value ?: return@collectLatest
                    val now = clock.elapsedRealtime()
                    val untilDeadline = if (live.isPaused) {
                        (live.pausedAtElapsed ?: now) + QuietRules.PAUSE_LIMIT_MILLIS - now
                    } else {
                        remainingMillis(live)
                    }
                    delay(untilDeadline.coerceIn(1L, CHECKPOINT_INTERVAL_MS))
                    checkDeadlines()
                    checkpoint()
                }
            }
    }

    companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L

        /** Finishing with less quiet than this saves nothing. */
        const val MIN_SAVED_MILLIS = 1_000L
    }
}
