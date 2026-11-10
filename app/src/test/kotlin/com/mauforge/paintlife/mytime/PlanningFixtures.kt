package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.core.database.SeedData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Plain entries, schedules and categories for the pure planning and insight tests. */
internal object PlanningFixtures {
    val london: ZoneId = ZoneId.of("Europe/London")

    val categories: List<ActivityCategoryEntity> = SeedData.categories.mapIndexed { i, c ->
        ActivityCategoryEntity(c.id, c.name, c.colorKey, c.isDistraction, i, isBuiltIn = true, isArchived = false)
    }

    private var counter = 0

    fun entry(
        category: String,
        start: LocalDateTime,
        end: LocalDateTime,
        kind: TimeEntryKind = TimeEntryKind.ACTUAL,
        zone: ZoneId = london,
    ): TimeEntryEntity = entryAt(category, start.atZone(zone).toInstant(), end.atZone(zone).toInstant(), kind, zone)

    fun entryAt(category: String, start: Instant, end: Instant, kind: TimeEntryKind = TimeEntryKind.ACTUAL, zone: ZoneId = london) = TimeEntryEntity(
        id = "e${counter++}",
        kind = kind,
        categoryId = category,
        startAt = start,
        endAt = end,
        zoneId = zone,
        endZoneId = zone,
        note = null,
        createdAt = start,
        createdZoneId = zone,
        updatedAt = start,
        updatedZoneId = zone,
    )

    fun schedule(
        activeFrom: LocalDate,
        activeUntil: LocalDate? = null,
        vararg blocks: Triple<String, Pair<Int, Int>, Int>,
        id: String = "s${counter++}",
    ): ScheduleWithBlocks {
        val at = activeFrom.atStartOfDay(london).toInstant()
        return ScheduleWithBlocks(
            DailyScheduleEntity(
                id = id,
                name = "Plan",
                status = if (activeUntil == null) ScheduleStatus.APPROVED else ScheduleStatus.ARCHIVED,
                createdAt = at,
                createdZoneId = london,
                approvedAt = at,
                approvedZoneId = london,
                updatedAt = at,
                updatedZoneId = london,
                activeFrom = activeFrom,
                activeUntil = activeUntil,
                basedOnFrom = null,
                basedOnTo = null,
            ),
            blocks.mapIndexed { i, (category, times, mask) ->
                ScheduleBlockEntity("$id-b$i", id, category, times.first, times.second, mask)
            },
        )
    }

    fun minutes(h: Int, m: Int = 0) = h * 60 + m
}
