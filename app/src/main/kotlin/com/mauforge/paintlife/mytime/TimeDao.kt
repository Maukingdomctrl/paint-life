package com.mauforge.paintlife.mytime

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/** One recently used activity: a category and note pair, and when it last ended. */
data class RecentActivityRow(val categoryId: String, val note: String?, val lastUsed: Instant)

/** The timing of one entry, for totals that need no category. */
data class EntrySpan(val startAt: Instant, val endAt: Instant, val zoneId: ZoneId)

/**
 * Time entries, categories and the running activity. Range reads use the (kind, startAt) index:
 * an entry lasts at most 24 hours, so every entry overlapping [from, to) starts in
 * [from - 24 h, to). Callers pass that lower bound as `earliestStart`.
 */
@Dao
interface TimeDao {

    @Query(
        "SELECT e.*, c.name AS categoryName, c.colorKey AS colorKey, c.isDistraction AS isDistraction " +
            "FROM time_entry e JOIN activity_category c ON c.id = e.categoryId " +
            "WHERE e.kind = :kind AND e.startAt >= :earliestStart AND e.startAt < :to AND e.endAt > :from " +
            "ORDER BY e.startAt, e.id",
    )
    fun observeEntries(kind: TimeEntryKind, from: Instant, to: Instant, earliestStart: Instant): Flow<List<EntryWithCategory>>

    /** Entries of [kind] overlapping [from, to). */
    @Query(
        "SELECT * FROM time_entry WHERE kind = :kind AND startAt >= :earliestStart AND startAt < :to AND endAt > :from " +
            "ORDER BY startAt, id",
    )
    suspend fun overlapping(kind: TimeEntryKind, from: Instant, to: Instant, earliestStart: Instant): List<TimeEntryEntity>

    @Query("SELECT * FROM time_entry WHERE id = :id")
    suspend fun entry(id: String): TimeEntryEntity?

    @Query("SELECT * FROM time_entry WHERE id IN (:ids)")
    suspend fun entries(ids: List<String>): List<TimeEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: TimeEntryEntity)

    @Update
    suspend fun update(entry: TimeEntryEntity)

    @Query("DELETE FROM time_entry WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>): Int

    /** The last [limit] distinct activities (category + note) used since [since], newest first. */
    @Query(
        "SELECT categoryId, note, MAX(endAt) AS lastUsed FROM time_entry " +
            "WHERE kind = 'ACTUAL' AND startAt >= :since GROUP BY categoryId, note ORDER BY lastUsed DESC LIMIT :limit",
    )
    fun observeRecent(since: Instant, limit: Int): Flow<List<RecentActivityRow>>

    /** Where the latest actual entry that ended by [until] ended (searching entries since [since]). */
    @Query("SELECT MAX(endAt) FROM time_entry WHERE kind = 'ACTUAL' AND startAt >= :since AND endAt <= :until")
    suspend fun lastActualEnd(since: Instant, until: Instant): Instant?

    /** The latest actual sleeps that started since [since], newest first. */
    @Query(
        "SELECT * FROM time_entry WHERE kind = 'ACTUAL' AND categoryId = :sleepId AND startAt >= :since " +
            "ORDER BY startAt DESC LIMIT :limit",
    )
    suspend fun recentSleeps(sleepId: String, since: Instant, limit: Int): List<TimeEntryEntity>

    /** How many actual sleeps overlap [from, to). */
    @Query(
        "SELECT COUNT(*) FROM time_entry WHERE kind = 'ACTUAL' AND categoryId = :sleepId " +
            "AND startAt >= :earliestStart AND startAt < :to AND endAt > :from",
    )
    fun observeSleepCount(sleepId: String, from: Instant, to: Instant, earliestStart: Instant): Flow<Int>

    /** Every actual entry's timing (read only while the observation week is still running). */
    @Query("SELECT startAt, endAt, zoneId FROM time_entry WHERE kind = 'ACTUAL' ORDER BY startAt")
    fun observeActualSpans(): Flow<List<EntrySpan>>

    @Query("SELECT * FROM activity_category WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<ActivityCategoryEntity>>

    @Query("SELECT * FROM activity_category WHERE id = :id")
    suspend fun category(id: String): ActivityCategoryEntity?

    @Query("SELECT * FROM current_activity WHERE id = 'current'")
    fun observeCurrent(): Flow<CurrentActivityEntity?>

    @Query("SELECT * FROM current_activity WHERE id = 'current'")
    suspend fun current(): CurrentActivityEntity?

    @Upsert
    suspend fun upsertCurrent(current: CurrentActivityEntity)

    @Query("DELETE FROM current_activity")
    suspend fun clearCurrent(): Int

    /** Changes a category's distraction flag; it applies to every day, past ones included. */
    @Query("UPDATE activity_category SET isDistraction = :isDistraction WHERE id = :id")
    suspend fun setDistraction(id: String, isDistraction: Boolean): Int

    /** Schedules that have been approved (active now or in the past), with their blocks. */
    @Transaction
    @Query("SELECT * FROM daily_schedule WHERE status != 'PROPOSED' AND activeFrom IS NOT NULL ORDER BY activeFrom, id")
    fun observeSchedules(): Flow<List<ScheduleWithBlocks>>

    @Transaction
    @Query("SELECT * FROM daily_schedule WHERE status != 'PROPOSED' AND activeFrom IS NOT NULL ORDER BY activeFrom, id")
    suspend fun schedules(): List<ScheduleWithBlocks>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: DailyScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: DailyScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlocks(blocks: List<ScheduleBlockEntity>)

    @Query("SELECT COUNT(*) FROM daily_schedule")
    suspend fun scheduleCount(): Int
}
