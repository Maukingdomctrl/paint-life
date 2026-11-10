package com.mauforge.paintlife.progress

import androidx.compose.runtime.Immutable
import com.mauforge.paintlife.exercise.cats.CatProfile
import java.time.LocalDate
import java.time.YearMonth

/** Stored by name (the remembered choice); never rename. */
enum class ProgressPeriod {
    Today,
    Last7Days,
    ThisMonth,
    LastMonth,
    ;

    /**
     * The first and last day covered when today is [today]. Periods never reach past today:
     * "this month" runs from the 1st to today, and "last 7 days" is today and the 6 days before.
     */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        Today -> today to today
        Last7Days -> today.minusDays(6) to today
        ThisMonth -> today.withDayOfMonth(1) to today
        LastMonth -> YearMonth.from(today).minusMonths(1).let { it.atDay(1) to it.atEndOfMonth() }
    }

    companion object {
        val Default = Last7Days
    }
}

@Immutable
data class ExerciseReport(
    /** Reps and sessions with at least one rep, the open session included (as on Today). */
    val totalReps: Int,
    val sessions: Int,
    /** Average active time of finished normal sessions; null without one. */
    val averageSessionMillis: Long?,
    /** Most reps in one finished session in the period; null without one. */
    val mostReps: Int?,
    /** [mostReps] equals the all-time record. */
    val mostRepsIsRecord: Boolean,
    /** Fastest completed 1,000 in the period, with the cat it was against. */
    val bestTimedMillis: Long?,
    val bestTimedAgainst: CatProfile?,
    /** [bestTimedMillis] equals the all-time fastest 1,000. */
    val bestTimedIsRecord: Boolean,
    /** Cats unlocked so far (all time), in level order. */
    val unlockedCats: List<CatProfile>,
    /** Cats beaten so far (all time). */
    val levelsBeaten: Int,
    /** Cats first beaten during the period. */
    val beatenInPeriod: List<CatProfile>,
) {
    val isEmpty: Boolean get() = sessions == 0
}

/** One day's quiet swatch: [fill] follows [com.mauforge.paintlife.quiet.QuietRules.fill]. */
@Immutable
data class QuietDayFill(val date: LocalDate, val completed: Boolean, val fill: Float, val minutes: Int)

/** One calendar week of a month period (clipped to the period): days with a full session of [countedDays]. */
@Immutable
data class QuietWeek(val start: LocalDate, val end: LocalDate, val daysWithFull: Int, val countedDays: Int)

@Immutable
data class QuietReport(
    val completedSessions: Int,
    val totalMinutes: Int,
    /** Every day of the period up to today, oldest first. */
    val days: List<QuietDayFill>,
    /** Days with a full session, of [countedDays]: past days, plus today once it has one. */
    val daysWithFull: Int,
    val countedDays: Int,
    /** Calendar weeks, for the month periods only. */
    val weeks: List<QuietWeek>,
) {
    val isEmpty: Boolean get() = completedSessions == 0 && totalMinutes == 0
}

@Immutable
data class CategoryTotal(
    val name: String,
    val colorKey: String,
    val actualMinutes: Int,
    /** Null when nothing was planned for this category. */
    val plannedMinutes: Int?,
    val isDistraction: Boolean,
)

@Immutable
data class DayDistribution(val date: LocalDate, val blocks: List<DistributionBlock>, val dayLengthMinutes: Int = 1440)

/** [colorKey] null = unrecorded. */
@Immutable
data class DistributionBlock(val startMinute: Int, val endMinute: Int, val colorKey: String?)

@Immutable
data class TimeReport(
    /** Actual minutes per category, largest first. */
    val categories: List<CategoryTotal>,
    /** Minutes with nothing recorded: whole past days, and today up to now. */
    val unrecordedMinutes: Int,
    val days: List<DayDistribution>,
    /** Per category: planned minutes and actual minutes, over [plannedDays] only. */
    val planned: List<CategoryTotal> = emptyList(),
    val plannedDays: Int = 0,
) {
    val hasPlans: Boolean get() = planned.isNotEmpty()
    val isEmpty: Boolean get() = categories.all { it.actualMinutes == 0 }
}

@Immutable
sealed interface TimeSection {
    /** The observation week is not over: [recordedDays] of 7. Only multi-day periods wait for it. */
    data class Locked(val recordedDays: Int) : TimeSection

    data class Ready(val report: TimeReport) : TimeSection
}

/** Progress reports, built from real records only. */
@Immutable
data class ProgressUiState(
    val period: ProgressPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val exercise: ExerciseReport,
    val quiet: QuietReport,
    val time: TimeSection,
) {
    /** The month shown as a swatch calendar, for the month periods. */
    val calendarMonth: YearMonth? get() = when (period) {
        ProgressPeriod.ThisMonth, ProgressPeriod.LastMonth -> YearMonth.from(start)
        ProgressPeriod.Today, ProgressPeriod.Last7Days -> null
    }
}
