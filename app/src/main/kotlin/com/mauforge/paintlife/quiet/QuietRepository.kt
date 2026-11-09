package com.mauforge.paintlife.quiet

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.core.time.TimerCheckpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Stored quiet sessions. Timing values come in from [QuietSessionController], which owns the
 * monotonic clock; this class stamps wall-clock instants with their zone and keeps the rules
 * that need the database: one open session at a time, and an ended session never changes again.
 */
class QuietRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao = db.quietDao()

    /** Per-day totals for sessions that started between [from] and [to] (inclusive). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, QuietDayTotals>> =
        dao.observeFinished(from, to).map(QuietRules::dayTotals)

    /** One day's totals (empty when nothing counted). */
    fun observeDay(date: LocalDate): Flow<QuietDayTotals> =
        observeDays(date, date).map { it[date] ?: QuietDayTotals.empty(date) }

    suspend fun session(id: String): QuietSessionEntity? = dao.session(id)

    /**
     * Run once when the app process starts. A session still open now was left by a process that
     * died (Android closed the app, or the phone restarted): the service and its timer lived in
     * that process. It is saved as [SessionStatus.INCOMPLETE] with the active time of its last
     * checkpoint, and never counts. The one exception is a session whose saved time had already
     * reached the target (the app was closed just before saving "completed"): that is completed.
     */
    suspend fun recoverOpenSessions() = db.withTransaction {
        dao.openSessions().forEach { open ->
            val timer = SessionTimer.recoveredAsPaused(open.timer)
            val reachedTarget = timer.activeDurationMillis >= open.targetDurationMillis
            if (reachedTarget) {
                val capped = timer.atMost(open.targetDurationMillis)
                // The last save was made this long after the target was reached.
                val overshoot = timer.activeDurationMillis - open.targetDurationMillis
                end(open, SessionStatus.COMPLETED, capped, endedAt = open.updatedAt.minusMillis(overshoot), endedZone = open.updatedZoneId)
            } else {
                end(open, SessionStatus.INCOMPLETE, timer, endedAt = time.now(), endedZone = time.zone())
            }
        }
    }

    /** Starts a session, or returns the open one: a second Start never makes a second session. */
    suspend fun start(timer: TimerCheckpoint, targetMillis: Long = QuietRules.TARGET_MILLIS): QuietSessionEntity = db.withTransaction {
        dao.openSessions().firstOrNull() ?: run {
            val now = time.now()
            val zone = time.zone()
            QuietSessionEntity(
                id = UUID.randomUUID().toString(),
                status = SessionStatus.RUNNING,
                startedAt = now,
                zoneId = zone,
                startDate = now.atZone(zone).toLocalDate(),
                endedAt = null,
                endedZoneId = null,
                targetDurationMillis = targetMillis,
                timer = timer,
                updatedAt = now,
                updatedZoneId = zone,
            ).also { dao.insert(it) }
        }
    }

    /** Saves an open session's status and timer. False if it is no longer open. */
    suspend fun saveProgress(session: QuietSessionEntity): Boolean {
        if (!session.isOpen) return false
        val timer = session.timer
        return dao.saveOpenProgress(
            id = session.id,
            status = session.status,
            activeDurationMillis = timer.activeDurationMillis,
            activeAtSegmentStartMillis = timer.activeAtSegmentStartMillis,
            segmentStartElapsedRealtime = timer.segmentStartElapsedRealtime,
            bootCount = timer.bootCount,
            updatedAt = time.now(),
            updatedZoneId = time.zone(),
        ) > 0
    }

    /**
     * Ends an open session as completed: its active time is exactly the target, and it ended
     * [overshootMillis] before now (the moment it really reached the target, even if this save
     * ran late). Null if the session had already ended.
     */
    suspend fun complete(id: String, timer: TimerCheckpoint, overshootMillis: Long): QuietSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen } ?: return@withTransaction null
        val endedAt = time.now().minusMillis(overshootMillis.coerceAtLeast(0L))
        end(stored, SessionStatus.COMPLETED, timer.atMost(stored.targetDurationMillis), endedAt, time.zone())
    }

    /** Finish early (or a pause that ran past the limit): the minutes are kept, never counted as completed. */
    suspend fun endEarly(id: String, timer: TimerCheckpoint): QuietSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen } ?: return@withTransaction null
        end(stored, SessionStatus.ENDED_EARLY, timer, time.now(), time.zone())
    }

    /** Cancel: the open session is removed. Returns it as it was, so Undo can put it back. */
    suspend fun discard(id: String): QuietSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen } ?: return@withTransaction null
        if (dao.delete(id) > 0) stored else null
    }

    /**
     * Undo of Cancel: [session] is open again, paused with [timer]. False if another session
     * was started in the meantime (only one can be open) or it somehow still exists.
     */
    suspend fun restore(session: QuietSessionEntity, timer: TimerCheckpoint): QuietSessionEntity? = db.withTransaction {
        if (dao.openSessions().isNotEmpty() || dao.session(session.id) != null) return@withTransaction null
        session.copy(status = SessionStatus.PAUSED, timer = timer, updatedAt = time.now(), updatedZoneId = time.zone())
            .also { dao.insert(it) }
    }

    private suspend fun end(
        stored: QuietSessionEntity,
        status: SessionStatus,
        timer: TimerCheckpoint,
        endedAt: Instant,
        endedZone: ZoneId,
    ): QuietSessionEntity = stored.copy(
        status = status,
        timer = timer,
        endedAt = endedAt,
        endedZoneId = endedZone,
        updatedAt = time.now(),
        updatedZoneId = time.zone(),
    ).also { dao.update(it) }

    /** A closed timer whose active time is no more than [limit]. */
    private fun TimerCheckpoint.atMost(limit: Long): TimerCheckpoint {
        val active = activeDurationMillis.coerceAtMost(limit)
        return copy(activeDurationMillis = active, activeAtSegmentStartMillis = active, segmentStartElapsedRealtime = null)
    }
}
