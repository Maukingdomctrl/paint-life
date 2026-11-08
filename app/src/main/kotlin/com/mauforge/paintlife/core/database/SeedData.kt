package com.mauforge.paintlife.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.TimeUnit

/**
 * Fixed reference data: the spec's initial activity categories and the ten Cat League
 * levels. Inserts use stable keys and INSERT OR IGNORE, so seeding is idempotent and can
 * never duplicate or overwrite rows the user has since edited.
 */
object SeedData {

    data class CategorySeed(val id: String, val name: String, val colorKey: String, val isDistraction: Boolean)

    data class LevelSeed(val level: Int, val catKey: String, val benchmarkMinutes: Long)

    val categories: List<CategorySeed> = listOf(
        CategorySeed("personal_care", "Personal care", "sage", isDistraction = false),
        CategorySeed("exercise", "Exercise", "forest", isDistraction = false),
        CategorySeed("work", "Work", "slate", isDistraction = false),
        CategorySeed("learning", "Learning", "teal", isDistraction = false),
        CategorySeed("household", "Household", "clay", isDistraction = false),
        CategorySeed("family", "Family", "rose", isDistraction = false),
        CategorySeed("rest", "Rest", "moss", isDistraction = false),
        CategorySeed("entertainment", "Entertainment", "plum", isDistraction = true),
        CategorySeed("phone_internet", "Phone/internet", "amber", isDistraction = true),
        CategorySeed("sleep", "Sleep", "night", isDistraction = false),
        CategorySeed("other", "Other", "stone", isDistraction = false),
    )

    /** Fictional game benchmarks for 1,000 repetitions, not medical recommendations. */
    val levels: List<LevelSeed> = listOf(
        LevelSeed(1, "ana", 30),
        LevelSeed(2, "mia", 29),
        LevelSeed(3, "zoe", 28),
        LevelSeed(4, "eva", 27),
        LevelSeed(5, "luna", 26),
        LevelSeed(6, "sara", 25),
        LevelSeed(7, "nina", 24),
        LevelSeed(8, "maya", 23),
        LevelSeed(9, "aria", 22),
        LevelSeed(10, "champion", 21),
    )

    fun seed(db: SupportSQLiteDatabase) {
        categories.forEachIndexed { index, category ->
            db.execSQL(
                "INSERT OR IGNORE INTO activity_category " +
                    "(id, name, colorKey, isDistraction, sortOrder, isBuiltIn, isArchived) " +
                    "VALUES (?, ?, ?, ?, ?, 1, 0)",
                arrayOf<Any>(category.id, category.name, category.colorKey, if (category.isDistraction) 1 else 0, index),
            )
        }
        levels.forEach { level ->
            db.execSQL(
                "INSERT OR IGNORE INTO challenge_level " +
                    "(level, catKey, benchmarkMillis, isUnlocked, unlockedAt, beatenAt) " +
                    "VALUES (?, ?, ?, ?, NULL, NULL)",
                arrayOf<Any>(
                    level.level,
                    level.catKey,
                    TimeUnit.MINUTES.toMillis(level.benchmarkMinutes),
                    if (level.level == 1) 1 else 0,
                ),
            )
        }
    }
}

/** Seeds reference data in the same transaction that creates the database. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        SeedData.seed(db)
    }
}
