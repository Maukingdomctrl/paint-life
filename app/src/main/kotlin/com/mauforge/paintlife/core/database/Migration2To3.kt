package com.mauforge.paintlife.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.ZoneId

/**
 * Version 2 → 3 (Iteration 4, Exercise and Cat League).
 *
 * - exercise_session: adds endedZoneId and updatedZoneId beside endedAt and updatedAt, and an
 *   index on startedAt (history is listed newest first). Existing rows take the zone the session
 *   was started in, which they already store. The table is rebuilt (create, copy, drop, rename)
 *   so it matches the exported v3 schema exactly; personal_best keeps referring to it by name.
 * - challenge_level: adds unlockedZoneId and beatenZoneId. Rows that already have those instants
 *   get [zone] (the device zone when the migration runs); the rest stay null.
 *
 * No row is dropped and no value other than the new columns changes.
 */
class Migration2To3(private val zone: () -> ZoneId) : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        migrateSessions(sql)
        migrateLevels(sql, zone().id)
    }

    private fun migrateSessions(sql: MigrationSql) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `exercise_session_new` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `challengeLevel` INTEGER, `startedAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, " +
                "`startDate` TEXT NOT NULL, `endedAt` INTEGER, `endedZoneId` TEXT, `repetitions` INTEGER NOT NULL, " +
                "`notes` TEXT, `updatedAt` INTEGER NOT NULL, `updatedZoneId` TEXT NOT NULL, " +
                "`activeDurationMillis` INTEGER NOT NULL, `activeAtSegmentStartMillis` INTEGER NOT NULL, " +
                "`segmentStartElapsedRealtime` INTEGER, `bootCount` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`challengeLevel`) REFERENCES `challenge_level`(`level`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        sql.exec(
            "INSERT INTO `exercise_session_new` (id, type, status, challengeLevel, startedAt, zoneId, startDate, " +
                "endedAt, endedZoneId, repetitions, notes, updatedAt, updatedZoneId, activeDurationMillis, " +
                "activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount) " +
                "SELECT id, type, status, challengeLevel, startedAt, zoneId, startDate, " +
                "endedAt, CASE WHEN endedAt IS NULL THEN NULL ELSE zoneId END, repetitions, notes, updatedAt, zoneId, " +
                "activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount " +
                "FROM `exercise_session`",
        )
        sql.exec("DROP TABLE `exercise_session`")
        sql.exec("ALTER TABLE `exercise_session_new` RENAME TO `exercise_session`")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_exercise_session_startDate` ON `exercise_session` (`startDate`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_exercise_session_startedAt` ON `exercise_session` (`startedAt`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_exercise_session_status` ON `exercise_session` (`status`)")
        sql.exec("CREATE INDEX IF NOT EXISTS `index_exercise_session_challengeLevel` ON `exercise_session` (`challengeLevel`)")
    }

    private fun migrateLevels(sql: MigrationSql, zoneId: String) {
        sql.exec("ALTER TABLE `challenge_level` ADD COLUMN `unlockedZoneId` TEXT")
        sql.exec("ALTER TABLE `challenge_level` ADD COLUMN `beatenZoneId` TEXT")
        sql.exec("UPDATE `challenge_level` SET unlockedZoneId = ? WHERE unlockedAt IS NOT NULL", listOf(zoneId))
        sql.exec("UPDATE `challenge_level` SET beatenZoneId = ? WHERE beatenAt IS NOT NULL", listOf(zoneId))
    }
}
