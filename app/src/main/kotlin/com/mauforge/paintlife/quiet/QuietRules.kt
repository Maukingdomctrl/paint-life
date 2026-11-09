package com.mauforge.paintlife.quiet

import com.mauforge.paintlife.core.database.SessionStatus
import java.time.LocalDate

/** Quiet time on one local day (sessions count on the day they started). */
data class QuietDayTotals(
    val date: LocalDate,
    /** Full 5:00 sessions. */
    val completedSessions: Int,
    /** Completed and ended-early time together. */
    val quietMillis: Long,
) {
    val completed: Boolean get() = completedSessions > 0
    val minutes: Int get() = QuietRules.wholeMinutes(quietMillis)

    /** How much of the day's swatch is painted: full only for a completed session. */
    val fill: Float get() = QuietRules.fill(completed, quietMillis)

    companion object {
        fun empty(date: LocalDate) = QuietDayTotals(date, 0, 0L)
    }
}

/**
 * The single source of what quiet time counts for. Everything that reports quiet time (Quiet,
 * Today, later Progress) uses these rules, so an incomplete session can never count anywhere.
 */
object QuietRules {
    const val TARGET_MILLIS: Long = 5 * 60 * 1000L

    /** A session paused this long is saved as ended early. */
    const val PAUSE_LIMIT_MILLIS: Long = 30 * 60 * 1000L

    /**
     * A day without a completed session never looks fully painted, however many minutes it had:
     * its swatch stops at this fraction.
     */
    const val PARTIAL_FILL_MAX: Float = 0.9f

    fun countsAsCompleted(status: SessionStatus, activeMillis: Long, targetMillis: Long): Boolean =
        status == SessionStatus.COMPLETED && activeMillis >= targetMillis

    /** Completed and ended-early sessions add their minutes; incomplete or open ones add nothing. */
    fun countsMinutes(status: SessionStatus): Boolean = status == SessionStatus.COMPLETED || status == SessionStatus.ENDED_EARLY

    fun fill(completed: Boolean, quietMillis: Long): Float = when {
        completed -> 1f
        quietMillis <= 0L -> 0f
        else -> (quietMillis.toFloat() / TARGET_MILLIS).coerceAtMost(PARTIAL_FILL_MAX)
    }

    /** Whole minutes, rounded down: 4:59 of quiet is 4 minutes. */
    fun wholeMinutes(millis: Long): Int = (millis.coerceAtLeast(0L) / 60_000L).toInt()

    /** Per-day totals for [sessions]; days with nothing counted are left out. */
    fun dayTotals(sessions: List<QuietSessionSummary>): Map<LocalDate, QuietDayTotals> =
        sessions.filter { countsMinutes(it.status) }.groupBy { it.startDate }.mapValues { (date, day) ->
            QuietDayTotals(
                date = date,
                completedSessions = day.count { countsAsCompleted(it.status, it.activeDurationMillis, it.targetDurationMillis) },
                quietMillis = day.sumOf { it.activeDurationMillis.coerceAtLeast(0L) },
            )
        }
}
