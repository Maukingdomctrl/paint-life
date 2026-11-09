package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 3 → 4 (Iteration 5, Quiet Time).
 *
 * quiet_session: adds endedZoneId and updatedZoneId beside endedAt and updatedAt, and an index on
 * startedAt. Existing rows take the zone the session was started in, which they already store.
 * The table is rebuilt (create, copy, drop, rename) so it matches the exported v4 schema exactly.
 *
 * No row is dropped and no value other than the new columns changes. The new SessionStatus
 * constant ENDED_EARLY is stored by name and needs no schema change.
 */
class Migration3To4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `quiet_session_new` (`id` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endedAt` INTEGER, " +
                "`endedZoneId` TEXT, `targetDurationMillis` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`updatedZoneId` TEXT NOT NULL, `activeDurationMillis` INTEGER NOT NULL, " +
                "`activeAtSegmentStartMillis` INTEGER NOT NULL, `segmentStartElapsedRealtime` INTEGER, " +
                "`bootCount` INTEGER, PRIMARY KEY(`id`))",
        )
        sql.exec(
            "INSERT INTO `quiet_session_new` (id, status, startedAt, zoneId, startDate, endedAt, endedZoneId, " +
                "targetDurationMillis, updatedAt, updatedZoneId, activeDurationMillis, activeAtSegmentStartMillis, " +
                "segmentStartElapsedRealtime, bootCount) " +
                "SELECT id, status, startedAt, zoneId, startDate, endedAt, CASE WHEN endedAt IS NULL THEN NULL ELSE zoneId END, " +
                "targetDurationMillis, updatedAt, zoneId, activeDurationMillis, activeAtSegmentStartMillis, " +
                "segmentStartElapsedRealtime, bootCount FROM `quiet_session`",
        )
        sql.exec("DROP TABLE `quiet_session`")
        sql.exec("ALTER TABLE `quiet_session_new` RENAME TO `quiet_session`")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_quiet_session_startDate` ON `quiet_session` (`startDate`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_quiet_session_startedAt` ON `quiet_session` (`startedAt`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_quiet_session_status` ON `quiet_session` (`status`)")
    }
}
