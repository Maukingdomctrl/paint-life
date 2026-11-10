package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.mytime.PlanningFixtures.entry
import com.mauforge.paintlife.mytime.PlanningFixtures.london
import com.mauforge.paintlife.mytime.PlanningFixtures.minutes
import com.mauforge.paintlife.mytime.PlanningFixtures.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannedLayerTest {

    private val monday: LocalDate = LocalDate.of(2026, 11, 9)

    private fun labels(spans: List<PlannedSpan>, date: LocalDate) = spans.map {
        val block = DayView.plannedBlock(it, null, date, london)!!
        Triple(it.categoryId, block.startLabel, block.endLabel)
    }

    @Test
    fun aDayWithoutPlansOfItsOwnShowsTheSchedule() {
        val s = schedule(monday, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.WEEKDAYS))
        val spans = PlannedLayer.forDay(monday, london, emptyList(), listOf(s))
        assertEquals(listOf(Triple("work", "09:00", "17:00")), labels(spans, monday))
        assertTrue(spans.single().fromSchedule)
        // Saturday is not in the block's days.
        assertEquals(emptyList<PlannedSpan>(), PlannedLayer.forDay(monday.plusDays(5), london, emptyList(), listOf(s)))
    }

    @Test
    fun ownPlansReplaceTheScheduleForThatDayOnly() {
        val s = schedule(monday, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS))
        val own = listOf(entry("family", monday.atTime(10, 0), monday.atTime(12, 0), TimeEntryKind.PLANNED))
        assertEquals(listOf(Triple("family", "10:00", "12:00")), labels(PlannedLayer.forDay(monday, london, own, listOf(s)), monday))
        // Tuesday still follows the schedule.
        assertEquals(listOf(Triple("work", "09:00", "17:00")), labels(PlannedLayer.forDay(monday.plusDays(1), london, own, listOf(s)), monday.plusDays(1)))
    }

    @Test
    fun aBlockCrossingMidnightReachesIntoTheNextDay() {
        val s = schedule(monday, null, Triple("sleep", minutes(23) to minutes(7), ScheduleRules.ALL_DAYS))
        val tuesday = monday.plusDays(1)
        val spans = PlannedLayer.forDay(tuesday, london, emptyList(), listOf(s))
        // Monday night's sleep (into Tuesday) and Tuesday night's.
        assertEquals(2, spans.size)
        assertEquals(listOf(monday, tuesday), spans.map { it.scheduleDay })
        assertEquals(MinuteSpan(0, minutes(7)), DayView.plannedBlock(spans[0], null, tuesday, london)!!.span)
    }

    @Test
    fun scheduleHistoryIsNeverRewritten() {
        val old = schedule(monday.minusDays(30), monday.minusDays(1), Triple("work", minutes(8) to minutes(16), ScheduleRules.ALL_DAYS))
        val current = schedule(monday, null, Triple("work", minutes(10) to minutes(18), ScheduleRules.ALL_DAYS))
        assertEquals(old, PlannedLayer.activeOn(listOf(old, current), monday.minusDays(1)))
        assertEquals(current, PlannedLayer.activeOn(listOf(old, current), monday))
        assertNull(PlannedLayer.activeOn(listOf(old, current), monday.minusDays(31)))
    }

    @Test
    fun blocksOnDstDaysKeepTheirWallClockTimes() {
        // 29 March 2026: UK clocks go forward at 01:00. Sleep 23:00-07:00 is 7 real hours.
        val springEve = LocalDate.of(2026, 3, 28)
        val s = schedule(springEve, null, Triple("sleep", minutes(23) to minutes(7), ScheduleRules.ALL_DAYS))
        val span = PlannedLayer.instances(s, springEve, london).single()
        assertEquals(7 * 60L, java.time.Duration.between(span.start, span.end).toMinutes())
        assertEquals("07:00", TimeRules.wallTime(span.end, london).text)
    }

    @Test
    fun scheduleRulesFindOverlapsAcrossMidnightAndDays() {
        // Friday-night sleep into Saturday overlaps a Saturday 06:00 block.
        assertTrue(ScheduleRules.overlaps(minutes(23), minutes(7), ScheduleRules.WEEKDAYS, minutes(6), minutes(8), ScheduleRules.WEEKEND))
        // But not a Saturday 08:00 block.
        assertFalse(ScheduleRules.overlaps(minutes(23), minutes(7), ScheduleRules.WEEKDAYS, minutes(8), minutes(9), ScheduleRules.WEEKEND))
        assertEquals(24 * 60, ScheduleRules.lengthMinutes(0, 0))
    }
}
