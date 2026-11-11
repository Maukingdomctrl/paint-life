package com.mauforge.paintlife.mytime

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Time entries on a real database file with a hand-driven clock. */
@RunWith(AndroidJUnit4::class)
class TimeRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), zone)
    private lateinit var file: File
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TimeRepository

    @Before
    fun setUp() {
        file = File(context.cacheDir, "time-repository-test.db").also { context.deleteDatabase(it.absolutePath) }
        open()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(file.absolutePath)
    }

    private fun open() {
        db = Room.databaseBuilder(context, PaintLifeDatabase::class.java, file.absolutePath).addCallback(SeedCallback).build()
        repository = TimeRepository(db, time.provider)
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(120_000) { block() } }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun draft(start: Instant, end: Instant, category: String = "work", id: String? = null, note: String? = null) =
        EntryDraft(id, TimeEntryKind.ACTUAL, category, note, start, end)

    private fun save(start: Instant, end: Instant, category: String = "work", resolution: Resolution = Resolution.None): SaveResult =
        blocking { repository.save(draft(start, end, category), resolution) }

    private fun saved(start: Instant, end: Instant, category: String = "work"): TimeEntryEntity =
        (save(start, end, category) as SaveResult.Saved).entry

    /** Everything stored for [day], as (category, start, end) in local "HH:mm". */
    private fun stored(date: LocalDate = day): List<Triple<String, String, String>> = blocking {
        repository.observeDayEntries(date).first().map {
            Triple(it.entry.categoryId, TimeRules.wallTime(it.entry.startAt, zone).text, TimeRules.wallTime(it.entry.endAt, zone).text)
        }
    }

    @Test
    fun savesWithZonesAndRoundsToTheMinute() {
        val entry = saved(at(day, 9).plusSeconds(42), at(day, 10).plusSeconds(5))
        assertEquals(at(day, 9), entry.startAt)
        assertEquals(at(day, 10), entry.endAt)
        assertEquals(zone, entry.zoneId)
        assertEquals(zone, entry.endZoneId)
        assertEquals(zone, entry.createdZoneId)
    }

    @Test
    fun tooShortTooLongOrUnknownCategoryIsRefused() {
        assertEquals(SaveResult.Invalid, save(at(day, 9), at(day, 9)))
        assertEquals(SaveResult.Invalid, save(at(day, 9), at(day, 9).plus(Duration.ofHours(25))))
        assertEquals(SaveResult.Invalid, save(at(day, 9), at(day, 10), category = "nope"))
        assertEquals(emptyList<Any>(), stored())
    }

    @Test
    fun anOverlapSavesNothingUntilTheUserChooses() {
        saved(at(day, 9), at(day, 12))
        val result = save(at(day, 11), at(day, 13), "family")
        assertTrue(result is SaveResult.Overlaps)
        assertEquals(listOf(Triple("work", "09:00", "12:00")), stored())
    }

    @Test
    fun fittingTrimsSplitsAndReplacesInOneGoAndUndoPutsItAllBack() {
        saved(at(day, 8), at(day, 10))
        saved(at(day, 10, 15), at(day, 10, 45), "household")
        saved(at(day, 10, 50), at(day, 12), "learning")

        val result = save(at(day, 9, 30), at(day, 11), "family", Resolution.Fit) as SaveResult.Saved

        assertEquals(
            listOf(Triple("work", "08:00", "09:30"), Triple("family", "09:30", "11:00"), Triple("learning", "11:00", "12:00")),
            stored(),
        )
        assertTrue(blocking { repository.undo(result.change!!) })
        assertEquals(
            listOf(Triple("work", "08:00", "10:00"), Triple("household", "10:15", "10:45"), Triple("learning", "10:50", "12:00")),
            stored(),
        )
    }

    @Test
    fun aShortEntryInsideALongOneSplitsIt() {
        saved(at(day, 9), at(day, 12, 30))
        save(at(day, 10), at(day, 10, 20), "family", Resolution.Fit)
        assertEquals(
            listOf(Triple("work", "09:00", "10:00"), Triple("family", "10:00", "10:20"), Triple("work", "10:20", "12:30")),
            stored(),
        )
    }

    @Test
    fun replacingRemovesEveryOverlappingEntry() {
        saved(at(day, 8), at(day, 10))
        saved(at(day, 10, 30), at(day, 12), "learning")
        save(at(day, 9), at(day, 11), "family", Resolution.Replace)
        assertEquals(listOf(Triple("family", "09:00", "11:00")), stored())
    }

    @Test
    fun editingAnEntryNeverOverlapsItself() {
        val entry = saved(at(day, 9), at(day, 10))
        val result = blocking { repository.save(draft(at(day, 9, 30), at(day, 11), id = entry.id), Resolution.None) }
        assertTrue(result is SaveResult.Saved)
        assertEquals(listOf(Triple("work", "09:30", "11:00")), stored())
        assertEquals(SaveResult.Missing, blocking { repository.save(draft(at(day, 9), at(day, 10), id = "gone"), Resolution.None) })
    }

    @Test
    fun deleteIsImmediateAndUndoBringsItBackUnlessSomethingTookItsPlace() {
        val entry = saved(at(day, 9), at(day, 10))
        val change = blocking { repository.delete(entry.id) }!!
        assertEquals(emptyList<Any>(), stored())
        assertTrue(blocking { repository.undo(change) })
        assertEquals(listOf(Triple("work", "09:00", "10:00")), stored())

        val again = blocking { repository.delete(entry.id) }!!
        saved(at(day, 9, 30), at(day, 9, 45), "family")
        assertFalse(blocking { repository.undo(again) })
        assertEquals(listOf(Triple("family", "09:30", "09:45")), stored())
    }

    @Test
    fun actualTimeThatHasNotHappenedYetIsRefusedButCanBePlanned() {
        // It is 18:00. Later today and tomorrow have not happened yet.
        assertEquals(SaveResult.Future, save(at(day, 17), at(day, 19)))
        assertEquals(SaveResult.Future, save(at(day, 23), at(day.plusDays(1), 7), "sleep"))
        assertEquals(SaveResult.Future, save(at(day.plusDays(1), 9), at(day.plusDays(1), 10)))
        assertEquals(emptyList<Triple<String, String, String>>(), stored())
        // Up to now is fine, and "now" follows the pickers' 5-minute grid (18:02 rounds to 18:00, 18:03 to 18:05).
        assertTrue(save(at(day, 17), at(day, 18)) is SaveResult.Saved)
        time.clock.instant = at(day, 18, 3)
        assertTrue(save(at(day, 18), at(day, 18, 5)) is SaveResult.Saved)
        assertEquals(SaveResult.Future, save(at(day, 18, 5), at(day, 18, 10)))
        // The same stretch can be planned, and an existing entry cannot be stretched past now.
        val plan = blocking { repository.save(EntryDraft(null, TimeEntryKind.PLANNED, "work", null, at(day, 19), at(day, 21)), Resolution.None) }
        assertTrue(plan is SaveResult.Saved)
        val first = blocking { repository.observeDayEntries(day).first() }.first().entry
        assertEquals(SaveResult.Future, blocking { repository.save(draft(first.startAt, at(day, 20), id = first.id), Resolution.None) })
    }

    @Test
    fun anEntryCrossingMidnightShowsOnBothDaysAndTotalsSplit() {
        // Logged the next morning: actual time is recorded once it has happened.
        time.setLocal(day.plusDays(1).atTime(8, 0))
        saved(at(day, 23), at(day.plusDays(1), 7), "sleep")
        val first = blocking { repository.observeDayEntries(day).first() }
        val second = blocking { repository.observeDayEntries(day.plusDays(1)).first() }
        assertEquals(60L, DayView.categoryTotals(DayView.onDay(first, day, zone), day, zone).single().minutes)
        assertEquals(420L, DayView.categoryTotals(DayView.onDay(second, day.plusDays(1), zone), day.plusDays(1), zone).single().minutes)
    }

    @Test
    fun runningActivityStopsIntoAnEntryAndSwitchingEndsTheCurrentOne() {
        time.setLocal(day.atTime(9, 0, 20))
        assertEquals(RunResult.Done(null), blocking { repository.startCurrent("work", "emails") })
        time.setLocal(day.atTime(10, 30, 50))

        val switched = blocking { repository.startCurrent("learning", null) } as RunResult.Done
        assertEquals("emails", switched.saved?.note)
        time.setLocal(day.atTime(11, 15))
        blocking { repository.stopCurrent() }

        assertEquals(listOf(Triple("work", "09:00", "10:30"), Triple("learning", "10:30", "11:15")), stored())
        assertNull(blocking { repository.observeCurrent().first() })
    }

    @Test
    fun aRunningActivityUnderAMinuteIsDroppedAndOneOverSixteenHoursAsksFirst() {
        time.setLocal(day.atTime(9, 0))
        blocking { repository.startCurrent("work", null) }
        time.setLocal(day.atTime(9, 0, 40))
        assertEquals(RunResult.Done(null), blocking { repository.stopCurrent() })
        assertEquals(emptyList<Any>(), stored())

        time.setLocal(day.atTime(1, 0))
        blocking { repository.startCurrent("work", null) }
        time.setLocal(day.atTime(18, 0))
        val pending = blocking { repository.stopCurrent() }
        assertTrue(pending is RunResult.ConfirmEnd)
        assertTrue(blocking { repository.observeCurrent().first() } != null)

        // Confirmed in the sheet: saving the draft clears the running activity.
        val confirmed = (pending as RunResult.ConfirmEnd).draft.copy(end = at(day, 9))
        assertTrue(blocking { repository.save(confirmed, Resolution.None) } is SaveResult.Saved)
        assertNull(blocking { repository.observeCurrent().first() })
    }

    @Test
    fun stoppingOntoAnotherEntryAsksBeforeChangingIt() {
        saved(at(day, 10), at(day, 10, 30), "family")
        time.setLocal(day.atTime(9, 0))
        blocking { repository.startCurrent("work", null) }
        time.setLocal(day.atTime(11, 0))
        val result = blocking { repository.stopCurrent() }
        assertTrue(result is RunResult.Overlaps)
        assertEquals(listOf(Triple("family", "10:00", "10:30")), stored())
    }

    @Test
    fun theRunningActivitySurvivesARestart() {
        time.setLocal(day.atTime(9, 0))
        blocking { repository.startCurrent("work", null) }
        db.close()
        open()
        assertEquals("work", blocking { repository.observeCurrent().first() }?.categoryId)
        time.setLocal(day.atTime(10, 0))
        blocking { repository.stopCurrent() }
        assertEquals(listOf(Triple("work", "09:00", "10:00")), stored())
    }

    @Test
    fun entriesSurviveARestart() {
        saved(at(day, 9), at(day, 10))
        db.close()
        open()
        assertEquals(listOf(Triple("work", "09:00", "10:00")), stored())
    }

    @Test
    fun recentActivitiesAreDistinctNewestFirst() {
        blocking { repository.save(draft(at(day, 8), at(day, 9), "work", note = "emails"), Resolution.None) }
        blocking { repository.save(draft(at(day, 9), at(day, 10), "learning"), Resolution.None) }
        blocking { repository.save(draft(at(day, 10), at(day, 11), "work", note = "emails"), Resolution.None) }
        val recent = blocking { repository.observeRecent().first() }
        assertEquals(listOf("work" to "emails", "learning" to null), recent.map { it.categoryId to it.note })
    }

    @Test
    fun sleepCountsAsLoggedForTheMorningItEndsOn() {
        assertFalse(blocking { repository.observeSleepLogged(day, zone).first() })
        saved(at(day.minusDays(1), 23), at(day, 7), "sleep")
        assertTrue(blocking { repository.observeSleepLogged(day, zone).first() })
        assertFalse(blocking { repository.observeSleepLogged(day.plusDays(1), zone).first() })
    }

    @Test
    fun observedDaysCountDaysWithAnHour() {
        (0L until 3L).forEach { saved(at(day.minusDays(it), 9), at(day.minusDays(it), 10)) }
        saved(at(day.minusDays(5), 9), at(day.minusDays(5), 9, 30))
        assertEquals(3, blocking { repository.observeObservedDays().first() })
    }

    @Test
    fun fiveYearsOfHistoryReadADayThroughTheIndex() {
        // About 40,000 entries: 22 a day for five years.
        val start = day.minusYears(5)
        blocking {
            db.withTransaction {
                var date = start
                while (date < day) {
                    (0 until 22).forEach { slot ->
                        val from = at(date, 0).plus(Duration.ofMinutes(slot * 65L))
                        db.timeDao().insert(
                            TimeEntryEntity(
                                UUID.randomUUID().toString(), TimeEntryKind.ACTUAL, "work", from, from.plus(Duration.ofMinutes(60)),
                                zone, zone, null, from, zone, from, zone,
                            ),
                        )
                    }
                    date = date.plusDays(1)
                }
            }
        }
        val target = day.minusDays(100)
        val entries = blocking { repository.observeDayEntries(target).first() }
        assertEquals(22, DayView.onDay(entries, target, zone).size)

        val (from, to) = DayView.readWindow(target)
        val plan = blocking {
            db.withTransaction {
                db.query(
                    "EXPLAIN QUERY PLAN SELECT * FROM time_entry WHERE kind = 'ACTUAL' AND startAt >= ? AND startAt < ? AND endAt > ?",
                    arrayOf<Any>(TimeRules.earliestStart(from).toEpochMilli(), to.toEpochMilli(), from.toEpochMilli()),
                ).use { cursor ->
                    buildString { while (cursor.moveToNext()) append(cursor.getString(cursor.getColumnIndexOrThrow("detail"))).append('\n') }
                }
            }
        }
        assertTrue(plan, plan.contains("index_time_entry_kind_startAt"))
    }

    // Sleep and Wake up buttons

    private fun sleepAt(dateTime: LocalDateTime): SleepResult {
        time.setLocal(dateTime)
        return blocking { repository.startSleep() }
    }

    private fun wakeAt(dateTime: LocalDateTime): SleepResult {
        time.setLocal(dateTime)
        return blocking { repository.wakeUp() }
    }

    private fun current(): CurrentActivityEntity? = blocking { repository.observeCurrent().first() }

    private val tomorrow: LocalDate = day.plusDays(1)

    @Test
    fun sleepStartsAtThisMinuteAndWakeUpSavesItAcrossMidnight() {
        val asleep = sleepAt(day.atTime(23, 14, 40)) as SleepResult.Done
        assertNull(asleep.tap.saved)
        assertEquals("sleep", current()?.categoryId)
        assertEquals(at(day, 23, 14), current()?.startAt)
        assertEquals(emptyList<Any>(), stored())

        val awake = wakeAt(tomorrow.atTime(7, 2, 30)) as SleepResult.Done

        val entry = awake.tap.saved!!
        assertEquals(Triple("sleep", at(day, 23, 14), at(tomorrow, 7, 2)), Triple(entry.categoryId, entry.startAt, entry.endAt))
        assertNull(current())
        // One stored entry; each day gets its share when it is read.
        assertEquals(mapOf(day to 46L, tomorrow to 422L), TimeRules.minutesByDay(entry.startAt, entry.endAt, entry.zoneId))
        assertEquals(listOf(Triple("sleep", "23:14", "07:02")), stored(tomorrow))
        assertTrue(blocking { repository.observeSleepLogged(tomorrow, zone).first() })
    }

    @Test
    fun sleepEndsTheRunningActivityAndUndoPutsItBackRunning() {
        time.setLocal(day.atTime(9, 0))
        blocking { repository.startCurrent("work", "emails") }
        val work = current()

        val tap = (sleepAt(day.atTime(23, 0)) as SleepResult.Done).tap
        assertEquals(listOf(Triple("work", "09:00", "23:00")), stored())
        assertEquals("sleep", current()?.categoryId)

        assertTrue(blocking { repository.undoSleepTap(tap) })
        assertEquals(emptyList<Any>(), stored())
        assertEquals(work, current())
        // A second Undo of the same tap finds things changed and does nothing.
        assertFalse(blocking { repository.undoSleepTap(tap) })
        assertEquals(work, current())
    }

    @Test
    fun undoingWakeUpLetsTheSleepRunOnFromWhereItStarted() {
        sleepAt(day.atTime(23, 14))
        val tap = (wakeAt(tomorrow.atTime(7, 0)) as SleepResult.Done).tap

        assertTrue(blocking { repository.undoSleepTap(tap) })
        assertEquals(emptyList<Any>(), stored(tomorrow))
        assertEquals(at(day, 23, 14), current()?.startAt)

        wakeAt(tomorrow.atTime(7, 30))
        assertEquals(listOf(Triple("sleep", "23:14", "07:30")), stored(tomorrow))
    }

    @Test
    fun undoIsRefusedOnceAnythingChangedSinceTheTap() {
        val asleep = (sleepAt(day.atTime(23, 14)) as SleepResult.Done).tap
        val awake = (wakeAt(tomorrow.atTime(7, 0)) as SleepResult.Done).tap
        // Undo of the Sleep tap after waking: the sleep is no longer running.
        assertFalse(blocking { repository.undoSleepTap(asleep) })

        // The saved sleep is edited before Undo of the Wake up tap.
        val entry = awake.saved!!
        blocking { repository.save(draft(entry.startAt, at(tomorrow, 6, 30), "sleep", id = entry.id), Resolution.None) }
        assertFalse(blocking { repository.undoSleepTap(awake) })
        assertEquals(listOf(Triple("sleep", "23:14", "06:30")), stored(tomorrow))
        assertNull(current())
    }

    @Test
    fun repeatedTapsActOnce() {
        sleepAt(day.atTime(23, 14))
        assertEquals(SleepResult.Ignored, sleepAt(day.atTime(23, 20)))
        assertEquals(at(day, 23, 14), current()?.startAt)

        assertTrue(wakeAt(tomorrow.atTime(7, 0)) is SleepResult.Done)
        assertEquals(SleepResult.Ignored, wakeAt(tomorrow.atTime(7, 1)))
        assertEquals(listOf(Triple("sleep", "23:14", "07:00")), stored(tomorrow))

        // Wake up never stops some other running activity.
        blocking { repository.startCurrent("work", null) }
        assertEquals(SleepResult.Ignored, wakeAt(tomorrow.atTime(9, 0)))
        assertEquals("work", current()?.categoryId)
    }

    @Test
    fun aSleepUnderAMinuteIsNotSavedAndCanStillBeUndone() {
        sleepAt(day.atTime(23, 14))
        val tap = (wakeAt(day.atTime(23, 14, 40)) as SleepResult.Done).tap
        assertNull(tap.saved)
        assertEquals(emptyList<Any>(), stored())
        assertNull(current())

        assertTrue(blocking { repository.undoSleepTap(tap) })
        assertEquals(at(day, 23, 14), current()?.startAt)
    }

    @Test
    fun aZoneChangeOvernightKeepsTheZoneAtEachEnd() {
        sleepAt(day.atTime(22, 0))
        // Asleep on a flight: the phone lands in Kolkata eight hours later.
        val kolkata = ZoneId.of("Asia/Kolkata")
        time.zone = kolkata
        time.clock.instant = at(day, 22).plus(Duration.ofHours(8))

        val entry = (blocking { repository.wakeUp() } as SleepResult.Done).tap.saved!!

        assertEquals(zone, entry.zoneId)
        assertEquals(kolkata, entry.endZoneId)
        assertEquals(480L, entry.durationMinutes)
        // Split at midnight in the zone it started in: two hours before, six after.
        assertEquals(mapOf(day to 120L, tomorrow to 360L), TimeRules.minutesByDay(entry.startAt, entry.endAt, entry.zoneId))
    }

    @Test
    fun dstNightsAreMeasuredInRealHours() {
        // Clocks go forward on 29 March 2026: 23:00 to 07:00 is seven hours.
        val spring = LocalDate.of(2026, 3, 28)
        sleepAt(spring.atTime(23, 0))
        val short = (wakeAt(spring.plusDays(1).atTime(7, 0)) as SleepResult.Done).tap.saved!!
        assertEquals(7 * 60L, short.durationMinutes)

        // Clocks go back on 25 October 2026: the same wall-clock night is nine hours.
        val autumn = LocalDate.of(2026, 10, 24)
        sleepAt(autumn.atTime(23, 0))
        val long = (wakeAt(autumn.plusDays(1).atTime(7, 0)) as SleepResult.Done).tap.saved!!
        assertEquals(9 * 60L, long.durationMinutes)
        assertEquals(mapOf(autumn to 60L, autumn.plusDays(1) to 480L), TimeRules.minutesByDay(long.startAt, long.endAt, long.zoneId))
    }

    @Test
    fun aSleepOverSixteenHoursAsksForItsEndAndChangesNothing() {
        sleepAt(day.atTime(1, 0))
        val pending = wakeAt(day.atTime(18, 0)) as SleepResult.Pending
        assertTrue(pending.run is RunResult.ConfirmEnd)
        assertEquals("sleep", current()?.categoryId)
        assertEquals(emptyList<Any>(), stored())
    }

    @Test
    fun sleepOverAnActivityThatNeedsTheUserWaitsForThem() {
        time.setLocal(day.atTime(1, 0))
        blocking { repository.startCurrent("work", null) }
        val pending = sleepAt(day.atTime(23, 0)) as SleepResult.Pending
        assertTrue(pending.run is RunResult.ConfirmEnd)
        assertEquals("work", current()?.categoryId)
    }

    @Test
    fun wakingOntoAnEntryLoggedDuringTheNightAsksBeforeChangingIt() {
        sleepAt(day.atTime(2, 0))
        time.setLocal(day.atTime(4, 0))
        saved(at(day, 3), at(day, 3, 30), "family")

        val pending = wakeAt(day.atTime(7, 0)) as SleepResult.Pending

        assertTrue(pending.run is RunResult.Overlaps)
        assertEquals(listOf(Triple("family", "03:00", "03:30")), stored())
        assertEquals("sleep", current()?.categoryId)
    }

    @Test
    fun aRunningSleepSurvivesARestart() {
        sleepAt(day.atTime(23, 14))
        db.close()
        open()
        assertEquals(at(day, 23, 14), current()?.startAt)
        wakeAt(tomorrow.atTime(7, 0))
        assertEquals(listOf(Triple("sleep", "23:14", "07:00")), stored(tomorrow))
    }

    @Test
    fun aNapIsSavedButIsNotLastNightsSleepAndDoesNotMoveTheUsualTimes() {
        sleepAt(day.atTime(13, 0))
        wakeAt(day.atTime(13, 40))
        assertEquals(listOf(Triple("sleep", "13:00", "13:40")), stored())
        assertFalse(blocking { repository.observeSleepLogged(day, zone).first() })
        assertEquals(at(day, 23) to at(tomorrow, 7), blocking { repository.sleepPrefill(tomorrow, zone) })

        // A night's sleep does both.
        sleepAt(day.atTime(23, 30))
        wakeAt(tomorrow.atTime(6, 30))
        assertTrue(blocking { repository.observeSleepLogged(tomorrow, zone).first() })
        assertEquals(at(tomorrow, 23, 30) to at(tomorrow.plusDays(1), 6, 30), blocking { repository.sleepPrefill(tomorrow.plusDays(1), zone) })
    }
}
