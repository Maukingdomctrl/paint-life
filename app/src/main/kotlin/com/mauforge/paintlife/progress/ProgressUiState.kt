package com.mauforge.paintlife.progress

import androidx.compose.runtime.Immutable
import com.mauforge.paintlife.exercise.cats.CatProfile
import java.time.LocalDate
import java.time.YearMonth

enum class ProgressPeriod { Today, Last7Days, ThisMonth, LastMonth }

@Immutable
data class ExerciseReport(
    val totalReps: Int,
    val sessions: Int,
    val averageSessionMillis: Long?,
    /** Fastest finished timed challenge in the period, with the cat it was against. */
    val bestTimedMillis: Long?,
    val bestTimedAgainst: CatProfile?,
    /** Cats unlocked so far (all time), for the avatar row. */
    val unlockedCats: List<CatProfile>,
    val levelsBeaten: Int,
)

@Immutable
data class QuietReport(
    val completedSessions: Int,
    val totalMinutes: Int,
    /** Days in the period, oldest first, with whether a full session was completed. */
    val days: List<Pair<LocalDate, Boolean>>,
)

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
    val categories: List<CategoryTotal>,
    val unclassifiedMinutes: Int,
    val days: List<DayDistribution>,
) {
    val hasPlans: Boolean get() = categories.any { it.plannedMinutes != null }
    val isEmpty: Boolean get() = categories.all { it.actualMinutes == 0 }
}

/** Progress reports. Built by ProgressViewModel in Iteration 7 from real records only. */
@Immutable
data class ProgressUiState(
    val period: ProgressPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val exercise: ExerciseReport,
    val quiet: QuietReport,
    val time: TimeReport,
) {
    /** The month shown as a swatch calendar, for the month periods. */
    val calendarMonth: YearMonth? get() = when (period) {
        ProgressPeriod.ThisMonth, ProgressPeriod.LastMonth -> YearMonth.from(start)
        ProgressPeriod.Today, ProgressPeriod.Last7Days -> null
    }
}
