package com.mauforge.paintlife.today

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Priorities and daily records. Multi-step writes are composed in [TodayRepository]. */
@Dao
interface TodayDao {

    @Query("SELECT * FROM priority WHERE date = :date ORDER BY position")
    fun observePriorities(date: LocalDate): Flow<List<PriorityEntity>>

    @Query("SELECT * FROM priority WHERE date = :date ORDER BY position")
    suspend fun priorities(date: LocalDate): List<PriorityEntity>

    @Query("SELECT * FROM priority WHERE id = :id")
    suspend fun priority(id: String): PriorityEntity?

    @Query("SELECT COUNT(*) FROM priority WHERE date = :date")
    suspend fun countPriorities(date: LocalDate): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPriority(priority: PriorityEntity)

    @Update
    suspend fun updatePriority(priority: PriorityEntity)

    @Query("UPDATE priority SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("DELETE FROM priority WHERE id = :id")
    suspend fun deletePriority(id: String)

    /** The latest day before [before], and no earlier than [earliest], that has any priorities. */
    @Query("SELECT MAX(date) FROM priority WHERE date < :before AND date >= :earliest")
    fun observeLatestPlannedDay(before: LocalDate, earliest: LocalDate): Flow<LocalDate?>

    /** Unfinished priorities of [source] that have not yet been brought to [target]. */
    @Query(
        "SELECT * FROM priority WHERE date = :source AND isCompleted = 0 AND id NOT IN " +
            "(SELECT carriedFromId FROM priority WHERE date = :target AND carriedFromId IS NOT NULL) " +
            "ORDER BY position",
    )
    fun observeOpenNotCarried(source: LocalDate, target: LocalDate): Flow<List<PriorityEntity>>

    @Query("SELECT * FROM daily_record WHERE date = :date")
    fun observeDailyRecord(date: LocalDate): Flow<DailyRecordEntity?>

    /** Creates the day's record if there is none; never overwrites an existing one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyRecordIfAbsent(record: DailyRecordEntity)

    @Query(
        "UPDATE daily_record SET carryOverDismissed = 1, updatedAt = :at, updatedZoneId = :zone " +
            "WHERE date = :date",
    )
    suspend fun dismissCarryOver(date: LocalDate, at: Instant, zone: ZoneId)
}
