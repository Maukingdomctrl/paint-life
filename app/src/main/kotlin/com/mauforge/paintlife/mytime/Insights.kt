package com.mauforge.paintlife.mytime

import java.time.LocalDate
import java.time.ZoneId

/** The days insights cover: complete days ending yesterday. Stored by name. */
enum class InsightsPeriod(val days: Int) {
    Week(7),
    FourWeeks(28),
    ;

    /** The first and last day covered when today is [today]. */
    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = today.minusDays(days.toLong()) to today.minusDays(1)
}

/** One category's minutes over the period, with its current distraction flag. */
data class CategoryMinutes(
    val categoryId: String,
    val name: String,
    val colorKey: String,
    val isDistraction: Boolean,
    val minutes: Long,
)

/** Planned and actual minutes of one category, over the days that had a plan. */
data class PlanComparison(val categoryId: String, val name: String, val colorKey: String, val plannedMinutes: Long, val actualMinutes: Long)

/**
 * One wall-clock hour over the period. [topCategoryId] is the category that filled most of this
 * hour (at least 30 minutes) on the most days, seen on [topDays] of [days]; null when no category
 * filled it on any day. [minutesByCategory] is the whole mix, largest first.
 */
data class HourPattern(
    val hour: Int,
    val topCategoryId: String?,
    val topDays: Int,
    val days: Int,
    val minutesByCategory: List<Pair<String, Long>>,
)

data class InsightsReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    /** Minutes in the period (23- and 25-hour days counted as they were). */
    val totalMinutes: Long,
    val recordedMinutes: Long,
    val categories: List<CategoryMinutes>,
    val distractionMinutes: Long,
    /** Minutes in distraction categories per day, oldest first. */
    val distractionByDay: List<Pair<LocalDate, Long>>,
    /** Days that had a plan (their own planned entries or a schedule). */
    val plannedDays: Int,
    val planned: List<PlanComparison>,
    val hours: List<HourPattern>,
) {
    val unrecordedMinutes: Long get() = totalMinutes - recordedMinutes
}

/**
 * Pure insight totals. Each day is read in its own zone ([DayView.zoneOf]); entries are split at
 * local midnight, so each day gets its share, and DST days count 23 or 25 hours. Planned time
 * never counts as actual: [actual] and [planned] are separate inputs throughout.
 */
object Insights {

    private const val HOUR_FILLED_MINUTES = 30

