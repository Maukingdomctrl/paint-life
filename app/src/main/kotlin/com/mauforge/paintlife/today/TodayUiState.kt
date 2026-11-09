package com.mauforge.paintlife.today

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class Greeting { Morning, Afternoon, Evening }

@Immutable
data class PriorityItem(val id: String, val title: String, val done: Boolean)

/** Minutes of one category recorded today, used to paint the Time swatch in category colours. */
@Immutable
data class CategoryMinutes(val categoryName: String, val colorKey: String, val minutes: Int)

/** A block of today on the "so far" strip. [colorKey] null = time passed with nothing recorded. */
@Immutable
data class TodayStripBlock(val startMinute: Int, val endMinute: Int, val colorKey: String?)

/** Everything the Today screen shows. Built by TodayViewModel from real records (Iteration 3). */
@Immutable
data class TodayUiState(
    val date: LocalDate,
    val greeting: Greeting,
    val exerciseRepsToday: Int,
    val exerciseSessionsToday: Int,
    val quietCompletedToday: Boolean,
    val timeByCategory: List<CategoryMinutes>,
    val priorities: List<PriorityItem>,
    val strip: List<TodayStripBlock>,
    /** Length of today in minutes (1380/1440/1500 around DST changes). */
    val dayLengthMinutes: Int = 1440,
) {
    val timeLoggedMinutes: Int get() = timeByCategory.sumOf { it.minutes }
    val prioritiesDone: Int get() = priorities.count { it.done }
    val canAddPriority: Boolean get() = priorities.size < MAX_PRIORITIES

    companion object {
        const val MAX_PRIORITIES = 3
    }
}
