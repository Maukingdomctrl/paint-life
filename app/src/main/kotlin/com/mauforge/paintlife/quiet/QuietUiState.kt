package com.mauforge.paintlife.quiet

import androidx.compose.runtime.Immutable
import java.time.LocalDate

enum class QuietPhase { Ready, Running, Paused, Completed }

/**
 * One day of the week strip. [fill] is how much of its swatch is painted: 1 with a completed
 * session, otherwise in proportion to its minutes (never full).
 */
@Immutable
data class QuietDay(
    val date: LocalDate,
    val completed: Boolean,
    val minutes: Int = 0,
    val fill: Float = if (completed) 1f else 0f,
)

/** Quiet Time, built by [QuietViewModel] from the timestamp-based timer. */
@Immutable
data class QuietUiState(
    val phase: QuietPhase,
    val remainingMillis: Long,
    /** The seven days of the current week, oldest first. */
    val week: List<QuietDay>,
    /** Completed five-minute sessions this month. Incomplete and ended-early sessions never count. */
    val monthCompletedSessions: Int,
    /** Minutes of quiet this month: completed and ended-early sessions. */
    val monthMinutes: Int,
    val totalMillis: Long = FIVE_MINUTES,
) {
    val progress: Float get() = if (phase == QuietPhase.Completed) 1f else 1f - remainingMillis.toFloat() / totalMillis

    companion object {
        const val FIVE_MINUTES = QuietRules.TARGET_MILLIS
    }
}

/** A cancelled session that can still be put back. [token] identifies this one cancellation. */
@Immutable
data class QuietUndo(val token: Long)

/** One-off message. */
@Immutable
data class QuietNotice(val token: Long, val kind: Kind, val endedEarlyMillis: Long = 0L) {
    enum class Kind { EndedEarly, NothingSaved, RestoreFailed }
}

/** The whole screen: content (null while loading) plus messages over it. */
@Immutable
data class QuietScreenState(
    val content: QuietUiState? = null,
    val undo: QuietUndo? = null,
    val notice: QuietNotice? = null,
    /** True once Paint Life has asked for the notification permission (asked at most once). */
    val notificationsAsked: Boolean = true,
)
