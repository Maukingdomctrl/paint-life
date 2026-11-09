package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class TimeLayer { Actual, Planned, Both }

/** One activity already clipped to the shown day. Labels are local wall-clock times ("09:30"). */
@Immutable
data class TimelineBlock(
    val id: String,
    val span: MinuteSpan,
    val startLabel: String,
    val endLabel: String,
    val categoryName: String,
    val colorKey: String,
    val isDistraction: Boolean,
    /** True when the entry started the day before or ends the day after. */
    val continuesBefore: Boolean = false,
    val continuesAfter: Boolean = false,
)

/** My Time for one day. Built by MyTimeViewModel in Iteration 6. */
@Immutable
data class MyTimeUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val layer: TimeLayer,
    val actual: List<TimelineBlock>,
    val planned: List<TimelineBlock>,
    val hourMarks: List<HourMark>,
    val dayLengthMinutes: Int,
    /** Minutes into the day right now, for today; null for past days. Gaps are hatched up to here. */
    val nowMinute: Int?,
    /** 1..7 during the observation week, null after it. */
    val observationDay: Int?,
    /** True once there are 7 days of records to summarise. */
    val insightsReady: Boolean,
)

@Immutable
data class CategoryOption(val id: String, val name: String, val colorKey: String, val isDistraction: Boolean)

/** The log/edit activity sheet. */
@Immutable
data class LogActivityUiState(
    val isEditing: Boolean,
    val layer: TimeLayer,
    val categories: List<CategoryOption>,
    val selectedCategoryId: String?,
    val startLabel: String,
    val endLabel: String,
    /** The end time is on the following day; shown so a crossing-midnight entry is obvious. */
    val endsNextDay: Boolean,
    val durationMinutes: Int,
) {
    val canSave: Boolean get() = selectedCategoryId != null && durationMinutes > 0
}

/** Saving would overlap a neighbouring actual entry: offer to trim or replace it. */
@Immutable
data class OverlapUiState(
    val newCategoryName: String,
    val newRange: String,
    val neighbourCategoryName: String,
    val neighbourRange: String,
    /** The neighbour's range after trimming; null when trimming would leave nothing. */
    val trimmedNeighbourRange: String?,
)
