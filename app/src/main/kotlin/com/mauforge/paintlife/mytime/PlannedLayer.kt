package com.mauforge.paintlife.mytime

import androidx.room.Embedded
import androidx.room.Relation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A schedule with its blocks (read with a Room relation). */
data class ScheduleWithBlocks(
    @Embedded val schedule: DailyScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId") val blocks: List<ScheduleBlockEntity>,
)

/**
 * One planned activity on a day: either a planned entry of its own ([scheduleDay] null), or a
 * block of the approved schedule placed on [scheduleDay] (the day the block starts).
 */
data class PlannedSpan(
    val id: String,
    val categoryId: String,
    val note: String?,
    val start: Instant,
    val end: Instant,
    val scheduleDay: LocalDate? = null,
) {
    val fromSchedule: Boolean get() = scheduleDay != null
}

/**
 * Schedule blocks in local wall-clock minutes. A block whose end is earlier than its start
 * crosses midnight; end equal to start is a whole day. Days are a bit mask, bit 0 = Monday.
 */
object ScheduleRules {
    const val ALL_DAYS = 0x7F
    const val WEEKDAYS = 0x1F
    const val WEEKEND = 0x60
    private const val DAY = TimeRules.MINUTES_PER_DAY
    private const val WEEK = 7 * DAY

    fun lengthMinutes(startMinute: Int, endMinute: Int): Int = Math.floorMod(endMinute - startMinute, DAY).let { if (it == 0) DAY else it }

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun hasDay(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    /** True when the two blocks share a minute on some day of the week. */
    fun overlaps(aStart: Int, aEnd: Int, aMask: Int, bStart: Int, bEnd: Int, bMask: Int): Boolean {
        val aLength = lengthMinutes(aStart, aEnd)
        val bLength = lengthMinutes(bStart, bEnd)
        for (i in 0 until 7) {
            if (aMask and (1 shl i) == 0) continue
            for (j in 0 until 7) {
                if (bMask and (1 shl j) == 0) continue
                val a = i * DAY + aStart
                val b = j * DAY + bStart
                if (Math.floorMod(b - a, WEEK) < aLength || Math.floorMod(a - b, WEEK) < bLength) return true
            }
        }
        return false
    }
}

/**
 * The planned lane of a day. A day's own planned entries always show. A day without planned
 * entries of its own (none starting that day) shows the blocks of the schedule active that day,
 * so approving or replacing a schedule never rewrites history. Schedule blocks that would
 * overlap an own planned entry are left out.
 */
object PlannedLayer {

    /** The approved schedule active on [date]: the latest one whose range contains it. */
    fun activeOn(schedules: List<ScheduleWithBlocks>, date: LocalDate): ScheduleWithBlocks? = schedules
        .filter { (s, _) ->
            s.status != ScheduleStatus.PROPOSED && s.activeFrom != null && date >= s.activeFrom &&
                (s.activeUntil == null || date <= s.activeUntil)
        }
        .maxByOrNull { it.schedule.activeFrom!! }

    /** The blocks of [schedule] that start on [day], as instants in [zone] (wall-clock times). */
    fun instances(schedule: ScheduleWithBlocks, day: LocalDate, zone: ZoneId): List<PlannedSpan> =
        schedule.blocks.filter { ScheduleRules.hasDay(it.daysOfWeekMask, day.dayOfWeek) }.mapNotNull { block ->
            val midnight = day.atStartOfDay()
            val length = ScheduleRules.lengthMinutes(block.startMinuteOfDay, block.endMinuteOfDay).toLong()
            val start = midnight.plusMinutes(block.startMinuteOfDay.toLong()).atZone(zone).toInstant()
            val end = midnight.plusMinutes(block.startMinuteOfDay + length).atZone(zone).toInstant()
            if (!TimeRules.isValidSpan(start, end)) return@mapNotNull null
            PlannedSpan(id = "$SCHEDULE_PREFIX${block.id}:$day", categoryId = block.categoryId, note = null, start = start, end = end, scheduleDay = day)
        }.sortedBy { it.start }

    /** True when a planned entry of its own starts on [day] in [zone]. */
    fun hasOwnPlan(own: List<TimeEntryEntity>, day: LocalDate, zone: ZoneId): Boolean =
        own.any { it.startAt.atZone(zone).toLocalDate() == day }

    /**
     * Schedule blocks that [day] shows (none if it has its own plan), leaving out any that overlap
     * an own planned entry.
     */
    fun scheduleFor(day: LocalDate, zone: ZoneId, own: List<TimeEntryEntity>, schedules: List<ScheduleWithBlocks>): List<PlannedSpan> {
        if (hasOwnPlan(own, day, zone)) return emptyList()
        val schedule = activeOn(schedules, day) ?: return emptyList()
        return instances(schedule, day, zone).filter { span -> own.none { it.startAt < span.end && it.endAt > span.start } }
    }

    /**
     * Everything planned that touches [date] in [zone]: own planned entries plus the schedule
     * blocks of [date] and of the day before (a block may cross midnight).
     */
    fun forDay(date: LocalDate, zone: ZoneId, own: List<TimeEntryEntity>, schedules: List<ScheduleWithBlocks>): List<PlannedSpan> {
        val owned = own.map { PlannedSpan(it.id, it.categoryId, it.note, it.startAt, it.endAt) }
        val fromSchedule = listOf(date.minusDays(1), date).flatMap { scheduleFor(it, zone, own, schedules) }
        return (owned + fromSchedule)
            .filter { TimelineGeometry.clipToDay(it.start, it.end, date, zone) != null }
            .sortedBy { it.start }
    }

    const val SCHEDULE_PREFIX = "schedule:"
}
