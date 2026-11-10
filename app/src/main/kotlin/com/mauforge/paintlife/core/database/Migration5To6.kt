package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 5 → 6 (Iteration 8b, the final cat roster).
 *
 * challenge_level.catKey takes the new roster's key for each level. Progress maps by level:
 * isUnlocked, unlockedAt, beatenAt, benchmarks, sessions and personal bests are not touched.
 * The table's shape is unchanged, so the v6 schema equals v5.
 *
 * catKey is unique and two keys move between levels ("aria" from 9 to 2), so every row first
 * takes a throwaway key and then its final one.
 */
class Migration5To6 : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        sql.exec("UPDATE challenge_level SET catKey = 'level_' || level")
        SeedData.levels.forEach { seed ->
            sql.exec("UPDATE challenge_level SET catKey = ? WHERE level = ?", listOf(seed.catKey, seed.level))
        }
    }
}
