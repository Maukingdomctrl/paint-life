package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.mytime.PlanningFixtures.categories
import com.mauforge.paintlife.mytime.PlanningFixtures.entry
import com.mauforge.paintlife.mytime.PlanningFixtures.london
import com.mauforge.paintlife.mytime.PlanningFixtures.minutes
import com.mauforge.paintlife.mytime.PlanningFixtures.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class InsightsTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 10)
    private val yesterday = today.minusDays(1)

    private fun report(
        actual: List<TimeEntryEntity>,
        planned: List<TimeEntryEntity> = emptyList(),
        schedules: List<ScheduleWithBlocks> = emptyList(),
        cats: List<ActivityCategoryEntity> = categories,
        from: LocalDate = yesterday,
        to: LocalDate = yesterday,
        on: LocalDate = today,
    ) = Insights.report(from, to, on, london, actual, planned, schedules, cats)

    private fun InsightsReport.minutesOf(id: String) = categories.firstOrNull { it.categoryId == id }?.minutes ?: 0L

    @Test
    fun plannedTimeNeverCountsAsActual() {
        val actual = listOf(entry("work", yesterday.atTime(9, 0), yesterday.atTime(11, 0)))
        val planned = listOf(entry("work", yesterday.atTime(9, 0), yesterday.atTime(17, 0), TimeEntryKind.PLANNED))
        val withPlan = report(actual, planned)
        val withoutPlan = report(actual)
        assertEquals(120L, withPlan.minutesOf("work"))
        assertEquals(withoutPlan.recordedMinutes, withPlan.recordedMinutes)
        assertEquals(withoutPlan.unrecordedMinutes, withPlan.unrecordedMinutes)
        assertEquals(24 * 60L - 120, withPlan.unrecordedMinutes)
        // Planned only shows in planned vs actual.
        assertEquals(PlanComparison("work", "Work", "slate", 480, 120), withPlan.planned.single())
        assertEquals(1, withPlan.plannedDays)
    }

    @Test
    fun plannedVsActualCountsOnlyDaysWithAPlan() {
        val dayBefore = yesterday.minusDays(1)
        val actual = listOf(
            entry("work", dayBefore.atTime(9, 0), dayBefore.atTime(17, 0)),
            entry("work", yesterday.atTime(9, 0), yesterday.atTime(12, 0)),
        )
        val planned = listOf(entry("work", yesterday.atTime(9, 0), yesterday.atTime(13, 0), TimeEntryKind.PLANNED))
        val r = report(actual, planned, from = dayBefore)
        assertEquals(1, r.plannedDays)
        assertEquals(PlanComparison("work", "Work", "slate", 240, 180), r.planned.single())
        assertEquals(11 * 60L, r.minutesOf("work"))
    }

    @Test
    fun anApprovedScheduleCountsAsTheDaysPlan() {
        val s = schedule(yesterday, null, Triple("exercise", minutes(18) to minutes(19), ScheduleRules.ALL_DAYS))
        val r = report(listOf(entry("exercise", yesterday.atTime(18, 0), yesterday.atTime(18, 30))), schedules = listOf(s))
        assertEquals(PlanComparison("exercise", "Exercise", "forest", 60, 30), r.planned.single())
    }

    @Test
    fun anEntryCrossingMidnightGivesEachDayItsShare() {
        val dayBefore = yesterday.minusDays(1)
        val sleep = entry("sleep", dayBefore.atTime(22, 0), yesterday.atTime(7, 0))
        val r = report(listOf(sleep), from = yesterday)
        assertEquals(7 * 60L, r.minutesOf("sleep"))
        val both = report(listOf(sleep), from = dayBefore)
        assertEquals(9 * 60L, both.minutesOf("sleep"))
        assertEquals(48 * 60L, both.totalMinutes)
    }

    @Test
    fun aSpringForwardDayHas23HoursAndAFallBackDay25() {
        val spring = LocalDate.of(2026, 3, 29)
        val springReport = report(emptyList(), from = spring, to = spring, on = spring.plusDays(1))
        assertEquals(23 * 60L, springReport.totalMinutes)
        assertEquals(23 * 60L, springReport.unrecordedMinutes)

        val autumn = LocalDate.of(2026, 10, 25)
        // Sleep from 23:00 on the 24th to 07:00 on the 25th: 9 real hours, 8 of them on the 25th.
        val sleep = entry("sleep", autumn.minusDays(1).atTime(23, 0), autumn.atTime(7, 0))
        val autumnReport = report(listOf(sleep), from = autumn, to = autumn, on = autumn.plusDays(1))
        assertEquals(25 * 60L, autumnReport.totalMinutes)
        assertEquals(8 * 60L, autumnReport.minutesOf("sleep"))
        assertEquals(17 * 60L, autumnReport.unrecordedMinutes)
        // 01:00 happens twice, so that hour holds two hours of sleep.
        assertEquals(120L, autumnReport.hours[1].minutesByCategory.single().second)
    }

    @Test
    fun changingTheDistractionFlagChangesTheTotals() {
        val actual = listOf(
            entry("phone_internet", yesterday.atTime(20, 0), yesterday.atTime(21, 0)),
            entry("learning", yesterday.atTime(21, 0), yesterday.atTime(21, 30)),
        )
        assertEquals(60L, report(actual).distractionMinutes)
        val flagged = categories.map {
            when (it.id) {
                "phone_internet" -> it.copy(isDistraction = false)
                "learning" -> it.copy(isDistraction = true)
                else -> it
            }
        }
        val after = report(actual, cats = flagged)
        assertEquals(30L, after.distractionMinutes)
        assertEquals(listOf(yesterday to 30L), after.distractionByDay)
    }

    @Test
    fun hourlyPatternsNameTheUsualCategoryAndHowOften() {
        val days = (1..7).map { today.minusDays(it.toLong()) }
        val actual = days.mapIndexed { i, d ->
            entry(if (i < 5) "work" else "family", d.atTime(9, 0), d.atTime(10, 0))
        } + entry("learning", days[0].atTime(10, 0), days[0].atTime(10, 20))
        val r = report(actual, from = today.minusDays(7))
        val nine = r.hours[9]
        assertEquals("work", nine.topCategoryId)
        assertEquals(5, nine.topDays)
        assertEquals(7, nine.days)
        // 20 minutes on one day doesn't fill the hour.
        assertNull(r.hours[10].topCategoryId)
        assertEquals(listOf("learning" to 20L), r.hours[10].minutesByCategory)
    }

    @Test
    fun theRangeIsCompleteDaysEndingYesterday() {
        assertEquals(today.minusDays(7) to yesterday, InsightsPeriod.Week.range(today))
        assertEquals(today.minusDays(28) to yesterday, InsightsPeriod.FourWeeks.range(today))
    }
}
