package com.mauforge.paintlife.progress

import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseDayTotals
import com.mauforge.paintlife.exercise.ExerciseRangeStats
import com.mauforge.paintlife.exercise.ExerciseRecords
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.cats.CatRoster
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.DayView
import com.mauforge.paintlife.mytime.EntryWithCategory
import com.mauforge.paintlife.mytime.Insights
import com.mauforge.paintlife.mytime.ScheduleWithBlocks
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.mytime.TimelineGeometry
import com.mauforge.paintlife.quiet.QuietDayTotals
import com.mauforge.paintlife.quiet.QuietRules
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pure report building. Progress has no counting rules of its own: every number comes from the
 * rule its own screen uses, so Progress never disagrees with Today, Exercise, Quiet or My Time.
 *
 * - Exercise: sessions count on the day they started ([ExerciseSessionEntity.startDate]).
 * - Quiet: [QuietRules.dayTotals] per day; minutes are summed, then rounded down once.
 * - Time: [Insights.report] for categories and planned vs actual; [DayView.stretches] for each
 *   day's strip and unrecorded time, as on Today (today counts only up to now).
 */
object ProgressRules {

    fun exercise(
        totals: ExerciseDayTotals,
        stats: ExerciseRangeStats,
        fastest: ExerciseSessionEntity?,
        records: ExerciseRecords,
        levels: List<ChallengeLevelEntity>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
    ): ExerciseReport {
        val mostReps = stats.mostReps.takeIf { it > 0 }
        val bestTimed = fastest?.timer?.activeDurationMillis
        return ExerciseReport(
            totalReps = totals.reps,
            sessions = totals.sessions,
            averageSessionMillis = if (stats.volumeSessions > 0) stats.volumeActiveMillis / stats.volumeSessions else null,
            mostReps = mostReps,
            mostRepsIsRecord = mostReps != null && mostReps == records.mostReps,
            bestTimedMillis = bestTimed,
            bestTimedAgainst = fastest?.challengeLevel?.let(CatRoster::forLevel),
            bestTimedIsRecord = bestTimed != null && bestTimed == records.fastestTimedMillis,
            unlockedCats = levels.filter { it.isUnlocked }.sortedBy { it.level }.mapNotNull { CatRoster.forLevel(it.level) },
            levelsBeaten = levels.count { it.beatenAt != null },
            // A cat counts as beaten on the local day of the win, in the zone it was won in.
            beatenInPeriod = levels.filter { level ->
                val at = level.beatenAt ?: return@filter false
                val date = at.atZone(level.beatenZoneId ?: zone).toLocalDate()
                date in from..to
            }.sortedBy { it.level }.mapNotNull { CatRoster.forLevel(it.level) },
        )
    }

    /**
     * Quiet totals from [days] ([QuietRules.dayTotals] of the period). A day counts toward
     * consistency once it is over, and today only once it has a full session, so an unfinished
     * day never reads as a miss. [weekStart] splits the month periods into calendar weeks.
     */
    fun quiet(
        days: Map<LocalDate, QuietDayTotals>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        weekStart: DayOfWeek,
        withWeeks: Boolean,
    ): QuietReport {
        val last = minOf(to, today)
        val dates = datesBetween(from, last)
        val fills = dates.map { date ->
            val totals = days[date] ?: QuietDayTotals.empty(date)
            QuietDayFill(date, totals.completed, totals.fill, totals.minutes)
        }
        val counted = fills.filter { it.date < today || it.completed }
        val inPeriod = days.values.filter { it.date in from..to }
        return QuietReport(
            completedSessions = inPeriod.sumOf { it.completedSessions },
            totalMinutes = QuietRules.wholeMinutes(inPeriod.sumOf { it.quietMillis }),
            days = fills,
            daysWithFull = counted.count { it.completed },
            countedDays = counted.size,
            weeks = if (withWeeks) weeks(counted, from, last, weekStart) else emptyList(),
        )
    }

    private fun weeks(counted: List<QuietDayFill>, from: LocalDate, last: LocalDate, weekStart: DayOfWeek): List<QuietWeek> {
        if (last < from) return emptyList()
        val result = ArrayList<QuietWeek>()
        var start = from
        while (start <= last) {
            val end = minOf(start.with(TemporalAdjusters.previousOrSame(weekStart)).plusDays(6), last)
            val inWeek = counted.filter { it.date in start..end }
            if (inWeek.isNotEmpty()) result += QuietWeek(start, end, inWeek.count { it.completed }, inWeek.size)
            start = end.plusDays(1)
        }
        return result
    }

    /**
     * Time over [from] to [to] (never past today). Each day is read in its own zone, as in My
     * Time; entries are split at local midnight and DST days have 23 or 25 hours.
     */
    fun time(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        now: Instant,
        currentZone: ZoneId,
        actual: List<EntryWithCategory>,
        planned: List<TimeEntryEntity>,
        schedules: List<ScheduleWithBlocks>,
        categories: List<ActivityCategoryEntity>,
    ): TimeReport {
        val last = minOf(to, today)
        val plain = actual.map { it.entry }
        val insights = Insights.report(from, last, today, currentZone, plain, planned, schedules, categories)
        val byId = categories.associateBy { it.id }
        var unrecorded = 0
        val days = datesBetween(from, last).map { date ->
            val zone = DayView.zoneOf(date, today, currentZone, plain)
            val length = TimelineGeometry.dayLengthMinutes(date, zone)
            val until = if (date == today) DayView.minuteOfDay(now, date, zone) else length
            val stretches = DayView.stretches(DayView.onDay(actual, date, zone), date, zone, until)
            unrecorded += stretches.filter { it.colorKey == null }.sumOf { it.endMinute - it.startMinute }
            DayDistribution(date, stretches.map { DistributionBlock(it.startMinute, it.endMinute, it.colorKey) }, length)
        }
        return TimeReport(
            categories = insights.categories.map { CategoryTotal(it.name, it.colorKey, it.minutes.toInt(), null, it.isDistraction) },
            unrecordedMinutes = unrecorded,
            days = days,
            planned = insights.planned.map {
                CategoryTotal(it.name, it.colorKey, it.actualMinutes.toInt(), it.plannedMinutes.toInt(), byId[it.categoryId]?.isDistraction == true)
            },
            plannedDays = insights.plannedDays,
        )
    }

    private fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
}
