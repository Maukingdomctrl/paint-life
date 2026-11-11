package com.mauforge.paintlife.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.today.AddResult
import com.mauforge.paintlife.today.TodayRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
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
    fun version3MigratesToTheExportedVersion4Schema() {
        helper.createDatabase(3).close()
        helper.runMigrationsAndValidate(4, migrations).close()
    }

    @Test
    fun version1MigratesAllTheWayToVersion4() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(4, migrations).close()
    }

    @Test
    fun version4MigratesToTheExportedVersion5Schema() {
        helper.createDatabase(4).close()
        helper.runMigrationsAndValidate(5, migrations).close()
    }

    @Test
    fun version1MigratesAllTheWayToVersion5() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(5, migrations).close()
    }

    @Test
    fun version5MigratesToTheExportedVersion6Schema() {
        helper.createDatabase(5).close()
        helper.runMigrationsAndValidate(6, migrations).close()
    }

    @Test
    fun version1MigratesAllTheWayToVersion6() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(6, migrations).close()
    }

    @Test
    fun version6MigratesToTheExportedVersion7Schema() {
        helper.createDatabase(6).close()
        helper.runMigrationsAndValidate(7, migrations).close()
    }

    @Test
    fun version1MigratesAllTheWayToVersion7() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(7, migrations).close()
    }

    /** Iteration 10: reminders are opt-in, so after the update every reminder is off and nothing else moved. */
    @Test
    fun plansAndSchedulesSurviveVersion7WithEveryReminderOff() {
        val entryColumns = "id, kind, categoryId, startAt, endAt, zoneId, endZoneId, note, createdAt, createdZoneId, updatedAt, updatedZoneId"
        val scheduleColumns = "id, name, status, createdAt, createdZoneId, approvedAt, approvedZoneId, updatedAt, updatedZoneId, " +
            "activeFrom, activeUntil, basedOnFrom, basedOnTo"
        val blockColumns = "id, scheduleId, categoryId, startMinuteOfDay, endMinuteOfDay, daysOfWeekMask"
        val entries = listOf(
            listOf("night", "ACTUAL", "sleep", "1000", "29800000", "Europe/London", "Europe/London", null, "30000000", "Europe/London", "30000000", "Europe/London"),
            listOf("plan", "PLANNED", "work", "40000000", "43600000", "Asia/Tokyo", "Asia/Tokyo", "deep work", "100", "Asia/Tokyo", "200", "Asia/Kolkata"),
            listOf("soon", "PLANNED", "family", "50000000", "53600000", "Asia/Kolkata", "Asia/Kolkata", null, "300", "Asia/Kolkata", "300", "Asia/Kolkata"),
        )
        val schedules = listOf(
            listOf("s1", "Weekdays", "APPROVED", "10", "Asia/Kolkata", "20", "Asia/Kolkata", "30", "Asia/Kolkata", "2026-11-01", null, "2026-10-01", "2026-10-28"),
            listOf("s0", "Before", "ARCHIVED", "1", "Asia/Tokyo", "2", "Asia/Tokyo", "3", "Asia/Tokyo", "2026-09-01", "2026-10-31", null, null),
        )
        val blocks = listOf(
            listOf("b1", "s1", "work", "540", "1020", "31"),
            listOf("b2", "s1", "sleep", "1380", "420", "127"),
            listOf("b0", "s0", "family", "1080", "1200", "96"),
        )
        fun values(rows: List<List<String?>>) = rows.joinToString(", ") { row -> row.joinToString(", ", "(", ")") { if (it == null) "NULL" else "'$it'" } }

        helper.createDatabase(6).use { db ->
            db.execSQL(
                "INSERT INTO activity_category (id, name, colorKey, isDistraction, sortOrder, isBuiltIn, isArchived) VALUES " +
                    "('sleep', 'Sleep', 'night', 0, 9, 1, 0), ('work', 'Work', 'slate', 0, 2, 1, 0), ('family', 'Family', 'rose', 0, 5, 1, 0)",
            )
            db.execSQL("INSERT INTO time_entry ($entryColumns) VALUES ${values(entries)}")
            db.execSQL("INSERT INTO daily_schedule ($scheduleColumns) VALUES ${values(schedules)}")
            db.execSQL("INSERT INTO schedule_block ($blockColumns) VALUES ${values(blocks)}")
            db.execSQL("INSERT INTO current_activity (id, categoryId, note, startAt, zoneId) VALUES ('current', 'work', 'now', 60000000, 'Asia/Kolkata')")
        }

        helper.runMigrationsAndValidate(7, migrations).use { db ->
            // Every row is as it was, and none of them reminds.
            assertEquals(entries.sortedBy { it[0] }.map { it + null }, db.rows("SELECT $entryColumns, reminderMinutes FROM time_entry ORDER BY id"))
            assertEquals(schedules.sortedBy { it[0] }, db.rows("SELECT $scheduleColumns FROM daily_schedule ORDER BY id"))
            assertEquals(blocks.sortedBy { it[0] }.map { it + null }, db.rows("SELECT $blockColumns, reminderMinutes FROM schedule_block ORDER BY id"))
            assertEquals(listOf(listOf("current", "work", "now", "60000000", "Asia/Kolkata")), db.rows("SELECT id, categoryId, note, startAt, zoneId FROM current_activity"))
            // No reminder exists until the user makes one.
            assertEquals(listOf(listOf("0")), db.rows("SELECT COUNT(*) FROM reminder"))
            assertEquals(listOf(listOf("index_reminder_fireAt")), db.rows("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'reminder' AND name NOT LIKE 'sqlite_%'"))
            assertEquals(emptyList<List<String?>>(), db.rows("PRAGMA foreign_key_check"))

            // The new columns and table take what the app writes.
            db.execSQL("UPDATE time_entry SET reminderMinutes = 10 WHERE id = 'plan'")
            db.execSQL("UPDATE schedule_block SET reminderMinutes = 30 WHERE id = 'b1'")
            db.execSQL(
                "INSERT INTO reminder (id, kind, title, categoryId, note, fireAt, zoneId, activityStartAt, activityEndAt, action, createdAt, createdZoneId) VALUES " +
                    "('r1', 'ONE_OFF', 'Call the bank', NULL, NULL, 70000000, 'Asia/Kolkata', NULL, NULL, 'NONE', 60000000, 'Asia/Kolkata')",
            )
            assertEquals(listOf(listOf("plan", "10")), db.rows("SELECT id, reminderMinutes FROM time_entry WHERE reminderMinutes IS NOT NULL"))
            assertEquals(listOf(listOf("b1", "30")), db.rows("SELECT id, reminderMinutes FROM schedule_block WHERE reminderMinutes IS NOT NULL"))
            assertEquals(listOf(listOf("r1", "ONE_OFF", "Call the bank", "70000000", "NONE")), db.rows("SELECT id, kind, title, fireAt, action FROM reminder"))
        }
    }

    @Test
    fun theNewRosterKeepsUnlockProgressByLevel() {
        val oldKeys = listOf("ana", "mia", "zoe", "eva", "luna", "sara", "nina", "maya", "aria", "champion")
        helper.createDatabase(5).use { db ->
            oldKeys.forEachIndexed { index, key ->
                val level = index + 1
                val unlocked = if (level <= 3) 1 else 0
                val unlockedAt = if (level in 2..3) "${level * 1000}, 'Asia/Tokyo'" else "NULL, NULL"
                val beatenAt = if (level <= 2) "${level * 1000 + 1000}, 'Asia/Tokyo'" else "NULL, NULL"
                db.execSQL(
                    "INSERT INTO challenge_level (level, catKey, benchmarkMillis, isUnlocked, unlockedAt, unlockedZoneId, beatenAt, beatenZoneId) " +
                        "VALUES ($level, '$key', ${(31 - level) * 60_000}, $unlocked, $unlockedAt, $beatenAt)",
                )
            }
        }

        helper.runMigrationsAndValidate(6, migrations).use { db ->
            val rows = db.rows(
                "SELECT level, catKey, benchmarkMillis, isUnlocked, unlockedAt, unlockedZoneId, beatenAt, beatenZoneId " +
                    "FROM challenge_level ORDER BY level",
            )
            assertEquals(SeedData.levels.map { it.catKey }, rows.map { it[1] })
            assertEquals((30 downTo 21).map { (it * 60_000).toString() }, rows.map { it[2] })
            assertEquals(listOf("1", "aero", "1800000", "1", null, null, "2000", "Asia/Tokyo"), rows[0])
            assertEquals(listOf("2", "aria", "1740000", "1", "2000", "Asia/Tokyo", "3000", "Asia/Tokyo"), rows[1])
            assertEquals(listOf("3", "pepper", "1680000", "1", "3000", "Asia/Tokyo", null, null), rows[2])
            assertEquals(listOf("9", "olive", "1320000", "0", null, null, null, null), rows[8])
        }
    }

    @Test
    fun everyVersionAfterOneHasAMigration() {
        val currentVersion = 7
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

    @Test
    fun quietSessionsSurviveVersion4WithZones() {
        helper.createDatabase(3).use { db ->
            db.execSQL(
                "INSERT INTO quiet_session (id, status, startedAt, zoneId, startDate, endedAt, targetDurationMillis, " +
                    "updatedAt, activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount) VALUES " +
                    "('done', 'COMPLETED', 1000, 'Europe/London', '2026-11-08', 301000, 300000, 301000, 300000, 300000, NULL, 4), " +
                    "('open', 'RUNNING', 5000, 'Asia/Tokyo', '2026-11-09', NULL, 300000, 6000, 1000, 0, 77, 4)",
            )
        }

        helper.runMigrationsAndValidate(4, migrations).use { db ->
            assertEquals(
                listOf(
                    listOf("done", "COMPLETED", "1000", "Europe/London", "2026-11-08", "301000", "Europe/London", "300000", "301000", "Europe/London", "300000", "300000", null, "4"),
                    listOf("open", "RUNNING", "5000", "Asia/Tokyo", "2026-11-09", null, null, "300000", "6000", "Asia/Tokyo", "1000", "0", "77", "4"),
                ),
                db.rows(
                    "SELECT id, status, startedAt, zoneId, startDate, endedAt, endedZoneId, targetDurationMillis, updatedAt, " +
                        "updatedZoneId, activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount " +
                        "FROM quiet_session ORDER BY id",
                ),
            )
        }
    }

    @Test
    fun timeEntriesAndSchedulesSurviveVersion5WithZones() {
        helper.createDatabase(4).use { db ->
            db.execSQL(
                "INSERT INTO activity_category (id, name, colorKey, isDistraction, sortOrder, isBuiltIn, isArchived) VALUES " +
                    "('sleep', 'Sleep', 'night', 0, 9, 1, 0), ('work', 'Work', 'slate', 0, 2, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO time_entry (id, kind, categoryId, startAt, endAt, zoneId, note, createdAt, updatedAt) VALUES " +
                    "('night', 'ACTUAL', 'sleep', 1000, 29800000, 'Europe/London', NULL, 30000000, 30000000), " +
                    "('plan', 'PLANNED', 'work', 40000000, 43600000, 'Asia/Tokyo', 'deep work', 100, 200)",
            )
            db.execSQL(
                "INSERT INTO daily_schedule (id, name, status, createdAt, approvedAt, updatedAt) VALUES " +
                    "('s1', 'Weekdays', 'APPROVED', 10, 20, 30), ('s2', 'Idea', 'PROPOSED', 40, NULL, 50)",
            )
            db.execSQL(
                "INSERT INTO schedule_block (id, scheduleId, categoryId, startMinuteOfDay, endMinuteOfDay, daysOfWeekMask) VALUES " +
                    "('b1', 's1', 'work', 540, 1020, 31)",
            )
        }

        helper.runMigrationsAndValidate(5, migrations).use { db ->
            assertEquals(
                listOf(
                    listOf("night", "ACTUAL", "sleep", "1000", "29800000", "Europe/London", "Europe/London", null, "30000000", "Europe/London", "30000000", "Europe/London"),
                    listOf("plan", "PLANNED", "work", "40000000", "43600000", "Asia/Tokyo", "Asia/Tokyo", "deep work", "100", "Asia/Tokyo", "200", "Asia/Tokyo"),
                ),
                db.rows(
                    "SELECT id, kind, categoryId, startAt, endAt, zoneId, endZoneId, note, createdAt, createdZoneId, updatedAt, " +
                        "updatedZoneId FROM time_entry ORDER BY id",
                ),
            )
            assertEquals(
                listOf(
                    listOf("s1", "Weekdays", "APPROVED", "10", "Asia/Kolkata", "20", "Asia/Kolkata", "30", "Asia/Kolkata", null, null, null, null),
                    listOf("s2", "Idea", "PROPOSED", "40", "Asia/Kolkata", null, null, "50", "Asia/Kolkata", null, null, null, null),
                ),
                db.rows(
                    "SELECT id, name, status, createdAt, createdZoneId, approvedAt, approvedZoneId, updatedAt, updatedZoneId, " +
                        "activeFrom, activeUntil, basedOnFrom, basedOnTo FROM daily_schedule ORDER BY id",
                ),
            )
            // The block still belongs to its schedule, and every foreign key holds.
            assertEquals(listOf(listOf("b1", "s1")), db.rows("SELECT id, scheduleId FROM schedule_block"))
            assertEquals(emptyList<List<String?>>(), db.rows("PRAGMA foreign_key_check"))
            assertEquals(listOf(listOf("0")), db.rows("SELECT COUNT(*) FROM current_activity"))
        }
    }

    /**
     * The whole way, as on a phone: a version 1 database file with records in every table is
     * opened by the app's own Room setup (not the test helper), which migrates 1 to 7, checks the
     * result against the current schema, and then reads, writes and reopens it.
     */
    @Test
    fun aVersion1DatabaseOpensInTheAppWithEveryRecordAndKeepsWorkingAfterARestart() {
        helper.createDatabase(1).use { db ->
            SeedData.levels.forEach {
                val unlocked = if (it.level <= 2) 1 else 0
                val unlockedAt = if (it.level == 2) "4900" else "NULL"
                val beatenAt = if (it.level == 1) "4900" else "NULL"
                db.execSQL(
                    "INSERT INTO challenge_level (level, catKey, benchmarkMillis, isUnlocked, unlockedAt, beatenAt) VALUES " +
                        "(${it.level}, '${it.catKey}', ${it.benchmarkMinutes * 60_000}, $unlocked, $unlockedAt, $beatenAt)",
                )
            }
            SeedData.categories.forEachIndexed { index, c ->
                val distraction = if (c.isDistraction) 1 else 0
                db.execSQL(
                    "INSERT INTO activity_category (id, name, colorKey, isDistraction, sortOrder, isBuiltIn, isArchived) VALUES " +
                        "('${c.id}', '${c.name}', '${c.colorKey}', $distraction, $index, 1, 0)",
                )
            }
            db.execSQL("INSERT INTO daily_record (id, date, note, createdAt, updatedAt) VALUES ('r1', '2026-11-08', 'kept', 50, 60)")
            db.execSQL(
                "INSERT INTO priority (id, date, position, title, isCompleted, completedAt, createdAt, updatedAt) VALUES " +
                    "('p1', '2026-11-08', 0, 'First', 1, 70, 50, 70), ('p2', '2026-11-08', 1, 'Second', 0, NULL, 51, 51)",
            )
            db.execSQL(
                "INSERT INTO exercise_session (id, type, status, challengeLevel, startedAt, zoneId, startDate, endedAt, repetitions, notes, " +
                    "updatedAt, activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount) VALUES " +
                    "('done', 'VOLUME', 'COMPLETED', NULL, 1000, 'Europe/London', '2026-11-08', 2000, 120, 'good', 2000, 900, 900, NULL, 4), " +
                    "('timed', 'TIMED_CHALLENGE', 'COMPLETED', 1, 4000, 'Europe/London', '2026-11-09', 4900, 1000, NULL, 4900, 800, 0, NULL, 4)",
            )
            db.execSQL(
                "INSERT INTO personal_best (key, kind, challengeLevel, value, sessionId, achievedAt, zoneId) VALUES " +
                    "('timed_level_1', 'TIMED_CHALLENGE_TIME', 1, 800, 'timed', 4900, 'Europe/London')",
            )
            db.execSQL(
                "INSERT INTO quiet_session (id, status, startedAt, zoneId, startDate, endedAt, targetDurationMillis, updatedAt, " +
                    "activeDurationMillis, activeAtSegmentStartMillis, segmentStartElapsedRealtime, bootCount) VALUES " +
                    "('q1', 'COMPLETED', 5000, 'Asia/Tokyo', '2026-11-09', 305000, 300000, 305000, 300000, 300000, NULL, 4)",
            )
            db.execSQL(
                "INSERT INTO time_entry (id, kind, categoryId, startAt, endAt, zoneId, note, createdAt, updatedAt) VALUES " +
                    "('night', 'ACTUAL', 'sleep', 0, 28800000, 'Europe/London', NULL, 30000000, 30000000)",
            )
            db.execSQL("INSERT INTO daily_schedule (id, name, status, createdAt, approvedAt, updatedAt) VALUES ('s1', 'Weekdays', 'APPROVED', 10, 20, 30)")
            db.execSQL(
                "INSERT INTO schedule_block (id, scheduleId, categoryId, startMinuteOfDay, endMinuteOfDay, daysOfWeekMask) VALUES " +
                    "('b1', 's1', 'work', 540, 1020, 31)",
            )
        }
        val context = instrumentation.targetContext
        val path = context.getDatabasePath("migration-test.db").absolutePath
        fun openAsTheAppDoes(): PaintLifeDatabase =
            Room.databaseBuilder(context, PaintLifeDatabase::class.java, path).addMigrations(*allMigrations { zone }).addCallback(SeedCallback).build()

        var app = openAsTheAppDoes()
        runBlocking {
            val dao = app.backupDao()
            assertEquals(listOf("First" to zone, "Second" to zone), dao.priorities().map { it.title to it.createdZoneId })
            assertEquals(listOf("kept"), dao.dailyRecords().map { it.note })
            assertEquals(listOf("done" to 120, "timed" to 1000), dao.finishedExerciseSessions().map { it.id to it.repetitions })
            // Rebuilding exercise_session did not cut the best time loose from its session.
            assertEquals("timed", dao.personalBests().single().sessionId)
            assertEquals(listOf(300_000L), dao.finishedQuietSessions().map { it.timer.activeDurationMillis })
            assertEquals(listOf(ZoneId.of("Europe/London")), dao.timeEntries().map { it.endZoneId })
            // Rebuilding daily_schedule did not take its blocks with it.
            assertEquals(listOf("s1"), dao.schedules().map { it.id })
            assertEquals(listOf("b1" to "s1"), dao.scheduleBlocks().map { it.id to it.scheduleId })
            assertEquals(10, dao.levels().size)
            assertEquals(listOf(true, true, false), dao.levels().take(3).map { it.isUnlocked })
            assertEquals(11, dao.categories().size)
            assertEquals(0, dao.runningActivities())
            // Version 7: every reminder is off after the update.
            assertEquals(listOf<Int?>(null), dao.timeEntries().map { it.reminderMinutes })
            assertEquals(listOf<Int?>(null), dao.scheduleBlocks().map { it.reminderMinutes })
            assertEquals(0, app.reminderDao().all().size)
            // The migrated database takes new records like any other.
            val today = TodayRepository(app, TimeProvider(Clock.systemUTC()) { zone })
            assertTrue(today.add(LocalDate.of(2026, 11, 8), "Added after the update") is AddResult.Added)
        }
        app.close()

        // "Restart": everything is still there.
        app = openAsTheAppDoes()
        runBlocking {
            assertEquals(listOf("First", "Second", "Added after the update"), app.backupDao().priorities().map { it.title })
            assertEquals(1, app.backupDao().timeEntries().size)
        }
        app.close()
    }

    private fun SQLiteConnection.rows(sql: String): List<List<String?>> = prepare(sql).use { statement ->
        val rows = mutableListOf<List<String?>>()
        while (statement.step()) {
            rows += (0 until statement.getColumnCount()).map { if (statement.isNull(it)) null else statement.getText(it) }
        }
        rows
    }
}
