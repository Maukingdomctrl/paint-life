package com.mauforge.paintlife.mytime

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * A block of a schedule being proposed or edited. [seenDays] of [groupDays] is the evidence for a
 * suggested block; both are null once the user has added or changed it.
 */
data class ProposalBlock(
    val id: String,
    val categoryId: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val seenDays: Int? = null,
    val groupDays: Int? = null,
)

sealed interface SuggestionResult {
    /** Fewer than [ScheduleSuggester.MIN_DAYS] recorded days in the window. */
    data class NotEnough(val recordedDays: Int) : SuggestionResult

    data class Ready(
        val basedOnFrom: LocalDate,
        val basedOnTo: LocalDate,
        val recordedDays: Int,
        /** Share of the recorded days' time that was not recorded, 0-100. */
        val unrecordedPercent: Int,
        /** True when weekdays and weekends got patterns of their own. */
        val split: Boolean,
        val weekdayDays: Int,
        val weekendDays: Int,
        val blocks: List<ProposalBlock>,
    ) : SuggestionResult
}

/**
 * "Suggest a schedule": plain counting over observed days, nothing more.
 *
 * 1. Days: the last [WINDOW_DAYS] complete days, keeping those with at least an hour of actual
 *    time. Fewer than [MIN_DAYS] gives [SuggestionResult.NotEnough].
 * 2. Each day (in its own zone) is cut into 48 half-hour wall-clock slots. A slot's category that
 *    day is the one covering most of it, if that is at least 15 minutes (a tie counts as nothing).
 * 3. Weekdays and weekends get separate patterns when each has at least 2 recorded days.
 * 4. Per group and slot, the category picked on the most days goes in only if it was picked on at
 *    least half of the group's recorded days and no other category was picked as often.
 *    Unrecorded slots stay in the count, so patchy or irregular data leaves time open.
 * 5. Neighbouring slots with the same category merge into blocks; a block at the end of the day
 *    joins one at the start (sleep crosses midnight) unless that would overlap another block.
 * Distraction categories are treated like any other time.
 */
object ScheduleSuggester {
    const val WINDOW_DAYS = 28
    const val MIN_DAYS = 7
    const val SLOT_MINUTES = 30
    const val SLOTS = TimeRules.MINUTES_PER_DAY / SLOT_MINUTES
    private const val SLOT_FILLED = 15
    private const val MIN_GROUP_DAYS = 2

    private class ObservedDay(val date: LocalDate, val slots: Array<String?>)

    private data class Run(val categoryId: String, val startSlot: Int, val endSlot: Int, val minDays: Int)

    fun suggest(today: LocalDate, currentZone: ZoneId, actual: List<TimeEntryEntity>, newId: () -> String): SuggestionResult {
        val from = today.minusDays(WINDOW_DAYS.toLong())
        val to = today.minusDays(1)
        var dayMinutes = 0L
        var recordedMinutes = 0L
        val observed = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.mapNotNull { date ->
            val zone = DayView.zoneOf(date, today, currentZone, actual)
            val dayStart = TimelineGeometry.startOfDay(date, zone)
            val dayEnd = TimelineGeometry.startOfDay(date.plusDays(1), zone)
            val perCategory = HashMap<String, IntArray>()
            var recorded = 0L
            actual.forEach { e ->
                val start = maxOf(e.startAt, dayStart)
                val end = minOf(e.endAt, dayEnd)
                if (end <= start) return@forEach
                recorded += java.time.Duration.between(start, end).toMinutes()
                TimeRules.addWallMinutes(start, end, zone, SLOT_MINUTES, perCategory.getOrPut(e.categoryId) { IntArray(SLOTS) })
            }
            if (recorded < TimeRules.OBSERVATION_DAY_MINUTES) return@mapNotNull null
            dayMinutes += TimelineGeometry.dayLengthMinutes(date, zone)
            recordedMinutes += recorded
            ObservedDay(date, Array(SLOTS) { slot -> slotCategory(perCategory, slot) })
        }.toList()
        if (observed.size < MIN_DAYS) return SuggestionResult.NotEnough(observed.size)

        val weekend = observed.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekdays = observed - weekend.toSet()
        val split = weekdays.size >= MIN_GROUP_DAYS && weekend.size >= MIN_GROUP_DAYS
        val groups = if (split) listOf(ScheduleRules.WEEKDAYS to weekdays, ScheduleRules.WEEKEND to weekend) else listOf(ScheduleRules.ALL_DAYS to observed)

        val runs = groups.map { (mask, days) -> Triple(mask, days.size, runs(days)) }
        val merged = runs.flatMap { (mask, n, r) -> blocks(r, mask, n, wrap = true, newId) }
        val blocks = if (anyOverlap(merged)) runs.flatMap { (mask, n, r) -> blocks(r, mask, n, wrap = false, newId) } else merged
        val unrecordedPercent = if (dayMinutes == 0L) 0 else Math.round((dayMinutes - recordedMinutes) * 100.0 / dayMinutes).toInt()
        return SuggestionResult.Ready(
            basedOnFrom = observed.first().date,
            basedOnTo = observed.last().date,
            recordedDays = observed.size,
            unrecordedPercent = unrecordedPercent.coerceIn(0, 100),
            split = split,
            weekdayDays = weekdays.size,
            weekendDays = weekend.size,
            blocks = blocks.sortedWith(compareBy<ProposalBlock> { it.daysMask != ScheduleRules.ALL_DAYS && it.daysMask != ScheduleRules.WEEKDAYS }.thenBy { it.startMinute }),
        )
    }

