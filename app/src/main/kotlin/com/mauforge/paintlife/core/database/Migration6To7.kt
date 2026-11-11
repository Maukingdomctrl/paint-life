package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 6 → 7 (Iteration 10, opt-in reminders).
 *
 * - time_entry and schedule_block: a nullable reminderMinutes column. Existing rows get NULL,
 *   so every reminder starts off.
 * - reminder: new, for one-off reminders and snoozed ones.
 *
 * No row is dropped and no existing value changes.
 */
class Migration6To7 : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        sql.exec("ALTER TABLE `time_entry` ADD COLUMN `reminderMinutes` INTEGER")
        sql.exec("ALTER TABLE `schedule_block` ADD COLUMN `reminderMinutes` INTEGER")
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `reminder` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT, " +
                "`categoryId` TEXT, `note` TEXT, `fireAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                "`activityStartAt` INTEGER, `activityEndAt` INTEGER, `action` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `createdZoneId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        sql.exec("CREATE INDEX IF NOT EXISTS `index_reminder_fireAt` ON `reminder` (`fireAt`)")
    }
}
