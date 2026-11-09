package com.mauforge.paintlife.today

import androidx.compose.runtime.Immutable
import com.mauforge.paintlife.core.time.DayPart
import java.time.LocalDate

@Immutable
data class PriorityItem(val id: String, val title: String, val done: Boolean)

/** Today's exercise totals. */
@Immutable
data class ExerciseToday(val reps: Int, val sessions: Int)

/**
 * Today's quiet time. [fill] paints the swatch: full with a completed 5:00 session, otherwise in
 * proportion to the minutes (never full).
 */
@Immutable
data class QuietToday(val completed: Boolean, val minutes: Int, val fill: Float)

/** Minutes of one category recorded today, used to paint the Time swatch in category colours. */
@Immutable
data class CategoryMinutes(val categoryName: String, val colorKey: String, val minutes: Int)

/** A block of today on the "so far" strip. [colorKey] null = time passed with nothing recorded. */
@Immutable
data class TodayStripBlock(val startMinute: Int, val endMinute: Int, val colorKey: String?)

/** Quick actions are shown only once the feature they open is built. */
enum class QuickAction { StartExercise, QuietTime, LogTime }

/** Unfinished priorities from an earlier day the user may copy to today. */
@Immutable
data class CarryOverUi(
    val sourceDate: LocalDate,
    val sourceIsYesterday: Boolean,
    val items: List<PriorityItem>,
    /** How many more priorities today has room for (1-3). */
    val freePlaces: Int,
)

/**
 * Everything the Today screen shows. A null tracking field means that feature is not tracked
 * yet (it arrives in a later iteration); the screen says so rather than showing a zero.
 */
@Immutable
data class TodayUiState(
    val date: LocalDate,
    val dayPart: DayPart,
    val priorities: List<PriorityItem>,
    val carryOver: CarryOverUi? = null,
    /** Null until Exercise is built (Iteration 4). */
    val exercise: ExerciseToday? = null,
    /** Null until Quiet Time is built (Iteration 5). */
    val quiet: QuietToday? = null,
    /** Null until My Time is built (Iteration 6). */
    val timeByCategory: List<CategoryMinutes>? = null,
    /** Null until My Time is built (Iteration 6); the strip is hidden until then. */
    val strip: List<TodayStripBlock>? = null,
    val quickActions: List<QuickAction> = emptyList(),
    /** Length of today in minutes (1380/1440/1500 around DST changes). */
    val dayLengthMinutes: Int = 1440,
) {
    val timeLoggedMinutes: Int get() = timeByCategory.orEmpty().sumOf { it.minutes }
    val prioritiesDone: Int get() = priorities.count { it.done }
    val canAddPriority: Boolean get() = priorities.size < MAX_PRIORITIES

    companion object {
        const val MAX_PRIORITIES = TodayRepository.MAX_PRIORITIES_PER_DAY
    }
}

/** The add/edit sheet. [id] null = adding a new priority. */
@Immutable
data class PriorityEditorUi(
    val id: String?,
    val initialTitle: String,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val saving: Boolean,
)

/** A deleted priority that can still be put back. [token] identifies this one deletion. */
@Immutable
data class PendingUndo(val token: Long, val title: String)

/** One-off message about something that could not be done. */
@Immutable
data class TodayNotice(val token: Long, val kind: Kind) {
    enum class Kind { LimitReached, RestoreFailed }
}

/** The whole screen: content (null while loading) plus the sheets and messages over it. */
@Immutable
data class TodayScreenState(
    val content: TodayUiState? = null,
    val editor: PriorityEditorUi? = null,
    val carryOverPickerOpen: Boolean = false,
    val undo: PendingUndo? = null,
    val notice: TodayNotice? = null,
)
