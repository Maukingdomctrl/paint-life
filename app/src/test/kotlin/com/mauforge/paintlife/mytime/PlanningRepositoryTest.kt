package com.mauforge.paintlife.mytime

import android.content.Context
import androidx.room.Room
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The planned layer, schedules and distraction flags on a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class PlanningRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(8, 0), zone)
    private lateinit var db: PaintLifeDatabase
    private lateinit var entries: TimeRepository
    private lateinit var schedules: ScheduleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        entries = TimeRepository(db, time.provider)
        schedules = ScheduleRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun save(kind: TimeEntryKind, category: String, start: Instant, end: Instant, resolution: Resolution = Resolution.None) =
        blocking { entries.save(EntryDraft(null, kind, category, null, start, end, zone = zone), resolution) }

    private fun planned(date: LocalDate = day): List<Triple<String, String, String>> = blocking {
        val (from, to) = DayView.readWindow(date)
        entries.observeEntries(TimeEntryKind.PLANNED, from, to).first()
            .filter { it.entry.startAt.atZone(zone).toLocalDate() == date }
            .map { Triple(it.entry.categoryId, TimeRules.wallTime(it.entry.startAt, zone).text, TimeRules.wallTime(it.entry.endAt, zone).text) }
    }

    private fun approveWork(): String {
        val result = blocking {
            schedules.approve(ApprovedProposal("Week", listOf(ProposalBlock("x", "work", 9 * 60, 17 * 60, ScheduleRules.ALL_DAYS)), null, null))
        }
        return (result as ApproveResult.Approved).scheduleId
    }

    @Test
    fun plannedEntriesNeverCountAsActual() {
        (0 until 7).forEach { back ->
            val d = day.minusDays(back.toLong())
            assertTrue(save(TimeEntryKind.PLANNED, "work", at(d, 9), at(d, 17)) is SaveResult.Saved)
        }
        assertEquals(0, blocking { entries.observeObservedDays().first() })
        assertEquals(emptyList<EntryWithCategory>(), blocking { entries.observeDayEntries(day).first() })
        assertEquals(emptyList<RecentActivityRow>(), blocking { entries.observeRecent().first() })
        assertNull(blocking { entries.lastActualEnd(at(day, 23)) })
        // The layers are independent: an actual entry at the same time saves without overlap.
        assertTrue(save(TimeEntryKind.ACTUAL, "learning", at(day, 9), at(day, 10)) is SaveResult.Saved)
    }

    @Test
    fun plannedEntriesNeverOverlapEachOther() {
        save(TimeEntryKind.PLANNED, "work", at(day, 9), at(day, 12))
        val result = save(TimeEntryKind.PLANNED, "family", at(day, 11), at(day, 13))
        assertTrue(result is SaveResult.Overlaps)
        assertEquals(listOf(Triple("work", "09:00", "12:00")), planned())
    }

    @Test
    fun approvingMakesTheScheduleActiveFromTodayAndEndsThePreviousOneYesterday() {
        val first = approveWork()
        time.setLocal(day.plusDays(3).atTime(8, 0))
        val second = approveWork()
        val stored = blocking { schedules.observeSchedules().first() }.associateBy { it.schedule.id }
        assertEquals(day, stored.getValue(first).schedule.activeFrom)
        assertEquals(day.plusDays(2), stored.getValue(first).schedule.activeUntil)
        assertEquals(ScheduleStatus.ARCHIVED, stored.getValue(first).schedule.status)
        assertEquals(day.plusDays(3), stored.getValue(second).schedule.activeFrom)
        assertNull(stored.getValue(second).schedule.activeUntil)
    }

    @Test
    fun anInvalidProposalWritesNothing() {
        val overlapping = listOf(
            ProposalBlock("a", "work", 9 * 60, 12 * 60, ScheduleRules.WEEKDAYS),
            ProposalBlock("b", "learning", 11 * 60, 13 * 60, ScheduleRules.ALL_DAYS),
        )
        assertEquals(ApproveResult.Invalid, blocking { schedules.approve(ApprovedProposal("x", overlapping, null, null)) })
        assertEquals(ApproveResult.Invalid, blocking { schedules.approve(ApprovedProposal("x", emptyList(), null, null)) })
        assertEquals(0, blocking { db.timeDao().scheduleCount() })
    }

    @Test
    fun changeForThisDayCopiesTheScheduleAndUndoPutsItBack() {
        approveWork()
        val change = blocking { entries.copyScheduleDay(day, zone) }!!
        assertEquals(listOf(Triple("work", "09:00", "17:00")), planned())
        // Other days still follow the schedule (nothing copied there).
        assertEquals(emptyList<Any>(), planned(day.plusDays(1)))
        assertTrue(blocking { entries.undo(change) })
        assertEquals(emptyList<Any>(), planned())
    }

    @Test
    fun aNewPlanOnAScheduleDayKeepsTheRestOfThatDaysPlan() {
        approveWork()
        // Without a choice about the overlap, nothing is written (the copy is rolled back).
        assertTrue(save(TimeEntryKind.PLANNED, "family", at(day, 12), at(day, 13)) is SaveResult.Overlaps)
        assertEquals(emptyList<Any>(), planned())

        val saved = save(TimeEntryKind.PLANNED, "family", at(day, 12), at(day, 13), Resolution.Fit) as SaveResult.Saved
        assertEquals(
            listOf(Triple("work", "09:00", "12:00"), Triple("family", "12:00", "13:00"), Triple("work", "13:00", "17:00")),
            planned(),
        )
        // Undo returns the day to the schedule.
        assertTrue(blocking { entries.undo(saved.change!!) })
        assertEquals(emptyList<Any>(), planned())
    }

    @Test
    fun aPlanOutsideTheScheduleBlocksStillCopiesTheDay() {
        approveWork()
        val saved = save(TimeEntryKind.PLANNED, "exercise", at(day, 18), at(day, 19)) as SaveResult.Saved
        assertEquals(listOf(Triple("work", "09:00", "17:00"), Triple("exercise", "18:00", "19:00")), planned())
        assertTrue(blocking { entries.undo(saved.change!!) })
        assertEquals(emptyList<Any>(), planned())
    }

    @Test
    fun theDistractionFlagCanBeChanged() {
        blocking { entries.setDistraction("learning", true) }
        blocking { entries.setDistraction("phone_internet", false) }
        val cats = blocking { entries.observeCategories().first() }.associateBy { it.id }
        assertTrue(cats.getValue("learning").isDistraction)
        assertEquals(false, cats.getValue("phone_internet").isDistraction)
    }
}
