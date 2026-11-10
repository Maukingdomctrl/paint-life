package com.mauforge.paintlife.mytime

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How saving an entry treats neighbours of the same kind that it overlaps. */
enum class Resolution {
    /** Save only if nothing overlaps; otherwise report the overlaps. */
    None,

    /** Make room: trim partial neighbours, split one that contains the entry, replace covered ones. */
    Fit,

    /** Remove every overlapping neighbour. */
    Replace,
}

/** What saving an entry would do to one overlapping neighbour. */
sealed interface OverlapChange {
    val original: TimeEntryEntity

    /** The neighbour keeps [start, end). */
    data class Trim(override val original: TimeEntryEntity, val start: Instant, val end: Instant) : OverlapChange

    /** The new entry sits inside the neighbour: it keeps [original.start, firstEnd) and [secondStart, original.end). */
    data class Split(override val original: TimeEntryEntity, val firstEnd: Instant, val secondStart: Instant) : OverlapChange

    /** The neighbour is covered completely (or Replace was chosen) and is removed. */
    data class Remove(override val original: TimeEntryEntity) : OverlapChange
}

/** A wall-clock label. [occurrence] is 1 or 2 when the time happens twice (clocks going back), else null. */
data class WallTime(val text: String, val occurrence: Int?)

/**
 * The pure rules of My Time. Entries are whole minutes, 1 minute to 24 hours long. Everything
 * that measures time uses real elapsed instants, so DST days have 23 or 25 hours.
 */
object TimeRules {
    val MAX_DURATION: Duration = Duration.ofHours(24)
    const val STEP_MINUTES = 5L

    /** A running activity this old asks for its end time to be confirmed. */
    val LONG_RUNNING: Duration = Duration.ofHours(16)

    /** A day with at least this much recorded counts toward the observation week. */
    const val OBSERVATION_DAY_MINUTES = 60L
    const val OBSERVATION_DAYS = 7

    /** Recent activities offered in the log sheet, looked up over this window. */
    const val RECENT_LIMIT = 6
    val RECENT_WINDOW: Duration = Duration.ofDays(30)

