package com.mauforge.paintlife.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mauforge.paintlife.core.backup.BackupDao
import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseDao
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.PersonalBestEntity
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.CurrentActivityEntity
import com.mauforge.paintlife.mytime.TimeDao
import com.mauforge.paintlife.mytime.DailyScheduleEntity
import com.mauforge.paintlife.mytime.ScheduleBlockEntity
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.quiet.QuietDao
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.today.DailyRecordEntity
import com.mauforge.paintlife.today.PriorityEntity
import com.mauforge.paintlife.today.TodayDao
import java.time.ZoneId

/**
 * DAOs are added in the iteration that builds each feature; the schema only changes through
 * explicit migrations ([allMigrations]). Exported to app/schemas/ (checked in).
 *
 * Version 2 (Iteration 3): zone ids beside every priority and daily-record timestamp, unique
 * (date, position) and (date, carriedFromId) on priorities, and the carry-over dismissal flag.
 *
 * Version 3 (Iteration 4): zone ids beside exercise end/update and level unlock/beat instants,
 * and an index on exercise_session.startedAt.
 *
 * Version 4 (Iteration 5): zone ids beside quiet_session end/update instants, and an index on
 * quiet_session.startedAt.
 *
 * Version 5 (Iteration 6): zone ids beside every time_entry and daily_schedule instant, schedule
 * activity dates, and current_activity (the running "now doing" activity).
 */
@Database(
    entities = [
        DailyRecordEntity::class,
        PriorityEntity::class,
        ChallengeLevelEntity::class,
        ExerciseSessionEntity::class,
        PersonalBestEntity::class,
        QuietSessionEntity::class,
        ActivityCategoryEntity::class,
        TimeEntryEntity::class,
        DailyScheduleEntity::class,
        ScheduleBlockEntity::class,
        CurrentActivityEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PaintLifeDatabase : RoomDatabase() {

    abstract fun todayDao(): TodayDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun quietDao(): QuietDao

    abstract fun timeDao(): TimeDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "paint_life.db"

        fun build(context: Context): PaintLifeDatabase =
            Room.databaseBuilder(context, PaintLifeDatabase::class.java, NAME)
                .addMigrations(*allMigrations(zone = ZoneId::systemDefault))
                .addCallback(SeedCallback)
                .build()
    }
}
