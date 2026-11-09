package com.mauforge.paintlife.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mauforge.paintlife.core.database.SessionStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reps and session count for one local day (sessions with at least one rep). */
data class ExerciseDayTotals(val reps: Int, val sessions: Int)

/** Exercise sessions. Multi-step writes are composed in [ExerciseRepository]. */
@Dao
interface ExerciseDao {

    /** Open sessions (running or paused), newest first. There is normally at most one. */
    @Query("SELECT * FROM exercise_session WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC, id")
    suspend fun openSessions(): List<ExerciseSessionEntity>

    @Query("SELECT * FROM exercise_session WHERE id = :id")
    suspend fun session(id: String): ExerciseSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ExerciseSessionEntity)

    @Update
    suspend fun updateSession(session: ExerciseSessionEntity)

    /**
     * Saves an open session's count and timer. Does nothing once the session has ended or been
     * removed, so a late save can never reopen or overwrite a finished session.
     */
    @Query(
        "UPDATE exercise_session SET status = :status, repetitions = :repetitions, " +
            "activeDurationMillis = :activeDurationMillis, activeAtSegmentStartMillis = :activeAtSegmentStartMillis, " +
            "segmentStartElapsedRealtime = :segmentStartElapsedRealtime, bootCount = :bootCount, " +
            "updatedAt = :updatedAt, updatedZoneId = :updatedZoneId " +
            "WHERE id = :id AND status IN ('RUNNING', 'PAUSED')",
    )
    suspend fun saveOpenProgress(
        id: String,
        status: SessionStatus,
        repetitions: Int,
        activeDurationMillis: Long,
        activeAtSegmentStartMillis: Long,
        segmentStartElapsedRealtime: Long?,
        bootCount: Int?,
        updatedAt: Instant,
        updatedZoneId: ZoneId,
    ): Int

    @Query("DELETE FROM exercise_session WHERE id = :id")
    suspend fun deleteSession(id: String): Int

    /** Every rep ever counted, open session included. Timed-challenge reps count too. */
    @Query("SELECT COALESCE(SUM(repetitions), 0) FROM exercise_session")
    fun observeLifetimeReps(): Flow<Int>

    /** A session counts on the local day it started. */
    @Query(
        "SELECT COALESCE(SUM(repetitions), 0) AS reps, COUNT(*) AS sessions FROM exercise_session " +
            "WHERE startDate = :date AND repetitions > 0",
    )
    fun observeDayTotals(date: LocalDate): Flow<ExerciseDayTotals>

    @Query("SELECT * FROM exercise_session WHERE status NOT IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC, id")
    fun observeFinishedSessions(): Flow<List<ExerciseSessionEntity>>
}
