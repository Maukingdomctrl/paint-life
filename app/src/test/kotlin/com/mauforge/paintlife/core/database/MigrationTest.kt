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
    fun version2MigratesToTheExportedVersion3Schema() {
        helper.createDatabase(2).close()
        helper.runMigrationsAndValidate(3, migrations).close()
    }

    @Test
    fun version1MigratesAllTheWayToVersion3() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(3, migrations).close()
    }

    @Test
    fun everyVersionAfterOneHasAMigration() {
        val currentVersion = 3
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

    @Test
    fun exerciseDataSurvivesVersion3WithZones() {
        helper.createDatabase(2).use { db ->
            db.execSQL(
                "INSERT INTO challenge_level (level, catKey, benchmarkMillis, isUnlocked, unlockedAt, beatenAt) VALUES " +
                    "(1, 'ana', 1800000, 1, NULL, 5000), (2, 'mia', 1740000, 1, 5000, NULL), (3, 'zoe', 1680000, 0, NULL, NULL)",
            )
            db.execSQL(
                "INSERT INTO exercise_session (id, type, status, challengeLevel, startedAt, zoneId, startDate, endedAt, " +
                    "repetitions, notes, updatedAt, activeDurationMillis, activeAtSegmentStartMillis, " +
                    "segmentStartElapsedRealtime, bootCount) VALUES " +
                    "('done', 'VOLUME', 'COMPLETED', NULL, 1000, 'Europe/London', '2026-11-08', 2000, 120, 'good', 2000, 900, 900, NULL, 4), " +
                    "('open', 'VOLUME', 'RUNNING', NULL, 3000, 'Asia/Tokyo', '2026-11-09', NULL, 15, NULL, 3500, 400, 0, 77, 4), " +
                    "('timed', 'TIMED_CHALLENGE', 'COMPLETED', 1, 4000, 'Europe/London', '2026-11-09', 4900, 1000, NULL, 4900, 800, 0, NULL, 4)",
            )
            db.execSQL(
                "INSERT INTO personal_best (key, kind, challengeLevel, value, sessionId, achievedAt, zoneId) VALUES " +
                    "('timed_level_1', 'TIMED_CHALLENGE_TIME', 1, 800, 'timed', 4900, 'Europe/London')",
            )
        }

        helper.runMigrationsAndValidate(3, migrations).use { db ->
            assertEquals(
                listOf(
                    listOf("done", "COMPLETED", "1000", "Europe/London", "2026-11-08", "2000", "Europe/London", "120", "good", "2000", "Europe/London", "900", "900", null, "4"),
                    listOf("open", "RUNNING", "3000", "Asia/Tokyo", "2026-11-09", null, null, "15", null, "3500", "Asia/Tokyo", "400", "0", "77", "4"),
                    listOf("timed", "COMPLETED", "4000", "Europe/London", "2026-11-09", "4900", "Europe/London", "1000", null, "4900", "Europe/London", "800", "0", null, "4"),
                ),
                db.rows(
                    "SELECT id, status, startedAt, zoneId, startDate, endedAt, endedZoneId, repetitions, notes, updatedAt, " +
                        "updatedZoneId, activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount " +
                        "FROM exercise_session ORDER BY id",
                ),
            )
            assertEquals(
                listOf(
                    listOf("1", "1", null, null, "5000", "Asia/Kolkata"),
                    listOf("2", "1", "5000", "Asia/Kolkata", null, null),
                    listOf("3", "0", null, null, null, null),
                ),
                db.rows("SELECT level, isUnlocked, unlockedAt, unlockedZoneId, beatenAt, beatenZoneId FROM challenge_level ORDER BY level"),
            )
            assertEquals(listOf(listOf("timed_level_1", "timed")), db.rows("SELECT key, sessionId FROM personal_best"))
            assertEquals(emptyList<List<String?>>(), db.rows("PRAGMA foreign_key_check"))
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
