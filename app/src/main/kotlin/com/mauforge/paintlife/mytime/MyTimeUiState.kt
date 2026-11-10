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
    val note: String? = null,
    /** A block of the approved schedule rather than a planned entry of this day's own. */
    val fromSchedule: Boolean = false,
)

/** The running "now doing" activity. */
@Immutable
data class NowDoingUi(
    val categoryName: String,
    val colorKey: String,
    val note: String?,
    val startLabel: String,
    val elapsedMinutes: Long,
)

/** A one-tap activity: a recent category + note pair, or a plain category. */
@Immutable
data class QuickActivity(val categoryId: String, val categoryName: String, val colorKey: String, val note: String?)

/** My Time for one day. */
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
    /** Days recorded so far (0-6) during the observation week; null once it is over. */
    val observedDays: Int?,
    /** True once the observation week is over and insights are available. */
    val insightsReady: Boolean,
    /** The Actual / Planned / Both picker shows (not on future days, which are planned only). */
    val layersAvailable: Boolean = true,
    /** A day after today: plans only. */
    val isFuture: Boolean = false,
    /** The next day can be shown (up to a week ahead, for planning). */
    val canGoForward: Boolean = false,
    /** Set when the day is shown in a zone other than the current one, or holds entries from another zone. */
    val shownZone: String? = null,
    val nowDoing: NowDoingUi? = null,
    /** Recent activities first, then the categories: one tap starts "now doing". */
    val quickActivities: List<QuickActivity> = emptyList(),
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
    val note: String = "",
    val recent: List<QuickActivity> = emptyList(),
    /** 1 or 2 when the start/end time happens twice that day (clocks going back). */
    val startOccurrence: Int? = null,
    val endOccurrence: Int? = null,
    /** Confirming when a long-running "now doing" activity really ended. */
    val confirmingRunning: Boolean = false,
    val layersAvailable: Boolean = true,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = selectedCategoryId != null && durationMinutes in 1..MAX_MINUTES && !saving

    companion object {
        const val MAX_MINUTES = 24 * 60
    }
}

/** What saving does to one overlapping neighbour. */
enum class OverlapOutcome { Trimmed, Split, Removed }

@Immutable
data class OverlapRow(
    val categoryName: String,
    val range: String,
    val outcome: OverlapOutcome,
    /** What the neighbour keeps: one range when trimmed, two when split, null when removed. */
    val kept: List<String>,
)

/** Saving would overlap other entries: offer to fit it in (trim) or replace them. */
@Immutable
data class OverlapUiState(
    /** The entries are planned ones (the planned layer never overlaps itself either). */
    val planned: Boolean = false,
    val newCategoryName: String,
    val newRange: String,
    val neighbours: List<OverlapRow>,
)

/** A change that can still be undone. */
@Immutable
data class MyTimeUndo(val token: Long, val kind: Kind) {
    enum class Kind { Deleted, Fitted, Copied }
}

@Immutable
data class MyTimeNotice(val token: Long, val kind: Kind) {
    enum class Kind { Invalid, Missing, UndoFailed }
}

/** The whole screen: content plus the sheets and messages over it. */
@Immutable
data class MyTimeScreenState(
    val content: MyTimeUiState? = null,
    val editor: LogActivityUiState? = null,
    val overlap: OverlapUiState? = null,
    val undo: MyTimeUndo? = null,
    val notice: MyTimeNotice? = null,
    /** A schedule block was tapped: offer "Change for this day". */
    val schedulePrompt: SchedulePromptUi? = null,
    /** The categories sheet is open with these. */
    val categories: List<CategoryOption>? = null,
)

/** A tapped block of the approved schedule. */
@Immutable
data class SchedulePromptUi(val categoryName: String, val range: String)
