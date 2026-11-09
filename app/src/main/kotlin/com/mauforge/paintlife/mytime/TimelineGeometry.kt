package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Minutes from the start of a local day: [start] inclusive, [end] exclusive. */
@Immutable
data class MinuteSpan(val start: Int, val end: Int) {
    init {
        require(end >= start) { "end ($end) before start ($start)" }
    }

    val length: Int get() = end - start
}

/** An hour line on the timeline: [minuteOffset] from the start of the day, labelled with the local [hour]. */
@Immutable
data class HourMark(val minuteOffset: Int, val hour: Int)

/**
 * Pure geometry for the 24-hour timeline. Days are measured in real elapsed minutes from local
 * midnight, so a DST day is 23 or 25 hours long and the hour labels follow the wall clock
 * (a spring-forward day skips 02:00; a fall-back day shows 01:00 twice).
 */
object TimelineGeometry {

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun dayLengthMinutes(date: LocalDate, zone: ZoneId): Int =
        Duration.between(startOfDay(date, zone), startOfDay(date.plusDays(1), zone)).toMinutes().toInt()

    /**
     * The part of [start, end) that falls on [date] in [zone], or null if none does. An entry
     * crossing midnight is split this way, so each day gets its own share.
     */
    fun clipToDay(start: Instant, end: Instant, date: LocalDate, zone: ZoneId): MinuteSpan? {
        val dayStart = startOfDay(date, zone)
        val dayEnd = startOfDay(date.plusDays(1), zone)
        val from = maxOf(start, dayStart)
        val to = minOf(end, dayEnd)
        if (!to.isAfter(from)) return null
        return MinuteSpan(
            start = Duration.between(dayStart, from).toMinutes().toInt(),
            end = Duration.between(dayStart, to).toMinutes().toInt(),
        )
    }

    /** One mark per elapsed hour from the start of the day, labelled with the local hour. */
    fun hourMarks(date: LocalDate, zone: ZoneId): List<HourMark> {
        val dayStart = startOfDay(date, zone)
        val hours = dayLengthMinutes(date, zone) / 60
        return (0 until hours).map { h ->
            val instant = dayStart.plusSeconds(h * 3600L)
            HourMark(minuteOffset = h * 60, hour = instant.atZone(zone).hour)
        }
    }

    /**
     * Stretches of [0, untilMinute) not covered by any span: time that passed with nothing
     * recorded. Spans may arrive in any order and may touch.
     */
    fun unrecordedGaps(spans: List<MinuteSpan>, untilMinute: Int): List<MinuteSpan> {
        val gaps = mutableListOf<MinuteSpan>()
        var cursor = 0
        spans.sortedBy { it.start }.forEach { span ->
            if (span.start > cursor) gaps += MinuteSpan(cursor, minOf(span.start, untilMinute))
            cursor = maxOf(cursor, span.end)
            if (cursor >= untilMinute) return gaps.filter { it.length > 0 }
        }
        if (cursor < untilMinute) gaps += MinuteSpan(cursor, untilMinute)
        return gaps.filter { it.length > 0 }
    }
}