    fun report(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        currentZone: ZoneId,
        actual: List<TimeEntryEntity>,
        planned: List<TimeEntryEntity>,
        schedules: List<ScheduleWithBlocks>,
        categories: List<ActivityCategoryEntity>,
    ): InsightsReport {
        val byId = categories.associateBy { it.id }
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        val perCategory = HashMap<String, Long>()
        val plannedTotals = HashMap<String, Long>()
        val actualOnPlanDays = HashMap<String, Long>()
        val distractionByDay = ArrayList<Pair<LocalDate, Long>>()
        val hourMinutes = HashMap<String, LongArray>()
        val hourTopCounts = Array(24) { HashMap<String, Int>() }
        var total = 0L
        var recorded = 0L
        var plannedDays = 0

        dates.forEach { date ->
            val zone = DayView.zoneOf(date, today, currentZone, actual)
            total += TimelineGeometry.dayLengthMinutes(date, zone)
            val dayActual = minutesByCategory(actual.map { Triple(it.categoryId, it.startAt, it.endAt) }, date, zone)
            dayActual.forEach { (id, minutes) -> perCategory[id] = (perCategory[id] ?: 0L) + minutes }
            recorded += dayActual.values.sum()
            distractionByDay += date to dayActual.filterKeys { byId[it]?.isDistraction == true }.values.sum()

            val dayPlan = PlannedLayer.forDay(date, zone, planned, schedules)
            if (dayPlan.isNotEmpty()) {
                plannedDays++
                minutesByCategory(dayPlan.map { Triple(it.categoryId, it.start, it.end) }, date, zone)
                    .forEach { (id, minutes) -> plannedTotals[id] = (plannedTotals[id] ?: 0L) + minutes }
                dayActual.forEach { (id, minutes) -> actualOnPlanDays[id] = (actualOnPlanDays[id] ?: 0L) + minutes }
            }

            // Hourly: minutes per wall-clock hour and category, and which category filled each hour.
            val dayStart = TimelineGeometry.startOfDay(date, zone)
            val dayEnd = TimelineGeometry.startOfDay(date.plusDays(1), zone)
            val dayHours = HashMap<String, IntArray>()
            actual.forEach { e ->
                val start = maxOf(e.startAt, dayStart)
                val end = minOf(e.endAt, dayEnd)
                if (end > start) TimeRules.addWallMinutes(start, end, zone, 60, dayHours.getOrPut(e.categoryId) { IntArray(24) })
            }
            dayHours.forEach { (id, hours) ->
                val sums = hourMinutes.getOrPut(id) { LongArray(24) }
                hours.forEachIndexed { h, m -> sums[h] += m.toLong() }
            }
            for (h in 0 until 24) {
                val best = dayHours.maxWithOrNull(compareBy<Map.Entry<String, IntArray>> { it.value[h] }.thenByDescending { it.key }) ?: continue
                if (best.value[h] >= HOUR_FILLED_MINUTES) hourTopCounts[h][best.key] = (hourTopCounts[h][best.key] ?: 0) + 1
            }
        }

        fun minutes(id: String, value: Long) = byId[id].let { c ->
            CategoryMinutes(id, c?.name ?: id, c?.colorKey ?: "stone", c?.isDistraction == true, value)
        }

        val hours = (0 until 24).map { h ->
            val top = hourTopCounts[h].entries.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }.thenBy { hourMinutes[it.key]?.get(h) ?: 0L }.thenByDescending { it.key },
            )
            HourPattern(
                hour = h,
                topCategoryId = top?.key,
                topDays = top?.value ?: 0,
                days = dates.size,
                minutesByCategory = hourMinutes.mapNotNull { (id, sums) -> sums[h].takeIf { it > 0 }?.let { id to it } }
                    .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }),
            )
        }
        val categoryList = perCategory.filterValues { it > 0 }.map { (id, value) -> minutes(id, value) }
            .sortedWith(compareByDescending<CategoryMinutes> { it.minutes }.thenBy { it.categoryId })
        val planIds = (plannedTotals.keys + actualOnPlanDays.keys).filter { (plannedTotals[it] ?: 0L) + (actualOnPlanDays[it] ?: 0L) > 0 }
        return InsightsReport(
            from = from,
            to = to,
            days = dates.size,
            totalMinutes = total,
            recordedMinutes = recorded,
            categories = categoryList,
            distractionMinutes = categoryList.filter { it.isDistraction }.sumOf { it.minutes },
            distractionByDay = distractionByDay,
            plannedDays = plannedDays,
            planned = planIds.map { id ->
                val c = byId[id]
                PlanComparison(id, c?.name ?: id, c?.colorKey ?: "stone", plannedTotals[id] ?: 0L, actualOnPlanDays[id] ?: 0L)
            }.sortedWith(compareByDescending<PlanComparison> { maxOf(it.plannedMinutes, it.actualMinutes) }.thenBy { it.categoryId }),
            hours = hours,
        )
    }

    /** Minutes per category of [spans] (category, start, end) that fall on [date] in [zone]. */
    private fun minutesByCategory(spans: List<Triple<String, java.time.Instant, java.time.Instant>>, date: LocalDate, zone: ZoneId): Map<String, Long> {
        val result = HashMap<String, Long>()
        spans.forEach { (id, start, end) ->
            val clipped = TimelineGeometry.clipToDay(start, end, date, zone) ?: return@forEach
            result[id] = (result[id] ?: 0L) + clipped.length
        }
        return result
    }
}
