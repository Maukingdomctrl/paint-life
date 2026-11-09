package com.mauforge.paintlife.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * Validates every migration against the exported schema JSON in app/schemas, and checks that
 * user data survives each one.
 *
 * Uses the driver-based helper with an explicit file: the name-based helper compares a bare
 * database name with a full path, which fails on Windows host tests.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val migrations = allMigrations { zone }.toList()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = PaintLifeDatabase::class,
    )

    @Test
    fun version1MigratesToTheExportedVersion2Schema() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(2, migrations).close()
    }

    @Test
    fun everyVersionAfterOneHasAMigration() {
        val currentVersion = 2
        val covered = migrations.map { it.startVersion to it.endVersion }.toSet()
        val required = (1 until currentVersion).map { it to it + 1 }.toSet()
        assertEquals(required, covered)
    }

    @Test
    fun prioritiesSurviveVersion2WithZonesAndTidyPositions() {
        helper.createDatabase(1).use { db ->
            // Two rows share position 0 on the same day: legal in v1, renumbered in v2.
            db.execSQL(
                "INSERT INTO priority (id, date, position, title, isCompleted, completedAt, createdAt, updatedAt) VALUES " +
                    "('a', '2026-11-08', 0, 'Call the bank', 1, 1000, 100, 1000), " +
                    "('b', '2026-11-08', 0, 'Walk', 0, NULL, 200, 200), " +
                    "('c', '2026-11-08', 5, 'Read', 0, NULL, 300, 300), " +
                    "('d', '2026-11-09', 2, 'Water plants', 0, NULL, 400, 400)",
            )
            db.execSQL(
                "INSERT INTO daily_record (id, date, note, createdAt, updatedAt) VALUES ('r1', '2026-11-08', 'kept', 50, 60)",
            )
        }

        helper.runMigrationsAndValidate(2, migrations).use { db ->
            assertEquals(
                listOf(
                    listOf("a", "2026-11-08", "0", "Call the bank", "1", "1000", "Asia/Kolkata", null, "100", "Asia/Kolkata", "1000", "Asia/Kolkata"),
                    listOf("b", "2026-11-08", "1", "Walk", "0", null, null, null, "200", "Asia/Kolkata", "200", "Asia/Kolkata"),
                    listOf("c", "2026-11-08", "2", "Read", "0", null, null, null, "300", "Asia/Kolkata", "300", "Asia/Kolkata"),
                    listOf("d", "2026-11-09", "0", "Water plants", "0", null, null, null, "400", "Asia/Kolkata", "400", "Asia/Kolkata"),
                ),
                db.rows(
                    "SELECT id, date, position, title, isCompleted, completedAt, completedZoneId, carriedFromId, " +
                        "createdAt, createdZoneId, updatedAt, updatedZoneId FROM priority ORDER BY id",
                ),
            )
            assertEquals(
                listOf(listOf("r1", "2026-11-08", "kept", "0", "50", "Asia/Kolkata", "60", "Asia/Kolkata")),
                db.rows(
                    "SELECT id, date, note, carryOverDismissed, createdAt, createdZoneId, updatedAt, updatedZoneId FROM daily_record",
                ),
            )
        }
    }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { statement ->
        val rows = mutableListOf<List<String?>>()
        while (statement.step()) {
            rows += (0 until statement.getColumnCount()).map { if (statement.isNull(it)) null else statement.getText(it) }
        }
        rows
    }
}
