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

/** "Log last night's sleep": the prefilled times. */
@Immutable
data class SleepCardUi(val startLabel: String, val endLabel: String)

/** The running "now doing" activity, shown with Stop. */
@Immutable
data class NowDoingToday(val label: String, val colorKey: String, val elapsedMinutes: Long)

/**
 * Sleep running since the Sleep button was tapped. [askWakeTime]: it is well past the usual wake
 * time, so Wake up was probably forgotten and the wake time is asked for.
 */
@Immutable
data class SleepingUi(val sinceLabel: String, val elapsedMinutes: Long, val askWakeTime: Boolean)

/** A Sleep or Wake up tap that can still be undone. [token] identifies this one tap. */
@Immutable
data class SleepUndoUi(val token: Long, val kind: Kind) {
    enum class Kind { Asleep, Awake, TooShort }
}

/** Minutes of one category recorded today, used to paint the Time swatch in category colours. */
@Immutable
data class CategoryMinutes(val categoryName: String, val colorKey: String, val minutes: Int)

/** A block of today on the "so far" strip. [colorKey] null = time passed with nothing recorded. */
@Immutable
data class TodayStripBlock(val startMinute: Int, val endMinute: Int, val colorKey: String?)

/** The shortcuts under the priorities. */
enum class QuickAction { StartExercise, QuietTime, LogTime, Sleep }

/** Unfinished priorities from an earlier day the user may copy to today. */
@Immutable
data class CarryOverUi(
    val sourceDate: LocalDate,
    val sourceIsYesterday: Boolean,
    val items: List<PriorityItem>,
    /** How many more priorities today has room for (1-3). */
    val freePlaces: Int,
)

/** Everything the Today screen shows. */
@Immutable
data class TodayUiState(
    val date: LocalDate,
    val dayPart: DayPart,
    val priorities: List<PriorityItem>,
    val carryOver: CarryOverUi? = null,
    val exercise: ExerciseToday = ExerciseToday(reps = 0, sessions = 0),
    val quiet: QuietToday = QuietToday(completed = false, minutes = 0, fill = 0f),
    val timeByCategory: List<CategoryMinutes> = emptyList(),
    /** Null until today's time has been read; the strip is hidden until then. */
    val strip: List<TodayStripBlock>? = null,
    val quickActions: List<QuickAction> = emptyList(),
    /** Set each morning until last night's sleep is logged (or the card is dismissed). */
    val sleepCard: SleepCardUi? = null,
    val nowDoing: NowDoingToday? = null,
    /** Set while sleep runs: the card with Wake up replaces the running-activity and sleep cards. */
    val sleeping: SleepingUi? = null,
    /** Length of today in minutes (1380/1440/1500 around DST changes). */
    val dayLengthMinutes: Int = 1440,
) {
    val timeLoggedMinutes: Int get() = timeByCategory.sumOf { it.minutes }
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
    enum class Kind { LimitReached, RestoreFailed, SleepLogged, SleepUndoFailed }
}

/** The whole screen: content (null while loading) plus the sheets and messages over it. */
@Immutable
data class TodayScreenState(
    val content: TodayUiState? = null,
    val editor: PriorityEditorUi? = null,
    val carryOverPickerOpen: Boolean = false,
    val undo: PendingUndo? = null,
    val sleepUndo: SleepUndoUi? = null,
    val notice: TodayNotice? = null,
    /** Set when My Time should open (it has a sheet waiting); [token] identifies the request. */
    val openMyTime: Long? = null,
)
