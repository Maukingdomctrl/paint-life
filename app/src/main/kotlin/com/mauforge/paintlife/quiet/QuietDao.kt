package com.mauforge.paintlife.quiet

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

/** Quiet sessions. Multi-step writes are composed in [QuietRepository]. */
@Dao
interface QuietDao {

    /** Open sessions (running or paused), newest first. There is normally at most one. */
    @Query("SELECT * FROM quiet_session WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAt DESC, id")
    suspend fun openSessions(): List<QuietSessionEntity>

    @Query("SELECT * FROM quiet_session WHERE id = :id")
    suspend fun session(id: String): QuietSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: QuietSessionEntity)

    @Update
    suspend fun update(session: QuietSessionEntity)

    /**
     * Saves an open session's status and timer. Does nothing once the session has ended or been
     * removed, so a late save can never reopen or overwrite a finished session.
     */
    @Query(
        "UPDATE quiet_session SET status = :status, " +
            "activeDurationMillis = :activeDurationMillis, activeAtSegmentStartMillis = :activeAtSegmentStartMillis, " +
            "segmentStartElapsedRealtime = :segmentStartElapsedRealtime, bootCount = :bootCount, " +
            "updatedAt = :updatedAt, updatedZoneId = :updatedZoneId " +
            "WHERE id = :id AND status IN ('RUNNING', 'PAUSED')",
    )
    suspend fun saveOpenProgress(
        id: String,
        status: SessionStatus,
        activeDurationMillis: Long,
        activeAtSegmentStartMillis: Long,
        segmentStartElapsedRealtime: Long?,
        bootCount: Int?,
        updatedAt: Instant,
        updatedZoneId: ZoneId,
    ): Int

    @Query("DELETE FROM quiet_session WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Finished sessions that count toward minutes, started between [from] and [to] (inclusive). */
    @Query(
        "SELECT startDate, status, activeDurationMillis, targetDurationMillis FROM quiet_session " +
            "WHERE startDate BETWEEN :from AND :to AND status IN ('COMPLETED', 'ENDED_EARLY') ORDER BY startDate",
    )
    fun observeFinished(from: LocalDate, to: LocalDate): Flow<List<QuietSessionSummary>>
}
