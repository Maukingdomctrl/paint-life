package com.mauforge.paintlife.exercise

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.core.time.TimerCheckpoint
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Stored exercise sessions and the Cat League. Timing values come in from
 * [ExerciseSessionController], which owns the monotonic clock; this class stamps wall-clock
 * instants with their zone and keeps the rules that need the database: one open session at a
 * time, only unlocked levels can be challenged, and results only ever move forward.
 */
class ExerciseRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao = db.exerciseDao()

    fun observeLifetimeReps(): Flow<Int> = dao.observeLifetimeReps()

    fun observeDayTotals(date: LocalDate): Flow<ExerciseDayTotals> = dao.observeDayTotals(date)

    fun observeFinishedSessions(): Flow<List<ExerciseSessionEntity>> = dao.observeFinishedSessions()

    fun observeLevels(): Flow<List<ChallengeLevelEntity>> = dao.observeLevels()

    fun observeTimedBests(): Flow<List<PersonalBestEntity>> = dao.observeTimedBests()

    /** The newest winning timed result that ended after [after], if any. */
    fun observeLatestWinAfter(after: Instant): Flow<ExerciseSessionEntity?> = dao.observeLatestWinAfter(after)

    suspend fun openSession(): ExerciseSessionEntity? = dao.openSessions().firstOrNull()

    suspend fun session(id: String): ExerciseSessionEntity? = dao.session(id)

    suspend fun levels(): List<ChallengeLevelEntity> = dao.levels()

    suspend fun timedBest(level: Int): PersonalBestEntity? = dao.personalBest(PersonalBestKeys.timed(level))

    /**
     * Run once when the app process starts, with the monotonic reading [now] and [boot] count.
     *
     * - A step-up session left running comes back paused with the active time of its last
     *   checkpoint.
     * - A timed challenge from this boot comes back paused with its clock still exact (the
     *   challenge clock never stops). One from before a phone restart can no longer be timed: it
     *   ends as [SessionStatus.INCOMPLETE] with its reps kept (removed if it had none).
     * - Only one session can be open; should older ones exist, they are ended the same way rather
     *   than left hanging (step-ups completed, timed challenges incomplete).
     *
     * Returns the session that is still open.
     */
    suspend fun recoverOpenSessions(now: Long, boot: Int?): ExerciseSessionEntity? = db.withTransaction {
        val open = dao.openSessions()
        open.drop(1).forEach { endInterrupted(it, if (it.type == ExerciseSessionType.VOLUME) SessionStatus.COMPLETED else SessionStatus.INCOMPLETE) }
        val newest = open.firstOrNull() ?: return@withTransaction null
        when (newest.type) {
            ExerciseSessionType.VOLUME -> {
                if (newest.status != SessionStatus.RUNNING) return@withTransaction newest
                newest.copy(status = SessionStatus.PAUSED, timer = SessionTimer.recoveredAsPaused(newest.timer))
                    .stamped()
                    .also { dao.updateSession(it) }
            }
            ExerciseSessionType.TIMED_CHALLENGE -> {
                if (!SessionTimer.isMeasurable(newest.timer, now, boot)) {
                    endInterrupted(newest, SessionStatus.INCOMPLETE)
                    return@withTransaction null
                }
                if (newest.status == SessionStatus.PAUSED) return@withTransaction newest
                newest.copy(status = SessionStatus.PAUSED).stamped().also { dao.updateSession(it) }
            }
        }
    }

    private suspend fun endInterrupted(session: ExerciseSessionEntity, status: SessionStatus) {
        if (status == SessionStatus.INCOMPLETE && session.repetitions == 0) {
            dao.deleteSession(session.id)
            return
        }
        val now = time.now()
        val zone = time.zone()
        dao.updateSession(
            session.copy(
                status = status,
                timer = SessionTimer.recoveredAsPaused(session.timer),
                endedAt = now,
                endedZoneId = zone,
                updatedAt = now,
                updatedZoneId = zone,
            ),
        )
    }

    /** Starts a step-up session, or returns the open one: a second Start never makes a second session. */
    suspend fun startVolumeSession(timer: TimerCheckpoint): ExerciseSessionEntity = db.withTransaction {
        dao.openSessions().firstOrNull() ?: newSession(ExerciseSessionType.VOLUME, level = null, timer).also { dao.insertSession(it) }
    }

    /**
     * Starts a timed challenge against [level], or returns the open session if there is one.
     * Null when the level does not exist or is still locked: a locked cat can never be challenged.
     */
    suspend fun startTimedSession(level: Int, timer: TimerCheckpoint): ExerciseSessionEntity? = db.withTransaction {
        dao.openSessions().firstOrNull()?.let { return@withTransaction it }
        val target = dao.level(level)?.takeIf { it.isUnlocked } ?: return@withTransaction null
        newSession(ExerciseSessionType.TIMED_CHALLENGE, target.level, timer).also { dao.insertSession(it) }
    }

    private fun newSession(type: ExerciseSessionType, level: Int?, timer: TimerCheckpoint): ExerciseSessionEntity {
        val now = time.now()
        val zone = time.zone()
        return ExerciseSessionEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            status = SessionStatus.RUNNING,
            challengeLevel = level,
            startedAt = now,
            zoneId = zone,
            startDate = now.atZone(zone).toLocalDate(),
            endedAt = null,
            endedZoneId = null,
            repetitions = 0,
            timer = timer,
            notes = null,
            updatedAt = now,
            updatedZoneId = zone,
        )
    }

    /** Saves an open session's count, status and timer. False if it is no longer open. */
    suspend fun saveProgress(session: ExerciseSessionEntity): Boolean {
        if (!session.isOpen) return false
        val timer = session.timer
        return dao.saveOpenProgress(
            id = session.id,
            status = session.status,
            repetitions = session.repetitions,
            activeDurationMillis = timer.activeDurationMillis,
            activeAtSegmentStartMillis = timer.activeAtSegmentStartMillis,
            segmentStartElapsedRealtime = timer.segmentStartElapsedRealtime,
            bootCount = timer.bootCount,
            updatedAt = time.now(),
            updatedZoneId = time.zone(),
        ) > 0
    }

    /**
     * Ends an open session as completed with [repetitions] and the closed [timer]: a step-up
     * session on Finish, or a timed challenge that reached 1,000. Returns the stored result, or
     * null if the session had already ended (a repeated Finish does nothing).
     */
    suspend fun finish(id: String, repetitions: Int, timer: TimerCheckpoint): ExerciseSessionEntity? =
        end(id, SessionStatus.COMPLETED, repetitions, timer)

    /**
     * "Stop challenge": the attempt ends without a time. Its reps still count as step-ups; an
     * attempt with no reps leaves no trace. Null if nothing open was stopped.
     */
    suspend fun stopTimed(id: String, repetitions: Int, timer: TimerCheckpoint): ExerciseSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen && it.type == ExerciseSessionType.TIMED_CHALLENGE } ?: return@withTransaction null
        if (repetitions == 0) {
            dao.deleteSession(id)
            return@withTransaction stored
        }
        end(id, SessionStatus.CANCELLED, repetitions, timer)
    }

    private suspend fun end(id: String, status: SessionStatus, repetitions: Int, timer: TimerCheckpoint): ExerciseSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen } ?: return@withTransaction null
        val now = time.now()
        val zone = time.zone()
        stored.copy(
            status = status,
            repetitions = repetitions,
            timer = timer,
            endedAt = now,
            endedZoneId = zone,
            updatedAt = now,
            updatedZoneId = zone,
        ).also { dao.updateSession(it) }
    }

    /**
     * Undo within the 5-second window after a timed challenge reached 1,000: the session is open
     * again exactly as [reopened] describes (its clock never stopped). Only a timed challenge that
     * is completed (or whose finish has not been saved yet) can be reopened, and the controller
     * only does so before its result is applied.
     */
    suspend fun reopenTimed(reopened: ExerciseSessionEntity): Boolean = db.withTransaction {
        val stored = dao.session(reopened.id) ?: return@withTransaction false
        val reopenable = stored.status == SessionStatus.COMPLETED || stored.isOpen
        if (stored.type != ExerciseSessionType.TIMED_CHALLENGE || !reopenable) return@withTransaction false
        dao.updateSession(reopened.copy(endedAt = null, endedZoneId = null).stamped())
        true
    }

    /**
     * Brings personal bests and unlocks in line with the completed timed challenges, in one
     * transaction. Idempotent: run after each result's undo window and at every app start (which
     * applies a result whose window was cut short by the app being closed).
     *
     * - A level's best time is its fastest completed run, and it only ever goes down.
     * - A level is beaten by a run strictly faster than its benchmark (a tie is not a win). The
     *   first such run stamps beatenAt and unlocks the next level, at the moment it ended.
     * - Nothing is ever locked again, and Champion (the last level) has no next level.
     */
    suspend fun applyTimedResults() = db.withTransaction {
        val levels = dao.levels()
        levels.forEach { level ->
            val fastest = dao.fastestCompleted(level.level) ?: return@forEach
            val key = PersonalBestKeys.timed(level.level)
            val best = dao.personalBest(key)
            val duration = fastest.timer.activeDurationMillis
            if (best == null || duration < best.value) {
                dao.upsertPersonalBest(
                    PersonalBestEntity(
                        key = key,
                        kind = PersonalBestKind.TIMED_CHALLENGE_TIME,
                        challengeLevel = level.level,
                        value = duration,
                        sessionId = fastest.id,
                        achievedAt = fastest.endedAt ?: fastest.updatedAt,
                        zoneId = fastest.endedZoneId ?: fastest.zoneId,
                    ),
                )
            }
            val win = dao.firstWin(level.level, level.benchmarkMillis) ?: return@forEach
            val wonAt = win.endedAt ?: win.updatedAt
            val wonZone = win.endedZoneId ?: win.zoneId
            if (level.beatenAt == null) {
                dao.updateLevel(level.copy(beatenAt = wonAt, beatenZoneId = wonZone))
            }
            val next = dao.level(level.level + 1)
            if (next != null && !next.isUnlocked) {
                dao.updateLevel(next.copy(isUnlocked = true, unlockedAt = wonAt, unlockedZoneId = wonZone))
            }
        }
    }

    /** Removes an open session that has no reps (Finish with nothing counted, then Discard). */
    suspend fun discardEmpty(id: String): Boolean = db.withTransaction {
        val stored = dao.session(id) ?: return@withTransaction false
        if (!stored.isOpen || stored.repetitions != 0) return@withTransaction false
        dao.deleteSession(id) > 0
    }

    /**
     * Edits a finished session from history: the note always, the count only for step-up
     * sessions (a timed result stays exactly as it was achieved). False if nothing was saved.
     */
    suspend fun updateFinished(id: String, repetitions: Int, rawNote: String): Boolean = db.withTransaction {
        val stored = dao.session(id)?.takeIf { !it.isOpen } ?: return@withTransaction false
        if (repetitions !in 0..SessionCounter.MAX_REPS) return@withTransaction false
        val reps = if (stored.type == ExerciseSessionType.VOLUME) repetitions else stored.repetitions
        val note = SessionNote.clean(rawNote)
        if (reps == stored.repetitions && note == stored.notes) return@withTransaction true
        dao.updateSession(stored.copy(repetitions = reps, notes = note).stamped())
        true
    }

    /** Deletes a finished step-up session. Timed results can't be deleted. */
    suspend fun deleteFinished(id: String): Boolean = db.withTransaction {
        val stored = dao.session(id) ?: return@withTransaction false
        if (stored.isOpen || stored.type != ExerciseSessionType.VOLUME) return@withTransaction false
        dao.deleteSession(id) > 0
    }

    private fun ExerciseSessionEntity.stamped(): ExerciseSessionEntity = copy(updatedAt = time.now(), updatedZoneId = time.zone())
}

/** Stable personal-best keys: each record exists at most once. */
object PersonalBestKeys {
    fun timed(level: Int): String = "timed_level_$level"
}

/** Session notes: trimmed, single spaces kept as typed, empty means no note. */
object SessionNote {
    const val MAX_LENGTH = 280

    fun clean(raw: String): String? = raw.trim().take(MAX_LENGTH).trim().ifEmpty { null }
}
