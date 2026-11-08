package com.mauforge.paintlife.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.PersonalBestEntity
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.DailyScheduleEntity
import com.mauforge.paintlife.mytime.ScheduleBlockEntity
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.today.DailyRecordEntity
import com.mauforge.paintlife.today.PriorityEntity

/**
 * Version 1 schema. DAOs are added in the iteration that builds each feature; the schema
 * itself only changes through [ALL_MIGRATIONS]. Exported to app/schemas/ (checked in).
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PaintLifeDatabase : RoomDatabase() {

    companion object {
        const val NAME = "paint_life.db"

        fun build(context: Context): PaintLifeDatabase =
            Room.databaseBuilder(context, PaintLifeDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(SeedCallback)
                .build()
    }
}
