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

/** Reps and session count for one local day or range of days (sessions with at least one rep). */
data class ExerciseDayTotals(val reps: Int, val sessions: Int)

/**
 * Finished sessions with at least one rep in a range of days: [volumeSessions] normal sessions
 * and their [volumeActiveMillis] (for the average), and the most reps in any one session.
 */
data class ExerciseRangeStats(val volumeSessions: Int, val volumeActiveMillis: Long, val mostReps: Int)

/** All-time records over finished sessions; null while there is none. */
data class ExerciseRecords(val mostReps: Int?, val fastestTimedMillis: Long?)

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

    /** [observeDayTotals] over the days [from] to [to] (inclusive). */
    @Query(
        "SELECT COALESCE(SUM(repetitions), 0) AS reps, COUNT(*) AS sessions FROM exercise_session " +
            "WHERE startDate BETWEEN :from AND :to AND repetitions > 0",
    )
    fun observeRangeTotals(from: LocalDate, to: LocalDate): Flow<ExerciseDayTotals>

    /** Finished sessions with reps that started between [from] and [to] (inclusive). */
    @Query(
        "SELECT COALESCE(SUM(CASE WHEN type = 'VOLUME' THEN 1 ELSE 0 END), 0) AS volumeSessions, " +
            "COALESCE(SUM(CASE WHEN type = 'VOLUME' THEN activeDurationMillis ELSE 0 END), 0) AS volumeActiveMillis, " +
            "COALESCE(MAX(repetitions), 0) AS mostReps FROM exercise_session " +
            "WHERE startDate BETWEEN :from AND :to AND repetitions > 0 AND status NOT IN ('RUNNING', 'PAUSED')",
    )
    fun observeRangeStats(from: LocalDate, to: LocalDate): Flow<ExerciseRangeStats>

    /** The fastest completed 1,000-rep run that started between [from] and [to] (earliest first on a tie). */
    @Query(
        "SELECT * FROM exercise_session WHERE type = 'TIMED_CHALLENGE' AND status = 'COMPLETED' " +
            "AND startDate BETWEEN :from AND :to ORDER BY activeDurationMillis, endedAt, id LIMIT 1",
    )
    fun observeFastestTimed(from: LocalDate, to: LocalDate): Flow<ExerciseSessionEntity?>

    @Query(
        "SELECT (SELECT MAX(repetitions) FROM exercise_session WHERE repetitions > 0 AND status NOT IN ('RUNNING', 'PAUSED')) AS mostReps, " +
            "(SELECT MIN(activeDurationMillis) FROM exercise_session WHERE type = 'TIMED_CHALLENGE' AND status = 'COMPLETED') AS fastestTimedMillis",
    )
    fun observeRecords(): Flow<ExerciseRecords>

    @Query("SELECT * FROM exercise_session WHERE status NOT IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC, id")
    fun observeFinishedSessions(): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM challenge_level ORDER BY level")
    fun observeLevels(): Flow<List<ChallengeLevelEntity>>

    @Query("SELECT * FROM challenge_level ORDER BY level")
    suspend fun levels(): List<ChallengeLevelEntity>

    @Query("SELECT * FROM challenge_level WHERE level = :level")
    suspend fun level(level: Int): ChallengeLevelEntity?

    @Update
    suspend fun updateLevel(level: ChallengeLevelEntity)

    @Query("SELECT * FROM personal_best WHERE kind = 'TIMED_CHALLENGE_TIME' ORDER BY challengeLevel")
    fun observeTimedBests(): Flow<List<PersonalBestEntity>>

    @Query("SELECT * FROM personal_best WHERE `key` = :key")
    suspend fun personalBest(key: String): PersonalBestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonalBest(best: PersonalBestEntity)

    /** The fastest completed 1,000-rep run at [level] (earliest first on a tie). */
    @Query(
        "SELECT * FROM exercise_session WHERE type = 'TIMED_CHALLENGE' AND status = 'COMPLETED' " +
            "AND challengeLevel = :level ORDER BY activeDurationMillis, endedAt, id LIMIT 1",
    )
    suspend fun fastestCompleted(level: Int): ExerciseSessionEntity?

    /** The first completed run at [level] strictly faster than [benchmarkMillis]. */
    @Query(
        "SELECT * FROM exercise_session WHERE type = 'TIMED_CHALLENGE' AND status = 'COMPLETED' " +
            "AND challengeLevel = :level AND activeDurationMillis < :benchmarkMillis ORDER BY endedAt, id LIMIT 1",
    )
    suspend fun firstWin(level: Int, benchmarkMillis: Long): ExerciseSessionEntity?

    /** The newest completed run that beat its cat's benchmark and ended after [after]. */
    @Query(
        "SELECT s.* FROM exercise_session s JOIN challenge_level l ON s.challengeLevel = l.level " +
            "WHERE s.type = 'TIMED_CHALLENGE' AND s.status = 'COMPLETED' AND s.activeDurationMillis < l.benchmarkMillis " +
            "AND s.endedAt > :after ORDER BY s.endedAt DESC LIMIT 1",
    )
    fun observeLatestWinAfter(after: Instant): Flow<ExerciseSessionEntity?>
}
