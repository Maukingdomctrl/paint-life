package com.mauforge.paintlife.core.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.PersonalBestEntity
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.DailyScheduleEntity
import com.mauforge.paintlife.mytime.ReminderEntity
import com.mauforge.paintlife.mytime.ScheduleBlockEntity
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.today.DailyRecordEntity
import com.mauforge.paintlife.today.PriorityEntity

/**
 * Whole-table reads and writes, for backup export and import only. Everything is composed into
 * one transaction by [BackupRepository]; nothing else should use these.
 */
@Dao
interface BackupDao {

    // Export: every record, in a stable order. Open sessions and the running activity are left out.

    @Query("SELECT * FROM daily_record ORDER BY date, id")
    suspend fun dailyRecords(): List<DailyRecordEntity>

    @Query("SELECT * FROM priority ORDER BY date, position, id")
    suspend fun priorities(): List<PriorityEntity>

    @Query("SELECT * FROM challenge_level ORDER BY level")
    suspend fun levels(): List<ChallengeLevelEntity>

    @Query("SELECT * FROM exercise_session WHERE status NOT IN ('RUNNING', 'PAUSED') ORDER BY startedAt, id")
    suspend fun finishedExerciseSessions(): List<ExerciseSessionEntity>

    @Query("SELECT * FROM personal_best ORDER BY `key`")
    suspend fun personalBests(): List<PersonalBestEntity>

    @Query("SELECT * FROM quiet_session WHERE status NOT IN ('RUNNING', 'PAUSED') ORDER BY startedAt, id")
    suspend fun finishedQuietSessions(): List<QuietSessionEntity>

    @Query("SELECT * FROM activity_category ORDER BY sortOrder, id")
    suspend fun categories(): List<ActivityCategoryEntity>

    @Query("SELECT * FROM time_entry ORDER BY kind, startAt, id")
    suspend fun timeEntries(): List<TimeEntryEntity>

    @Query("SELECT * FROM daily_schedule ORDER BY createdAt, id")
    suspend fun schedules(): List<DailyScheduleEntity>

    @Query("SELECT * FROM schedule_block ORDER BY scheduleId, startMinuteOfDay, id")
    suspend fun scheduleBlocks(): List<ScheduleBlockEntity>

    /** One-off reminders only: a snoozed reminder belongs to this phone's notifications. */
    @Query("SELECT * FROM reminder WHERE kind = 'ONE_OFF' ORDER BY fireAt, id")
    suspend fun oneOffReminders(): List<ReminderEntity>

    // What is in progress (an import waits for these to be finished).

    @Query("SELECT COUNT(*) FROM exercise_session WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun openExerciseSessions(): Int

    @Query("SELECT COUNT(*) FROM quiet_session WHERE status IN ('RUNNING', 'PAUSED')")
    suspend fun openQuietSessions(): Int

    @Query("SELECT COUNT(*) FROM current_activity")
    suspend fun runningActivities(): Int

    // Counts, for the "on this phone now" summary.

    @Query("SELECT COUNT(*) FROM priority")
    suspend fun priorityCount(): Int

    @Query("SELECT COUNT(*) FROM exercise_session WHERE status NOT IN ('RUNNING', 'PAUSED')")
    suspend fun exerciseSessionCount(): Int

    @Query("SELECT COUNT(*) FROM quiet_session WHERE status NOT IN ('RUNNING', 'PAUSED')")
    suspend fun quietSessionCount(): Int

    @Query("SELECT COUNT(*) FROM time_entry")
    suspend fun timeEntryCount(): Int

    @Query("SELECT COUNT(*) FROM daily_schedule")
    suspend fun scheduleCount(): Int

    // Import: clear every record table (children before parents), then insert the file's rows.

    /** Snoozed reminders go too: they belong to the data being replaced. */
    @Query("DELETE FROM reminder")
    suspend fun clearReminders()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminders(rows: List<ReminderEntity>)

    @Query("DELETE FROM schedule_block")
    suspend fun clearScheduleBlocks()

    @Query("DELETE FROM daily_schedule")
    suspend fun clearSchedules()

    @Query("DELETE FROM time_entry")
    suspend fun clearTimeEntries()

    @Query("DELETE FROM personal_best")
    suspend fun clearPersonalBests()

    @Query("DELETE FROM exercise_session")
    suspend fun clearExerciseSessions()

    @Query("DELETE FROM quiet_session")
    suspend fun clearQuietSessions()

    @Query("DELETE FROM priority")
    suspend fun clearPriorities()

    @Query("DELETE FROM daily_record")
    suspend fun clearDailyRecords()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDailyRecords(rows: List<DailyRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPriorities(rows: List<PriorityEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseSessions(rows: List<ExerciseSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersonalBests(rows: List<PersonalBestEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuietSessions(rows: List<QuietSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTimeEntries(rows: List<TimeEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedules(rows: List<DailyScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertScheduleBlocks(rows: List<ScheduleBlockEntity>)

    /** The ten levels always exist; an import only changes their state. */
    @Update
    suspend fun updateLevels(rows: List<ChallengeLevelEntity>)

    @Query("UPDATE activity_category SET isDistraction = :isDistraction WHERE id = :id")
    suspend fun setDistraction(id: String, isDistraction: Boolean)
}