    val DEFAULT_BEDTIME: LocalTime = LocalTime.of(23, 0)
    val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 0)
    const val SLEEP_ID = "sleep"

    /** Sleep is offered on Today until this local time. */
    val MORNING_ENDS: LocalTime = LocalTime.of(14, 0)

    fun toMinute(instant: Instant): Instant = instant.truncatedTo(ChronoUnit.MINUTES)

    /** The nearest 5-minute mark ("now" in the pickers). */
    fun roundToStep(instant: Instant): Instant {
        val minutes = Math.floorDiv(instant.epochSecond + 150, 300) * 5
        return Instant.ofEpochSecond(minutes * 60)
    }

    /**
     * The latest moment an actual entry may end: now on the pickers' 5-minute grid, and never
     * earlier than this minute (a running activity stops to the minute). Time that has not
     * happened yet can only be planned.
     */
    fun latestActualEnd(now: Instant): Instant = maxOf(roundToStep(now), toMinute(now))

    /**
     * Last night's [sleep] cut off at [now]: it cannot end after the moment it is logged. Null
     * when it would not have started yet.
     */
    fun sleepSoFar(sleep: Pair<Instant, Instant>, now: Instant): Pair<Instant, Instant>? {
        val end = minOf(sleep.second, latestActualEnd(now))
        return (sleep.first to end).takeIf { isValidSpan(sleep.first, end) }
    }

    fun isValidSpan(start: Instant, end: Instant): Boolean {
        val length = Duration.between(start, end)
        return length >= Duration.ofMinutes(1) && length <= MAX_DURATION
    }

    /** Earliest start of an entry that can overlap something starting at [from]. */
    fun earliestStart(from: Instant): Instant = from.minus(MAX_DURATION)

    /**
     * What saving [start, end) does to each overlapping [neighbours] entry (same kind, the edited
     * entry itself left out). Pieces are always at least a minute, as everything is whole minutes.
     */
    fun planOverlaps(start: Instant, end: Instant, neighbours: List<TimeEntryEntity>, resolution: Resolution): List<OverlapChange> =
        neighbours.filter { it.startAt < end && it.endAt > start }.map { n ->
            when {
                resolution == Resolution.Replace -> OverlapChange.Remove(n)
                n.startAt >= start && n.endAt <= end -> OverlapChange.Remove(n)
                n.startAt < start && n.endAt > end -> OverlapChange.Split(n, firstEnd = start, secondStart = end)
                n.startAt < start -> OverlapChange.Trim(n, n.startAt, start)
                else -> OverlapChange.Trim(n, end, n.endAt)
            }
        }

    /** Minutes of [start, end) on each local day of [zone] (split at local midnight). */
    fun minutesByDay(start: Instant, end: Instant, zone: ZoneId): Map<LocalDate, Long> {
        val result = LinkedHashMap<LocalDate, Long>()
        var date = start.atZone(zone).toLocalDate()
        var cursor = start
        while (cursor < end) {
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val pieceEnd = minOf(end, dayEnd)
            result[date] = (result[date] ?: 0L) + Duration.between(cursor, pieceEnd).toMinutes()
            cursor = pieceEnd
            date = date.plusDays(1)
        }
        return result
    }

    /**
     * Counts each real minute of [start, end) into [buckets] by the local wall-clock time it shows
     * in [zone]: bucket = minute of the day ÷ [bucketMinutes]. On a spring-forward day the skipped
     * hour gets nothing; on a fall-back day the repeated hour is counted twice.
     */
    fun addWallMinutes(start: Instant, end: Instant, zone: ZoneId, bucketMinutes: Int, buckets: IntArray) {
        val rules = zone.rules
        var cursor = start.epochSecond / 60
        val last = end.epochSecond / 60
        while (cursor < last) {
            val at = Instant.ofEpochSecond(cursor * 60)
            val offsetMinutes = rules.getOffset(at).totalSeconds / 60
            val nextChange = rules.nextTransition(at)?.instant?.epochSecond?.div(60) ?: Long.MAX_VALUE
            val segmentEnd = minOf(last, nextChange)
            while (cursor < segmentEnd) {
                val minuteOfDay = Math.floorMod(cursor + offsetMinutes, MINUTES_PER_DAY.toLong()).toInt()
                buckets[minuteOfDay / bucketMinutes]++
                cursor++
            }
        }
    }

    const val MINUTES_PER_DAY = 24 * 60

    /** Days with at least an hour of actual time recorded, capped at the observation length. */
    fun observedDays(spans: List<EntrySpan>): Int {
        val perDay = HashMap<LocalDate, Long>()
        spans.forEach { span ->
            minutesByDay(span.startAt, span.endAt, span.zoneId).forEach { (date, minutes) -> perDay[date] = (perDay[date] ?: 0L) + minutes }
        }
        return perDay.values.count { it >= OBSERVATION_DAY_MINUTES }.coerceAtMost(OBSERVATION_DAYS)
    }

    /**
     * Last night's sleep for the morning of [date]: the median bedtime and wake time of
     * [recentSleeps] (23:00 and 07:00 without any), ending on [date] in [zone].
     */
    fun sleepPrefill(recentSleeps: List<TimeEntryEntity>, date: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
        // Bedtimes are measured from noon, so 23:30 and 00:30 sit next to each other.
        val bed = recentSleeps.map { minutesFromNoon(it.startAt.atZone(it.zoneId).toLocalTime()) }.median()
            ?: minutesFromNoon(DEFAULT_BEDTIME)
        val wake = recentSleeps.map { it.endAt.atZone(it.endZoneId).toLocalTime().toSecondOfDay() / 60L }.median()
            ?: (DEFAULT_WAKE.toSecondOfDay() / 60L)
        val bedDate = date.minusDays(1)
        val start = bedDate.atTime(LocalTime.NOON).plusMinutes(bed).atZone(zone).toInstant()
        var end = date.atStartOfDay().plusMinutes(wake).atZone(zone).toInstant()
        if (!isValidSpan(start, end)) end = start.plus(Duration.ofHours(8))
        return toMinute(start) to toMinute(end)
    }

    private fun minutesFromNoon(time: LocalTime): Long = Math.floorMod(time.toSecondOfDay() / 60L - 12 * 60L, 24 * 60L)

    private fun List<Long>.median(): Long? = if (isEmpty()) null else sorted()[size / 2]

    /** "HH:mm" in [zone], marking a time that happens twice on a day the clocks go back. */
    fun wallTime(instant: Instant, zone: ZoneId): WallTime {
        val local = instant.atZone(zone)
        val text = "%02d:%02d".format(java.util.Locale.ROOT, local.hour, local.minute)
        val offsets = zone.rules.getValidOffsets(local.toLocalDateTime())
        if (offsets.size < 2) return WallTime(text, null)
        // The earlier occurrence has the larger offset (before the clocks went back).
        val first = offsets.maxBy { it.totalSeconds }
        return WallTime(text, if (local.offset == first) 1 else 2)
    }
}