    /** The category covering most of [slot] (at least 15 minutes, no tie), or null. */
    private fun slotCategory(perCategory: Map<String, IntArray>, slot: Int): String? {
        val ranked = perCategory.entries.map { it.key to it.value[slot] }.sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        if (best.second < SLOT_FILLED) return null
        if (ranked.size > 1 && ranked[1].second == best.second) return null
        return best.first
    }

    /** The winning category of each slot (picked on at least half the days, unique), merged into runs. */
    private fun runs(days: List<ObservedDay>): List<Run> {
        val winners = Array<Pair<String, Int>?>(SLOTS) { slot ->
            val counts = days.mapNotNull { it.slots[slot] }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val top = counts.firstOrNull() ?: return@Array null
            val unique = counts.size == 1 || counts[1].value < top.value
            if (unique && top.value * 2 >= days.size) top.key to top.value else null
        }
        val runs = mutableListOf<Run>()
        var slot = 0
        while (slot < SLOTS) {
            val win = winners[slot]
            if (win == null) {
                slot++
                continue
            }
            var end = slot
            var minDays = win.second
            while (end < SLOTS && winners[end]?.first == win.first) {
                minDays = minOf(minDays, winners[end]!!.second)
                end++
            }
            runs += Run(win.first, slot, end, minDays)
            slot = end
        }
        return runs
    }

    private fun blocks(runs: List<Run>, mask: Int, groupDays: Int, wrap: Boolean, newId: () -> String): List<ProposalBlock> {
        var list = runs
        val first = runs.firstOrNull()
        val last = runs.lastOrNull()
        if (wrap && runs.size > 1 && first!!.startSlot == 0 && last!!.endSlot == SLOTS && first.categoryId == last.categoryId) {
            // Joined across midnight: from the evening run's start to the morning run's end.
            list = runs.drop(1).dropLast(1) + Run(first.categoryId, last.startSlot, first.endSlot, minOf(first.minDays, last.minDays))
        }
        return list.map { run ->
            ProposalBlock(
                id = newId(),
                categoryId = run.categoryId,
                startMinute = run.startSlot * SLOT_MINUTES,
                endMinute = (run.endSlot * SLOT_MINUTES) % TimeRules.MINUTES_PER_DAY,
                daysMask = mask,
                seenDays = run.minDays,
                groupDays = groupDays,
            )
        }
    }

    fun anyOverlap(blocks: List<ProposalBlock>): Boolean = blocks.indices.any { i ->
        (i + 1 until blocks.size).any { j -> overlap(blocks[i], blocks[j]) }
    }

    fun overlap(a: ProposalBlock, b: ProposalBlock): Boolean =
        ScheduleRules.overlaps(a.startMinute, a.endMinute, a.daysMask, b.startMinute, b.endMinute, b.daysMask)
}
