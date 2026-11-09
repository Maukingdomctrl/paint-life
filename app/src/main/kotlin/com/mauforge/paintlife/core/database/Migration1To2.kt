package com.mauforge.paintlife.core.database

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import java.time.ZoneId

/**
 * Version 1 → 2 (Iteration 3, Today and daily planning).
 *
 * - priority: adds createdZoneId, updatedZoneId and completedZoneId beside the existing
 *   instants, and carriedFromId; adds unique (date, position) and (date, carriedFromId).
 * - daily_record: adds createdZoneId, updatedZoneId and carryOverDismissed.
 *
 * Rows written before zones were stored get [zone] (the device zone when the migration runs).
 * Both tables are rebuilt (create, copy, drop, rename) so the result matches the exported v2
 * schema exactly. Positions are renumbered 0, 1, 2… per day in their existing order before the
 * unique index is created, so the migration can never fail on old data, and no row is dropped.
 *
 * Room calls [migrate] with a [SupportSQLiteDatabase] in the app and with an [SQLiteConnection]
 * in the driver-based migration tests; both run the same steps.
 */
class Migration1To2(private val zone: () -> ZoneId) : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) = migrate(SupportSql(db))

    override fun migrate(connection: SQLiteConnection) = migrate(ConnectionSql(connection))

    private fun migrate(sql: MigrationSql) {
        val zoneId = zone().id
        migratePriorities(sql, zoneId)
        migrateDailyRecords(sql, zoneId)
    }

    private fun migratePriorities(sql: MigrationSql, zoneId: String) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `priority_new` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, " +
                "`completedAt` INTEGER, `completedZoneId` TEXT, `carriedFromId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `createdZoneId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`updatedZoneId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        sql.exec(
            "INSERT INTO `priority_new` (id, date, position, title, isCompleted, completedAt, completedZoneId, " +
                "carriedFromId, createdAt, createdZoneId, updatedAt, updatedZoneId) " +
                "SELECT id, date, position, title, isCompleted, completedAt, " +
                "CASE WHEN completedAt IS NULL THEN NULL ELSE ? END, NULL, createdAt, ?, updatedAt, ? FROM `priority`",
            listOf(zoneId, zoneId, zoneId),
        )
        sql.exec("DROP TABLE `priority`")
        sql.exec("ALTER TABLE `priority_new` RENAME TO `priority`")

        val rows = sql.query("SELECT id, date FROM `priority` ORDER BY date, position, createdAt, id")
        var currentDate: String? = null
        var position = 0
        rows.forEach { (id, date) ->
            if (date != currentDate) {
                currentDate = date
                position = 0
            }
            sql.exec("UPDATE `priority` SET position = ? WHERE id = ?", listOf(position.toLong(), id))
            position++
        }

        sql.exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_priority_date_position` ON `priority` (`date`, `position`)")
        sql.exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_priority_date_carriedFromId` ON `priority` (`date`, `carriedFromId`)",
        )
    }

    private fun migrateDailyRecords(sql: MigrationSql, zoneId: String) {
        sql.exec(
            "CREATE TABLE IF NOT EXISTS `daily_record_new` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `note` TEXT, " +
                "`carryOverDismissed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `createdZoneId` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `updatedZoneId` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
        sql.exec(
            "INSERT INTO `daily_record_new` (id, date, note, carryOverDismissed, createdAt, createdZoneId, " +
                "updatedAt, updatedZoneId) SELECT id, date, note, 0, createdAt, ?, updatedAt, ? FROM `daily_record`",
            listOf(zoneId, zoneId),
        )
        sql.exec("DROP TABLE `daily_record`")
        sql.exec("ALTER TABLE `daily_record_new` RENAME TO `daily_record`")
        sql.exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_record_date` ON `daily_record` (`date`)")
    }
}

/** The few SQL operations a migration needs, over either of Room's two database APIs. */
private interface MigrationSql {
    fun exec(sql: String, args: List<Any?> = emptyList())

    /** Rows of text columns. Only for small result sets. */
    fun query(sql: String): List<List<String?>>
}

private class SupportSql(private val db: SupportSQLiteDatabase) : MigrationSql {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) db.execSQL(sql) else db.execSQL(sql, args.toTypedArray())
    }

    override fun query(sql: String): List<List<String?>> = db.query(sql).use { cursor -> cursor.textRows() }

    private fun Cursor.textRows(): List<List<String?>> {
        val rows = mutableListOf<List<String?>>()
        while (moveToNext()) rows += (0 until columnCount).map { if (isNull(it)) null else getString(it) }
        return rows
    }
}

private class ConnectionSql(private val connection: SQLiteConnection) : MigrationSql {
    override fun exec(sql: String, args: List<Any?>) {
        if (args.isEmpty()) {
            connection.execSQL(sql)
            return
        }
        connection.prepare(sql).use { statement ->
            args.forEachIndexed { index, value ->
                val position = index + 1
                when (value) {
                    null -> statement.bindNull(position)
                    is Long -> statement.bindLong(position, value)
                    is Int -> statement.bindLong(position, value.toLong())
                    is String -> statement.bindText(position, value)
                    else -> error("Unsupported migration argument type: ${value::class}")
                }
            }
            statement.step()
        }
    }

    override fun query(sql: String): List<List<String?>> = connection.prepare(sql).use { statement ->
        val rows = mutableListOf<List<String?>>()
        while (statement.step()) {
            rows += (0 until statement.getColumnCount()).map { if (statement.isNull(it)) null else statement.getText(it) }
        }
        rows
    }
}
