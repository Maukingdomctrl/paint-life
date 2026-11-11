package com.mauforge.paintlife.mytime

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One category's minutes on a day. */
data class CategoryTotal(val categoryId: String, val categoryName: String, val colorKey: String, val minutes: Long)

/** A stretch of a day: [colorKey] null means time that passed with nothing recorded. */
data class DayStretch(val startMinute: Int, val endMinute: Int, val colorKey: String?)

/**
 * Pure day building for My Time and Today. Entries are stored once and split at local midnight
 * here, so an entry crossing midnight gives each day its share.
 *
 * Which zone a day is shown in: today uses the current zone. A past day uses the zone of its
 * latest entry that started that day (in that entry's own zone), or the current zone without
 * one; entries recorded in another zone are converted, and the screen says so.
 */
object DayView {

    /**
     * The instants to read for [date]: every zone is within 14 hours of UTC, so this covers the
     * day in any zone an entry may have been recorded in.
     */
    fun readWindow(date: LocalDate): Pair<Instant, Instant> {
        val utc = ZoneId.of("UTC")
        return date.atStartOfDay(utc).toInstant().minus(Duration.ofHours(14)) to
            date.plusDays(1).atStartOfDay(utc).toInstant().plus(Duration.ofHours(14))
    }

    fun zoneFor(date: LocalDate, today: LocalDate, currentZone: ZoneId, entries: List<EntryWithCategory>): ZoneId =
        zoneOf(date, today, currentZone, entries.map { it.entry })

    /** [zoneFor] over plain entries. Today and future days use the current zone. */
    fun zoneOf(date: LocalDate, today: LocalDate, currentZone: ZoneId, entries: List<TimeEntryEntity>): ZoneId {
        if (date >= today) return currentZone
        return entries
            .filter { it.startAt.atZone(it.zoneId).toLocalDate() == date }
            .maxByOrNull { it.startAt }
            ?.zoneId
            ?: currentZone
    }

    /** A planned span clipped to [date], or null when it has no minute there. */
    fun plannedBlock(span: PlannedSpan, category: ActivityCategoryEntity?, date: LocalDate, zone: ZoneId): TimelineBlock? {
        val clipped = TimelineGeometry.clipToDay(span.start, span.end, date, zone) ?: return null
        return TimelineBlock(
            id = span.id,
            span = clipped,
            startLabel = TimeRules.wallTime(span.start, zone).text,
            endLabel = TimeRules.wallTime(span.end, zone).text,
            categoryName = category?.name.orEmpty(),
            colorKey = category?.colorKey ?: "stone",
            isDistraction = category?.isDistraction == true,
            continuesBefore = span.start < TimelineGeometry.startOfDay(date, zone),
            continuesAfter = span.end > TimelineGeometry.startOfDay(date.plusDays(1), zone),
            note = span.note,
            fromSchedule = span.fromSchedule,
            reminderMinutes = span.reminderMinutes,
        )
    }

    /** True when some entry shown on [date] was recorded in a zone other than [zone]. */
    fun hasOtherZones(entries: List<EntryWithCategory>, date: LocalDate, zone: ZoneId): Boolean =
        onDay(entries, date, zone).any { it.entry.zoneId != zone || it.entry.endZoneId != zone }

    /** The entries that have at least a minute on [date] in [zone]. */
    fun onDay(entries: List<EntryWithCategory>, date: LocalDate, zone: ZoneId): List<EntryWithCategory> =
        entries.filter { TimelineGeometry.clipToDay(it.entry.startAt, it.entry.endAt, date, zone) != null }

    fun blocks(entries: List<EntryWithCategory>, date: LocalDate, zone: ZoneId): List<TimelineBlock> {
        val dayStart = TimelineGeometry.startOfDay(date, zone)
        val dayEnd = TimelineGeometry.startOfDay(date.plusDays(1), zone)
        return entries.mapNotNull { item ->
            val e = item.entry
            val span = TimelineGeometry.clipToDay(e.startAt, e.endAt, date, zone) ?: return@mapNotNull null
            val start = TimeRules.wallTime(e.startAt, zone)
            val end = TimeRules.wallTime(e.endAt, zone)
            TimelineBlock(
                id = e.id,
                span = span,
                startLabel = start.text,
                endLabel = end.text,
                categoryName = item.categoryName,
                colorKey = item.colorKey,
                isDistraction = item.isDistraction,
                continuesBefore = e.startAt < dayStart,
                continuesAfter = e.endAt > dayEnd,
                note = e.note,
            )
        }
    }

    /** Minutes per category on [date] in [zone], largest first. */
    fun categoryTotals(entries: List<EntryWithCategory>, date: LocalDate, zone: ZoneId): List<CategoryTotal> =
        entries.mapNotNull { item ->
            val span = TimelineGeometry.clipToDay(item.entry.startAt, item.entry.endAt, date, zone) ?: return@mapNotNull null
            item to span.length.toLong()
        }
            .groupBy { it.first.entry.categoryId }
            .map { (id, parts) ->
                val first = parts.first().first
                CategoryTotal(id, first.categoryName, first.colorKey, parts.sumOf { it.second })
            }
            .sortedWith(compareByDescending<CategoryTotal> { it.minutes }.thenBy { it.categoryId })

    /** The day so far as recorded stretches and unrecorded gaps, from midnight to [untilMinute]. */
    fun stretches(entries: List<EntryWithCategory>, date: LocalDate, zone: ZoneId, untilMinute: Int): List<DayStretch> {
        val recorded = entries.mapNotNull { item ->
            TimelineGeometry.clipToDay(item.entry.startAt, item.entry.endAt, date, zone)?.let { span ->
                DayStretch(span.start, minOf(span.end, untilMinute), item.colorKey)
            }
        }.filter { it.endMinute > it.startMinute }
        val gaps = TimelineGeometry.unrecordedGaps(recorded.map { MinuteSpan(it.startMinute, it.endMinute) }, untilMinute)
            .map { DayStretch(it.start, it.end, null) }
        return (recorded + gaps).sortedBy { it.startMinute }
    }

    /** Minutes into [date] in [zone] at [now]: clamped to the day. */
    fun minuteOfDay(now: Instant, date: LocalDate, zone: ZoneId): Int {
        val start = TimelineGeometry.startOfDay(date, zone)
        val minutes = Duration.between(start, now).toMinutes().toInt()
        return minutes.coerceIn(0, TimelineGeometry.dayLengthMinutes(date, zone))
    }

    /** The instant [minute] minutes into [date] in [zone]. */
    fun instantAt(date: LocalDate, zone: ZoneId, minute: Int): Instant =
        TimelineGeometry.startOfDay(date, zone).plus(Duration.ofMinutes(minute.toLong()))
}
