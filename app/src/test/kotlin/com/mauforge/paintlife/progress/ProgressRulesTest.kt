package com.mauforge.paintlife.progress

import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseDayTotals
import com.mauforge.paintlife.exercise.ExerciseRangeStats
import com.mauforge.paintlife.exercise.ExerciseRecords
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.ExerciseSessionType
import com.mauforge.paintlife.exercise.cats.CatRoster
import com.mauforge.paintlife.mytime.EntryWithCategory
import com.mauforge.paintlife.mytime.Insights
import com.mauforge.paintlife.mytime.PlanningFixtures
import com.mauforge.paintlife.mytime.PlanningFixtures.categories
import com.mauforge.paintlife.mytime.PlanningFixtures.london
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.quiet.QuietDayTotals
import com.mauforge.paintlife.quiet.QuietRules
import com.mauforge.paintlife.quiet.QuietSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ProgressRulesTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 10)

    // Periods

    @Test
    fun periodsNeverReachPastToday() {
        assertEquals(today to today, ProgressPeriod.Today.range(today))
        assertEquals(LocalDate.of(2026, 11, 4) to today, ProgressPeriod.Last7Days.range(today))
        assertEquals(LocalDate.of(2026, 11, 1) to today, ProgressPeriod.ThisMonth.range(today))
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), ProgressPeriod.LastMonth.range(today))
    }

    @Test
    fun monthBoundaries() {
        // The 1st: this month is one day; last week reaches into the previous month.
        val first = LocalDate.of(2026, 12, 1)
        assertEquals(first to first, ProgressPeriod.ThisMonth.range(first))
        assertEquals(LocalDate.of(2026, 11, 25) to first, ProgressPeriod.Last7Days.range(first))
        // The last day of a month, and January's last month is the previous December.
        assertEquals(LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31), ProgressPeriod.ThisMonth.range(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2025, 12, 1) to LocalDate.of(2025, 12, 31), ProgressPeriod.LastMonth.range(LocalDate.of(2026, 1, 1)))
        // A leap February.
        assertEquals(LocalDate.of(2028, 2, 1) to LocalDate.of(2028, 2, 29), ProgressPeriod.LastMonth.range(LocalDate.of(2028, 3, 31)))
    }

    // Quiet

    private fun quietDay(date: LocalDate, vararg sessions: Pair<SessionStatus, Long>): Pair<LocalDate, QuietDayTotals> =
        date to QuietRules.dayTotals(sessions.map { QuietSessionSummary(date, it.first, it.second, QuietRules.TARGET_MILLIS) }).getValue(date)

    private fun quiet(days: Map<LocalDate, QuietDayTotals>, period: ProgressPeriod, on: LocalDate = today): QuietReport {
        val (from, to) = period.range(on)
        val months = period == ProgressPeriod.ThisMonth || period == ProgressPeriod.LastMonth
        return ProgressRules.quiet(days.filterKeys { it in from..to }, from, to, on, DayOfWeek.MONDAY, months)
    }

    @Test
    fun todayCountsTowardConsistencyOnlyOnceItHasAFullSession() {
        val yesterday = quietDay(today.minusDays(1), SessionStatus.COMPLETED to 300_000L)
        val partToday = quietDay(today, SessionStatus.ENDED_EARLY to 120_000L)

        val morning = quiet(mapOf(yesterday, partToday), ProgressPeriod.Last7Days)
        assertEquals(1, morning.daysWithFull)
        assertEquals(6, morning.countedDays)
        assertEquals(7, morning.days.size)
        // A partial day is painted in proportion, never as done.
        assertEquals(0.4f, morning.days.last().fill, 0.001f)
        assertFalse(morning.days.last().completed)

        val doneToday = quietDay(today, SessionStatus.ENDED_EARLY to 120_000L, SessionStatus.COMPLETED to 300_000L)
        val evening = quiet(mapOf(yesterday, doneToday), ProgressPeriod.Last7Days)
        assertEquals(2, evening.daysWithFull)
        assertEquals(7, evening.countedDays)
        assertEquals(2, evening.completedSessions)
    }

    @Test
    fun minutesAreSummedThenRoundedDownOnceAsOnTheQuietScreen() {
        // Two 4:30 sessions are 9 minutes, not 4 + 4.
        val days = mapOf(
            quietDay(today.minusDays(2), SessionStatus.ENDED_EARLY to 270_000L),
            quietDay(today.minusDays(1), SessionStatus.ENDED_EARLY to 270_000L),
        )
        val report = quiet(days, ProgressPeriod.ThisMonth)
        assertEquals(9, report.totalMinutes)
        assertEquals(0, report.completedSessions)
        assertEquals(0, report.daysWithFull)
    }

    @Test
    fun monthPeriodsSplitIntoCalendarWeeksClippedToThePeriod() {
        // 1 November 2026 is a Sunday; weeks start on Monday.
        val days = mapOf(
            quietDay(LocalDate.of(2026, 11, 1), SessionStatus.COMPLETED to 300_000L),
            quietDay(LocalDate.of(2026, 11, 3), SessionStatus.COMPLETED to 300_000L),
            quietDay(LocalDate.of(2026, 11, 9), SessionStatus.COMPLETED to 300_000L),
        )
        val report = quiet(days, ProgressPeriod.ThisMonth)
        assertEquals(
            listOf(
                QuietWeek(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), 1, 1),
                QuietWeek(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), 1, 7),
                // This week so far: today (the 10th) has no full session yet, so only the 9th counts.
                QuietWeek(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), 1, 1),
            ),
            report.weeks,
        )
        assertEquals(3, report.daysWithFull)
        assertEquals(9, report.countedDays)
        // Weekly lines are for month periods only.
        assertEquals(emptyList<QuietWeek>(), quiet(days, ProgressPeriod.Last7Days).weeks)
    }

    @Test
    fun lastMonthCountsEveryDayOfIt() {
        val report = quiet(mapOf(quietDay(LocalDate.of(2026, 10, 31), SessionStatus.COMPLETED to 300_000L)), ProgressPeriod.LastMonth)
        assertEquals(31, report.countedDays)
        assertEquals(1, report.daysWithFull)
        assertEquals(31, report.days.size)
    }

    @Test
    fun anEmptyPeriodIsEmpty() {
        val report = quiet(emptyMap(), ProgressPeriod.Last7Days)
        assertTrue(report.isEmpty)
        assertEquals(0, report.daysWithFull)
        assertTrue(report.days.all { it.fill == 0f })
    }

    // Exercise

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun level(level: Int, beatenAt: LocalDateTime? = null, unlocked: Boolean = true, beatenZone: ZoneId = zone) = ChallengeLevelEntity(
        level = level,
        catKey = CatRoster.forLevel(level)!!.key,
        benchmarkMillis = 1_800_000,
        isUnlocked = unlocked,
        unlockedAt = null,
        unlockedZoneId = null,
        beatenAt = beatenAt?.atZone(beatenZone)?.toInstant(),
        beatenZoneId = beatenAt?.let { beatenZone },
    )

    private fun timed(level: Int, millis: Long) = ExerciseSessionEntity(
        id = "t$level$millis",
        type = ExerciseSessionType.TIMED_CHALLENGE,
        status = SessionStatus.COMPLETED,
        challengeLevel = level,
        startedAt = Instant.EPOCH,
        zoneId = zone,
        startDate = today,
        endedAt = Instant.EPOCH,
        endedZoneId = zone,
        repetitions = 1000,
        timer = TimerCheckpoint(millis, millis, null, null),
        notes = null,
        updatedAt = Instant.EPOCH,
        updatedZoneId = zone,
    )

    @Test
    fun theAverageUsesNormalSessionsAndRecordsAreMarkedOnlyWhenTheyEqualTheAllTimeBest() {
        val report = ProgressRules.exercise(
            totals = ExerciseDayTotals(reps = 1300, sessions = 3),
            stats = ExerciseRangeStats(volumeSessions = 2, volumeActiveMillis = 1_500_000, mostReps = 1000),
            fastest = timed(2, 1_700_000),
            records = ExerciseRecords(mostReps = 1000, fastestTimedMillis = 1_650_000),
            levels = listOf(level(1, today.minusDays(20).atTime(9, 0)), level(2), level(3, unlocked = false)),
            from = today.minusDays(6),
            to = today,
            zone = zone,
        )
        assertEquals(750_000L, report.averageSessionMillis)
        assertEquals(1000, report.mostReps)
        assertTrue(report.mostRepsIsRecord)
        assertEquals(1_700_000L, report.bestTimedMillis)
        assertEquals(CatRoster.forLevel(2), report.bestTimedAgainst)
        assertFalse(report.bestTimedIsRecord)
        assertEquals(listOf(CatRoster.forLevel(1), CatRoster.forLevel(2)), report.unlockedCats)
        assertEquals(1, report.levelsBeaten)
        // Beaten 20 days ago: not in the last 7 days.
        assertEquals(emptyList<Any>(), report.beatenInPeriod)
    }

    @Test
    fun aCatCountsAsBeatenOnTheLocalDayOfTheWinInItsOwnZone() {
        // 23:30 on 31 October in Kolkata: an October win there, although it is a few hours from November.
        val levels = listOf(level(1, LocalDate.of(2026, 10, 31).atTime(23, 30)), level(2))
        fun beaten(period: ProgressPeriod) = ProgressRules.exercise(
            ExerciseDayTotals(0, 0), ExerciseRangeStats(0, 0, 0), null, ExerciseRecords(null, null), levels,
            period.range(today).first, period.range(today).second, zone,
        ).beatenInPeriod
        assertEquals(listOf(CatRoster.forLevel(1)), beaten(ProgressPeriod.LastMonth))
        assertEquals(emptyList<Any>(), beaten(ProgressPeriod.ThisMonth))
    }

    @Test
    fun noSessionsMeansNoAverageAndNoBests() {
        val report = ProgressRules.exercise(
            ExerciseDayTotals(0, 0), ExerciseRangeStats(0, 0, 0), null, ExerciseRecords(null, null), listOf(level(1)), today, today, zone,
        )
        assertTrue(report.isEmpty)
        assertNull(report.averageSessionMillis)
        assertNull(report.mostReps)
        assertFalse(report.mostRepsIsRecord)
        assertNull(report.bestTimedMillis)
        assertFalse(report.bestTimedIsRecord)
    }

    // Time

    private fun withCategory(entry: TimeEntryEntity): EntryWithCategory {
        val c = categories.first { it.id == entry.categoryId }
        return EntryWithCategory(entry, c.name, c.colorKey, c.isDistraction)
    }

    private fun entry(category: String, start: LocalDateTime, end: LocalDateTime, kind: TimeEntryKind = TimeEntryKind.ACTUAL) =
        PlanningFixtures.entry(category, start, end, kind)

    private fun time(
        period: ProgressPeriod,
        actual: List<TimeEntryEntity>,
        planned: List<TimeEntryEntity> = emptyList(),
        on: LocalDate = today,
        now: LocalDateTime = on.atTime(18, 0),
    ): TimeReport {
        val (from, to) = period.range(on)
        return ProgressRules.time(from, to, on, now.atZone(london).toInstant(), london, actual.map(::withCategory), planned, emptyList(), categories)
    }

    private fun TimeReport.minutesOf(name: String) = categories.firstOrNull { it.name == name }?.actualMinutes ?: 0

    @Test
    fun anEntryCrossingMidnightAtTheMonthBoundaryIsSplitBetweenBothMonths() {
        val night = entry("sleep", LocalDateTime.of(2026, 10, 31, 22, 0), LocalDateTime.of(2026, 11, 1, 2, 0))
        assertEquals(120, time(ProgressPeriod.LastMonth, listOf(night)).minutesOf("Sleep"))
        assertEquals(120, time(ProgressPeriod.ThisMonth, listOf(night)).minutesOf("Sleep"))
    }

    @Test
    fun todayCountsOnlyUpToNow() {
        val report = time(ProgressPeriod.Today, listOf(entry("work", today.atTime(8, 0), today.atTime(9, 0))), now = today.atTime(10, 0))
        assertEquals(60, report.minutesOf("Work"))
        // 10 hours have passed; 9 of them have nothing recorded.
        assertEquals(9 * 60, report.unrecordedMinutes)
        assertEquals(listOf(DistributionBlock(0, 480, null), DistributionBlock(480, 540, "slate"), DistributionBlock(540, 600, null)), report.days.single().blocks)
    }

    @Test
    fun dstDaysHave23And25Hours() {
        // In London the clocks go forward on 29 March 2026 and back on 25 October 2026.
        val spring = time(ProgressPeriod.Last7Days, emptyList(), on = LocalDate.of(2026, 3, 31))
        assertEquals(1380, spring.days.first { it.date == LocalDate.of(2026, 3, 29) }.dayLengthMinutes)
        assertEquals(6 * 1440 - 60 + 18 * 60, spring.unrecordedMinutes)

        val autumn = time(ProgressPeriod.LastMonth, listOf(entry("sleep", LocalDateTime.of(2026, 10, 25, 0, 0), LocalDateTime.of(2026, 10, 25, 8, 0))))
        // Midnight to 08:00 on the day the clocks go back is 9 real hours.
        assertEquals(9 * 60, autumn.minutesOf("Sleep"))
        assertEquals(1500, autumn.days.first { it.date == LocalDate.of(2026, 10, 25) }.dayLengthMinutes)
        assertEquals(31 * 1440 + 60 - 9 * 60, autumn.unrecordedMinutes)
    }

    @Test
    fun pastDaysAgreeWithInsights() {
        val yesterday = today.minusDays(1)
        val actual = listOf(
            entry("sleep", yesterday.minusDays(1).atTime(23, 0), yesterday.atTime(7, 0)),
            entry("work", yesterday.atTime(9, 0), yesterday.atTime(17, 0)),
            entry("phone_internet", yesterday.atTime(20, 0), yesterday.atTime(21, 30)),
        )
        val planned = listOf(entry("work", yesterday.atTime(9, 0), yesterday.atTime(16, 0), TimeEntryKind.PLANNED))
        // Last month seen from 1 December is all of November, complete days only, like Insights.
        val on = LocalDate.of(2026, 12, 1)
        val report = time(ProgressPeriod.LastMonth, actual, planned, on = on)
        val insights = Insights.report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), on, london, actual, planned, emptyList(), categories)

        assertEquals(insights.categories.map { it.name to it.minutes.toInt() }, report.categories.map { it.name to it.actualMinutes })
        assertEquals(insights.unrecordedMinutes.toInt(), report.unrecordedMinutes)
        assertEquals(1, report.plannedDays)
        // Planned and actual cover only the day with a plan, every category of that day included.
        assertEquals(
            listOf(
                CategoryTotal("Work", "slate", 480, 420, false),
                CategoryTotal("Sleep", "night", 420, 0, false),
                CategoryTotal("Phone/internet", "amber", 90, 0, true),
            ),
            report.planned,
        )
    }

    @Test
    fun plannedTimeNeverCountsAsActual() {
        val planned = listOf(entry("work", today.atTime(9, 0), today.atTime(17, 0), TimeEntryKind.PLANNED))
        val report = time(ProgressPeriod.Today, emptyList(), planned)
        assertTrue(report.isEmpty)
        assertEquals(18 * 60, report.unrecordedMinutes)
        assertEquals(listOf(CategoryTotal("Work", "slate", 0, 480, false)), report.planned)
    }

    @Test
    fun anEmptyPeriodHasNothingButUnrecordedTime() {
        val report = time(ProgressPeriod.Last7Days, emptyList())
        assertTrue(report.isEmpty)
        assertFalse(report.hasPlans)
        assertEquals(6 * 1440 + 18 * 60, report.unrecordedMinutes)
        assertEquals(7, report.days.size)
    }
}
