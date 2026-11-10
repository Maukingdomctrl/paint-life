package com.mauforge.paintlife.core.backup

import android.content.Context
import android.database.SQLException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.backup.BackupFixtures.ms
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.mytime.ApprovedProposal
import com.mauforge.paintlife.mytime.EntryDraft
import com.mauforge.paintlife.mytime.ProposalBlock
import com.mauforge.paintlife.mytime.Resolution
import com.mauforge.paintlife.mytime.SaveResult
import com.mauforge.paintlife.mytime.ScheduleRepository
import com.mauforge.paintlife.mytime.ScheduleRules
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.quiet.QuietRules
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import com.mauforge.paintlife.today.TodayRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Export and import on real database files: what goes out comes back exactly, an import replaces
 * everything in one transaction or changes nothing, and nothing in progress is ever overwritten.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    /** One install of the app: its database, preferences and repositories. */
    private inner class Phone(name: String) {
        val file: File = File(context.cacheDir, name).also { context.deleteDatabase(it.absolutePath) }
        val db: PaintLifeDatabase = Room.databaseBuilder(context, PaintLifeDatabase::class.java, file.absolutePath).addCallback(SeedCallback).build()
        val preferences: UserPreferencesRepository = testPreferences(context)
        val exercise = ExerciseRepository(db, time.provider)
        val replaced = DataReplaced()
        val backup = BackupRepository(db, preferences, exercise, time.provider, replaced)

        fun exported(): String = blocking {
            ByteArrayOutputStream().also { backup.export(it, "1.0.0") }.toString(Charsets.UTF_8.name())
        }

        fun import(text: String): ReplaceResult = blocking { backup.replaceAll(backup.read(stream(text))) }

        fun close() {
            db.close()
            context.deleteDatabase(file.absolutePath)
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), zone)
    private lateinit var phone: Phone
    private val others = mutableListOf<Phone>()

    @Before
    fun setUp() {
        phone = Phone("backup-test.db")
    }

    @After
    fun tearDown() {
        phone.close()
        others.forEach { it.close() }
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(120_000) { block() } }

    private fun stream(text: String): InputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private fun otherPhone(): Phone = Phone("backup-test-${others.size}.db").also { others += it }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = date.atTime(h, m).atZone(zone).toInstant()

    /** The exported text without the moment it was written, so two exports can be compared. */
    private fun comparable(text: String): BackupFile = BackupFormat.decode(text).copy(exportedAt = 0L, exportedZoneId = "UTC")

    private fun problemOf(text: String): BackupProblem = try {
        blocking { phone.backup.read(stream(text)) }
        fail("Expected the file to be refused")
        throw AssertionError()
    } catch (error: BackupException) {
        error.problem
    }

    /** Records something in every part of the app, through the app's own repositories. */
    private fun useTheApp(target: Phone) = blocking {
        val today = TodayRepository(target.db, time.provider)
        val quiet = QuietRepository(target.db, time.provider)
        val entries = TimeRepository(target.db, time.provider)
        val schedules = ScheduleRepository(target.db, time.provider)

        // Yesterday: priorities (one done), a step-up session, a quiet session ended early, and time.
        time.setLocal(day.minusDays(1).atTime(21, 0))
        today.add(day.minusDays(1), "Call the dentist")
        today.add(day.minusDays(1), "Water the plants")
        val first = today.observePriorities(day.minusDays(1)).first()
        today.setCompleted(first[0].id, true)
        val volume = target.exercise.startVolumeSession(SessionTimer.started(1_000, 3))
        target.exercise.finish(volume.id, 180, SessionTimer.paused(volume.timer, 601_000, 3))
        val short = quiet.start(SessionTimer.started(700_000, 3))
        quiet.endEarly(short.id, SessionTimer.paused(short.timer, 820_000, 3))
        assertTrue(entries.save(EntryDraft(null, TimeEntryKind.ACTUAL, "work", "deep work", at(day.minusDays(1), 9), at(day.minusDays(1), 13)), Resolution.None) is SaveResult.Saved)

        // Today: sleep across midnight, a carried-over priority, a full quiet session, a timed win, a plan and a schedule.
        time.setLocal(day.atTime(18, 0))
        assertTrue(entries.save(EntryDraft(null, TimeEntryKind.ACTUAL, "sleep", null, at(day.minusDays(1), 23), at(day, 7)), Resolution.None) is SaveResult.Saved)
        today.carryOver(day, listOf(first[1].id))
        today.dismissCarryOver(day)
        val full = quiet.start(SessionTimer.started(900_000, 3))
        quiet.complete(full.id, SessionTimer.paused(full.timer, 900_000 + QuietRules.TARGET_MILLIS, 3), 0)
        val timed = target.exercise.startTimedSession(1, SessionTimer.started(2_000_000, 3))!!
        target.exercise.finish(timed.id, 1000, SessionTimer.paused(timed.timer, 2_000_000 + 29 * 60_000L, 3))
        target.exercise.applyTimedResults()
        assertTrue(entries.save(EntryDraft(null, TimeEntryKind.PLANNED, "learning", null, at(day, 20), at(day, 21), zone = zone), Resolution.None) is SaveResult.Saved)
        schedules.approve(ApprovedProposal("My schedule", listOf(ProposalBlock("x", "sleep", 1380, 420, ScheduleRules.ALL_DAYS), ProposalBlock("y", "work", 540, 1020, ScheduleRules.WEEKDAYS)), day.minusDays(8), day.minusDays(1)))
        entries.setDistraction("work", true)
        target.preferences.markExerciseSafetyRead()
        target.preferences.markMyTimeObservationComplete()
        target.preferences.markVictorySeen(at(day, 17))
        target.preferences.setProgressPeriod("ThisMonth")
        target.preferences.recordFirstLaunchIfAbsent(day.minusDays(30))
    }

    @Test
    fun importingAFileAndExportingAgainGivesTheSameFile() {
        val original = BackupFormat.encode(BackupFixtures.full)

        val result = phone.import(original)

        assertEquals(ReplaceResult.Replaced(BackupSummary(3, 3, 3, 3, 2)), result)
        assertEquals(comparable(original), comparable(phone.exported()))
        // Zones and the fall-back night came back to the millisecond.
        val sleep = blocking { phone.db.timeDao().entry("t1") }!!
        assertEquals(Instant.parse("2026-11-01T03:00:00Z") to Instant.parse("2026-11-01T12:00:00Z"), sleep.startAt to sleep.endAt)
        assertEquals(ZoneId.of("America/New_York"), sleep.zoneId)
        val moved = blocking { phone.db.timeDao().entry("t2") }!!
        assertEquals(ZoneId.of("Europe/London") to ZoneId.of("Asia/Kolkata"), moved.zoneId to moved.endZoneId)
        assertEquals(ZoneId.of("+05:45"), blocking { phone.db.quietDao().session("q2") }!!.zoneId)
        assertEquals(1, phone.replaced.count.value)
    }

    @Test
    fun whatTheAppRecordedComesBackOnAFreshInstall() {
        useTheApp(phone)
        val exported = phone.exported()

        val fresh = otherPhone()
        val result = fresh.import(exported)

        assertTrue(result is ReplaceResult.Replaced)
        assertEquals(comparable(exported), comparable(fresh.exported()))
        // Row for row, the records the screens read are identical.
        val a = phone.db.backupDao()
        val b = fresh.db.backupDao()
        blocking {
            assertEquals(a.priorities(), b.priorities())
            assertEquals(3, b.priorities().size)
            assertEquals(a.dailyRecords(), b.dailyRecords())
            assertEquals(a.timeEntries(), b.timeEntries())
            assertEquals(3, b.timeEntries().size)
            assertEquals(a.schedules(), b.schedules())
            assertEquals(a.scheduleBlocks(), b.scheduleBlocks())
            assertEquals(a.levels(), b.levels())
            assertEquals(a.personalBests(), b.personalBests())
            assertEquals(a.categories(), b.categories())
            // Sessions: the same days, counts and times (only the closed timer's bookkeeping differs).
            assertEquals(
                a.finishedExerciseSessions().map { listOf(it.id, it.type, it.status, it.startDate, it.repetitions, it.timer.activeDurationMillis, it.endedAt, it.zoneId) },
                b.finishedExerciseSessions().map { listOf(it.id, it.type, it.status, it.startDate, it.repetitions, it.timer.activeDurationMillis, it.endedAt, it.zoneId) },
            )
            assertEquals(
                a.finishedQuietSessions().map { listOf(it.id, it.status, it.startDate, it.timer.activeDurationMillis, it.targetDurationMillis, it.endedAt) },
                b.finishedQuietSessions().map { listOf(it.id, it.status, it.startDate, it.timer.activeDurationMillis, it.targetDurationMillis, it.endedAt) },
            )
            assertEquals(2, b.finishedExerciseSessions().size)
            assertEquals(2, b.finishedQuietSessions().size)
            // Ana was beaten, so Mia is unlocked on the new phone too.
            assertEquals(listOf(true, true, false), b.levels().take(3).map { it.isUnlocked })
            assertEquals(phone.preferences.backupFlags(), fresh.preferences.backupFlags())
            assertTrue(fresh.preferences.hasReadExerciseSafety.first())
            assertTrue(fresh.preferences.myTimeObservationComplete.first())
            // Phone-specific flags do not travel.
            assertFalse(fresh.preferences.quietNotificationsAsked.first())
        }
    }

    @Test
    fun importReplacesEverythingThatWasThere() {
        useTheApp(phone)

        phone.import(BackupFormat.encode(BackupFixtures.full))

        blocking {
            assertEquals(listOf("p1", "p2", "p3"), phone.db.backupDao().priorities().map { it.id })
            assertEquals(listOf("t1", "t2", "t3"), phone.db.backupDao().timeEntries().map { it.id })
            assertEquals(listOf("e1", "e2", "e3"), phone.db.backupDao().finishedExerciseSessions().map { it.id })
            assertEquals(listOf("s1", "s2"), phone.db.backupDao().schedules().map { it.id })
            // Flags follow the file, even back: this file's period, and its distraction flags.
            assertEquals("ThisMonth", phone.preferences.progressPeriod.first())
            assertEquals("FourWeeks", phone.preferences.myTimeInsightsPeriod.first())
            assertEquals(Instant.ofEpochMilli(ms("2026-11-03T02:29:00Z")), phone.preferences.victorySeenUpTo.first())
            val flags = phone.db.backupDao().categories().associate { it.id to it.isDistraction }
            assertEquals(true, flags["work"])
            assertEquals(false, flags["phone_internet"])
            assertEquals(true, flags["entertainment"])
            // The fixed reference data is untouched: eleven categories, ten cats.
            assertEquals(11, flags.size)
            assertEquals(10, phone.db.backupDao().levels().size)
        }
    }

    @Test
    fun sessionsInProgressAreNotExportedAndBlockAnImport() {
        useTheApp(phone)
        val before = phone.exported()
        val entries = TimeRepository(phone.db, time.provider)
        val quiet = QuietRepository(phone.db, time.provider)

        // An open step-up session.
        val open = blocking { phone.exercise.startVolumeSession(SessionTimer.started(5_000_000, 3)) }
        assertEquals(setOf(InProgress.Exercise), blocking { phone.backup.inProgress() })
        assertEquals(comparable(before), comparable(phone.exported()))
        assertEquals(ReplaceResult.Blocked(setOf(InProgress.Exercise)), phone.import(BackupFormat.encode(BackupFixtures.full)))
        blocking { phone.exercise.discardEmpty(open.id) }

        // A quiet timer and a running activity.
        val session = blocking { quiet.start(SessionTimer.started(6_000_000, 3)) }
        blocking { entries.startCurrent("work", null) }
        assertEquals(setOf(InProgress.Quiet, InProgress.NowDoing), blocking { phone.backup.inProgress() })
        assertEquals(comparable(before), comparable(phone.exported()))
        assertEquals(ReplaceResult.Blocked(setOf(InProgress.Quiet, InProgress.NowDoing)), phone.import(BackupFormat.encode(BackupFixtures.full)))

        // Nothing was replaced, and the things in progress are still there.
        assertEquals(comparable(before), comparable(phone.exported()))
        assertEquals(session.id, blocking { phone.db.quietDao().openSessions() }.single().id)
        assertEquals("work", blocking { phone.db.timeDao().current() }!!.categoryId)
        assertEquals(0, phone.replaced.count.value)

        // Once they are finished, the import goes through.
        blocking {
            quiet.discard(session.id)
            entries.discardCurrent()
        }
        assertTrue(phone.import(BackupFormat.encode(BackupFixtures.full)) is ReplaceResult.Replaced)
    }

    @Test
    fun aFailureHalfwayThroughLeavesEverythingExactlyAsItWas() {
        useTheApp(phone)
        val before = phone.exported()
        val flagsBefore = blocking { phone.preferences.backupFlags() }
        // Rows that pass every earlier insert and then collide: the last table written fails.
        val valid = BackupMapper.toContents(BackupFixtures.full)
        val broken = valid.copy(scheduleBlocks = valid.scheduleBlocks + valid.scheduleBlocks.first())

        try {
            blocking { phone.backup.replaceAll(broken) }
            fail("Expected the import to fail")
        } catch (_: SQLException) {
            // The transaction was rolled back.
        }

        assertEquals(comparable(before), comparable(phone.exported()))
        assertEquals(flagsBefore, blocking { phone.preferences.backupFlags() })
        assertEquals(0, phone.replaced.count.value)
    }

    @Test
    fun aFileThatIsRefusedChangesNothing() {
        useTheApp(phone)
        val before = phone.exported()
        val good = BackupFormat.encode(BackupFixtures.full)

        assertEquals(BackupProblem.NotABackup, problemOf("holiday photos"))
        assertEquals(BackupProblem.TooNew, problemOf(good.replace("\"formatVersion\": 1", "\"formatVersion\": 7")))
        assertEquals(BackupProblem.Overlap, problemOf(BackupFormat.encode(BackupFixtures.full.copy(timeEntries = BackupFixtures.full.timeEntries.map { it.copy(kind = "ACTUAL", startAt = ms("2026-11-08T08:00:00Z"), endAt = ms("2026-11-08T09:00:00Z")) }.mapIndexed { i, e -> e.copy(id = "o$i") }))))
        assertEquals(BackupProblem.Sessions, problemOf(good.replace("\"status\": \"ENDED_EARLY\"", "\"status\": \"RUNNING\"")))
        assertEquals(BackupProblem.Unreadable, problemOf(object : InputStream() {
            override fun read(): Int = throw java.io.IOException("card removed")
        }))

        assertEquals(comparable(before), comparable(phone.exported()))
        assertEquals(0, phone.replaced.count.value)
    }

    private fun problemOf(input: InputStream): BackupProblem = try {
        blocking { phone.backup.read(input) }
        fail("Expected the file to be refused")
        throw AssertionError()
    } catch (error: BackupException) {
        error.problem
    }

    @Test
    fun aFileLargerThanAnyBackupIsRefusedUnread() {
        val endless = object : InputStream() {
            override fun read(): Int = ' '.code

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(' '.code.toByte(), off, off + len)
                return len
            }
        }
        assertEquals(BackupProblem.TooLarge, problemOf(endless))
    }

    @Test
    fun importBringsCatLeagueInLineWithTheResultsInTheFile() {
        // A winning run at level 1, in a file whose levels still say nothing was beaten.
        val file = BackupFixtures.full.copy(
            challengeLevels = BackupFixtures.levels.map { it.copy(isUnlocked = it.level == 1, unlockedAt = null, unlockedZoneId = null, beatenAt = null, beatenZoneId = null) },
            personalBests = emptyList(),
            exerciseSessions = BackupFixtures.full.exerciseSessions.filter { it.id != "e3" },
        )

        phone.import(BackupFormat.encode(file))

        val levels = blocking { phone.db.backupDao().levels() }
        assertEquals(Instant.ofEpochMilli(ms("2026-11-03T02:29:00Z")), levels[0].beatenAt)
        assertEquals(listOf(true, true, false), levels.take(3).map { it.isUnlocked })
        val best = blocking { phone.db.backupDao().personalBests() }.single()
        assertEquals("e2" to 29 * 60_000L, best.sessionId to best.value)
        // A tie or a slower run unlocks nothing: only level 1 was beaten.
        assertNull(levels[1].beatenAt)
    }

    @Test
    fun fiveYearsOfHistoryExportAndImportUnchanged() {
        val start = LocalDate.of(2021, 11, 10)
        val days = 5 * 365
        val entries = ArrayList<TimeEntryDto>()
        val quiet = ArrayList<QuietSessionDto>()
        val exercise = ArrayList<ExerciseSessionDto>()
        val priorities = ArrayList<PriorityDto>()
        val categories = listOf("sleep", "personal_care", "work", "family", "household", "learning", "phone_internet", "rest")
        repeat(days) { i ->
            val date = start.plusDays(i.toLong())
            val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
            // Eight three-hour entries fill the day exactly.
            categories.forEachIndexed { slot, category ->
                val from = midnight + slot * 3 * 3_600_000L
                entries += TimeEntryDto("t$i-$slot", "ACTUAL", category, from, from + 3 * 3_600_000L, zone.id, zone.id, null, from, zone.id, from, zone.id)
            }
            quiet += QuietSessionDto("q$i", "COMPLETED", midnight + 6 * 3_600_000L, zone.id, midnight + 6 * 3_600_000L + 300_000, zone.id, 300_000, 300_000, midnight, zone.id)
            exercise += ExerciseSessionDto("e$i", "VOLUME", "COMPLETED", null, midnight + 7 * 3_600_000L, zone.id, midnight + 7 * 3_600_000L + 600_000, zone.id, 100, 600_000, null, midnight, zone.id)
            repeat(3) { position ->
                priorities += PriorityDto("p$i-$position", date.toString(), position, "Priority $position", true, midnight, zone.id, null, midnight, zone.id, midnight, zone.id)
            }
        }
        val file = BackupFixtures.full.copy(
            dailyRecords = emptyList(), priorities = priorities, exerciseSessions = exercise, personalBests = emptyList(),
            challengeLevels = BackupFixtures.levels.map { it.copy(isUnlocked = it.level == 1, unlockedAt = null, unlockedZoneId = null, beatenAt = null, beatenZoneId = null) },
            quietSessions = quiet, timeEntries = entries, schedules = emptyList(),
        )
        val text = BackupFormat.encode(file)
        assertTrue("A five-year backup stays far below the size limit", text.toByteArray(Charsets.UTF_8).size < BackupFormat.MAX_BYTES / 2)

        val result = phone.import(text)

        assertEquals(ReplaceResult.Replaced(BackupSummary(days * 3, days, days, days * 8, 0)), result)
        assertEquals(blocking { phone.backup.summary() }, BackupSummary(days * 3, days, days, days * 8, 0))
        assertEquals(comparable(text), comparable(phone.exported()))
    }

    @Test
    fun theDatabaseVersionWrittenIntoTheFileIsTheRealOne() {
        assertEquals(phone.db.openHelper.readableDatabase.version, BackupRepository.DATABASE_VERSION)
    }
}
