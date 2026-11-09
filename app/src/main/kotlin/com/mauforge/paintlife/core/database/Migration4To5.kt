package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.ZoneId

/**
 * Version 4 → 5 (Iteration 6, My Time). Covers both 6a and 6b, so 6b needs no migration.
 *
 * - time_entry: adds endZoneId, createdZoneId and updatedZoneId. Existing rows take the zone the
 *   entry was recorded in (zoneId). Rebuilt (create, copy, drop, rename) to match the exported
 *   schema exactly.
 * - daily_schedule: adds createdZoneId, approvedZoneId, updatedZoneId, activeFrom, activeUntil,
 *   basedOnFrom and basedOnTo. Existing rows get [zone] (the device zone when the migration runs); approved ones are active from
 *   their approval date onwards (unknown, so left null: they show nowhere until re-approved).
 *   schedule_block refers to it by name and survives the rebuild.
 * - current_activity: new, for the running "now doing" activity.
 *
 * No row is dropped and no existing value changes.
 */
class Migration4To5(private val zone: () -> ZoneId) : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        migrateEntries(sql)
        migrateSchedules(sql, zone().id)
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `current_activity` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                "`note` TEXT, `startAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`categoryId`) REFERENCES `activity_category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        sql.exec("CREATE INDEX IF NOT EXISTS `index_current_activity_categoryId` ON `current_activity` (`categoryId`)")
    }

    private fun migrateEntries(sql: MigrationSql) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `time_entry_new` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`categoryId` TEXT NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                "`endZoneId` TEXT NOT NULL, `note` TEXT, `createdAt` INTEGER NOT NULL, `createdZoneId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `updatedZoneId` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`categoryId`) REFERENCES `activity_category`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        sql.exec(
            "INSERT INTO `time_entry_new` (id, kind, categoryId, startAt, endAt, zoneId, endZoneId, note, createdAt, " +
                "createdZoneId, updatedAt, updatedZoneId) " +
                "SELECT id, kind, categoryId, startAt, endAt, zoneId, zoneId, note, createdAt, zoneId, updatedAt, zoneId " +
                "FROM `time_entry`",
        )
        sql.exec("DROP TABLE `time_entry`")
        sql.exec("ALTER TABLE `time_entry_new` RENAME TO `time_entry`")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_time_entry_kind_startAt` ON `time_entry` (`kind`, `startAt`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_time_entry_categoryId` ON `time_entry` (`categoryId`)")
    }

    private fun migrateSchedules(sql: MigrationSql, zoneId: String) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `daily_schedule_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `createdZoneId` TEXT NOT NULL, `approvedAt` INTEGER, " +
                "`approvedZoneId` TEXT, `updatedAt` INTEGER NOT NULL, `updatedZoneId` TEXT NOT NULL, `activeFrom` TEXT, " +
                "`activeUntil` TEXT, `basedOnFrom` TEXT, `basedOnTo` TEXT, PRIMARY KEY(`id`))",
        )
        sql.exec(
            "INSERT INTO `daily_schedule_new` (id, name, status, createdAt, createdZoneId, approvedAt, approvedZoneId, " +
                "updatedAt, updatedZoneId, activeFrom, activeUntil, basedOnFrom, basedOnTo) " +
                "SELECT id, name, status, createdAt, ?, approvedAt, CASE WHEN approvedAt IS NULL THEN NULL ELSE ? END, " +
                "updatedAt, ?, NULL, NULL, NULL, NULL FROM `daily_schedule`",
            listOf(zoneId, zoneId, zoneId),
        )
        // schedule_block's foreign key names daily_schedule, so it points at the new table after
        // the rename (foreign keys are not enforced while migrations run).
        sql.exec("DROP TABLE `daily_schedule`")
        sql.exec("ALTER TABLE `daily_schedule_new` RENAME TO `daily_schedule`")
    }
}
