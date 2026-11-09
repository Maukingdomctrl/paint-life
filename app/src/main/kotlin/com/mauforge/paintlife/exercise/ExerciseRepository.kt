package com.mauforge.paintlife.exercise

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.core.time.TimerCheckpoint
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Stored exercise sessions. Timing values come in from [ExerciseSessionController], which owns
 * the monotonic clock; this class stamps wall-clock instants with their zone and keeps the rules
 * that need the database: one open session at a time, and finished timed results stay as they are.
 */
class ExerciseRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao = db.exerciseDao()

    fun observeLifetimeReps(): Flow<Int> = dao.observeLifetimeReps()

    fun observeDayTotals(date: LocalDate): Flow<ExerciseDayTotals> = dao.observeDayTotals(date)

    fun observeFinishedSessions(): Flow<List<ExerciseSessionEntity>> = dao.observeFinishedSessions()

    suspend fun openSession(): ExerciseSessionEntity? = dao.openSessions().firstOrNull()

    /**
     * Run once when the app process starts. A session that was running when the app was closed
     * or the phone restarted comes back paused, with the reps and active time of its last saved
     * checkpoint. Only one session can be open; should older ones exist, they are finished with
     * their reps kept rather than left hanging.
     */
    suspend fun recoverOpenSessions(): ExerciseSessionEntity? = db.withTransaction {
        val open = dao.openSessions()
        val now = time.now()
        val zone = time.zone()
        open.drop(1).forEach { extra ->
            dao.updateSession(
                extra.copy(
                    status = SessionStatus.COMPLETED,
                    timer = SessionTimer.recoveredAsPaused(extra.timer),
                    endedAt = now,
                    endedZoneId = zone,
                    updatedAt = now,
                    updatedZoneId = zone,
                ),
            )
        }
        val newest = open.firstOrNull() ?: return@withTransaction null
        if (newest.status != SessionStatus.RUNNING) return@withTransaction newest
        newest.copy(
            status = SessionStatus.PAUSED,
            timer = SessionTimer.recoveredAsPaused(newest.timer),
            updatedAt = now,
            updatedZoneId = zone,
        ).also { dao.updateSession(it) }
    }

    /** Starts a step-up session, or returns the open one: a second Start never makes a second session. */
    suspend fun startVolumeSession(timer: TimerCheckpoint): ExerciseSessionEntity = db.withTransaction {
        dao.openSessions().firstOrNull() ?: run {
            val now = time.now()
            val zone = time.zone()
            ExerciseSessionEntity(
                id = UUID.randomUUID().toString(),
                type = ExerciseSessionType.VOLUME,
                status = SessionStatus.RUNNING,
                challengeLevel = null,
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
            ).also { dao.insertSession(it) }
        }
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
     * Ends an open session as completed with [repetitions] and the closed [timer]. Returns the
     * stored result, or null if the session had already ended (a repeated Finish does nothing).
     */
    suspend fun finish(id: String, repetitions: Int, timer: TimerCheckpoint): ExerciseSessionEntity? = db.withTransaction {
        val stored = dao.session(id)?.takeIf { it.isOpen } ?: return@withTransaction null
        val now = time.now()
        val zone = time.zone()
        stored.copy(
            status = SessionStatus.COMPLETED,
            repetitions = repetitions,
            timer = timer,
            endedAt = now,
            endedZoneId = zone,
            updatedAt = now,
            updatedZoneId = zone,
        ).also { dao.updateSession(it) }
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
        dao.updateSession(
            stored.copy(repetitions = reps, notes = note, updatedAt = time.now(), updatedZoneId = time.zone()),
        )
        true
    }

    /** Deletes a finished step-up session. Timed results can't be deleted. */
    suspend fun deleteFinished(id: String): Boolean = db.withTransaction {
        val stored = dao.session(id) ?: return@withTransaction false
        if (stored.isOpen || stored.type != ExerciseSessionType.VOLUME) return@withTransaction false
        dao.deleteSession(id) > 0
    }
}

/** Session notes: trimmed, single spaces kept as typed, empty means no note. */
object SessionNote {
    const val MAX_LENGTH = 280

    fun clean(raw: String): String? = raw.trim().take(MAX_LENGTH).trim().ifEmpty { null }
}
