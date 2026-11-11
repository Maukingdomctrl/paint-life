package com.mauforge.paintlife.mytime

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** One-off and snoozed reminders, and the few facts a reminder needs about what is running. */
@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminder WHERE kind = 'ONE_OFF' ORDER BY fireAt, id")
    fun observeOneOffs(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder ORDER BY fireAt, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder ORDER BY fireAt, id")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun reminder(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: String): Int

    /** Removes reminders whose moment has come: they have been shown, or were too late to show. */
    @Query("DELETE FROM reminder WHERE fireAt <= :now")
    suspend fun deleteFired(now: Instant): Int

    /** Planned entries that end at or after [from]. Any change to them re-plans the alarm. */
    @Query("SELECT * FROM time_entry WHERE kind = 'PLANNED' AND endAt >= :from ORDER BY startAt, id")
    fun observePlanned(from: Instant): Flow<List<TimeEntryEntity>>

    @Query("UPDATE schedule_block SET reminderMinutes = :minutes WHERE id = :blockId")
    suspend fun setBlockReminder(blockId: String, minutes: Int?): Int

    @Query("SELECT * FROM schedule_block WHERE id = :blockId")
    suspend fun block(blockId: String): ScheduleBlockEntity?

    @Query("SELECT COUNT(*) FROM exercise_session WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun openExerciseSessions(): Int

    @Query("SELECT COUNT(*) FROM quiet_session WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun openQuietSessions(): Int
}
