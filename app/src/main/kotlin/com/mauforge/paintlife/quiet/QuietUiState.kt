package com.mauforge.paintlife.quiet

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class QuietPhase { Ready, Running, Paused, Completed }

@Immutable
data class QuietDay(val date: LocalDate, val completed: Boolean)

/** Quiet Time. Built by QuietViewModel from the timestamp-based timer service in Iteration 5. */
@Immutable
data class QuietUiState(
    val phase: QuietPhase,
    val remainingMillis: Long,
    /** The seven days of the current week, oldest first. */
    val week: List<QuietDay>,
    /** Completed five-minute sessions this month. Incomplete sessions never count. */
    val monthCompletedSessions: Int,
    val monthMinutes: Int,
    val totalMillis: Long = FIVE_MINUTES,
) {
    val progress: Float get() = if (phase == QuietPhase.Completed) 1f else 1f - remainingMillis.toFloat() / totalMillis

    companion object {
        const val FIVE_MINUTES = 5 * 60 * 1000L
    }
}
