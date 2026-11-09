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
    fun anEntryCrossingMidnightShowsOnBothDaysAndTotalsSplit() {
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
}
