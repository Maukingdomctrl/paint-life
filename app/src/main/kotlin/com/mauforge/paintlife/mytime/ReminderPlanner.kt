package com.mauforge.paintlife.mytime

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The fixed numbers of reminders. */
object ReminderRules {
    /** Minutes before a planned activity that a reminder can be set to. */
    val LEADS: List<Int> = listOf(5, 10, 15, 30)

    val SNOOZE: Duration = Duration.ofMinutes(5)

    /** A one-off, bedtime or snoozed reminder this late (the phone was off) is dropped, not shown. */
    val LATE_LIMIT: Duration = Duration.ofMinutes(30)

    /** How long a reminder without an activity end stays in the notification shade. */
    val SHOWN_FOR: Duration = Duration.ofHours(1)

    /** Days ahead that are planned for. Entries reach 7 days ahead, and a schedule repeats weekly. */
    const val HORIZON_DAYS = 8L

    const val TITLE_MAX = 60
    const val EXERCISE_ID = "exercise"

    fun isLead(minutes: Int?): Boolean = minutes == null || minutes in LEADS

    /** What "Start now" starts for a planned activity of [categoryId]. */
    fun actionFor(categoryId: String): StartAction = when (categoryId) {
        EXERCISE_ID -> StartAction.EXERCISE
        TimeRules.SLEEP_ID -> StartAction.SLEEP
        else -> StartAction.NOW_DOING
    }
}

/** Where a reminder comes from. */
enum class ReminderSource { PLANNED, ONE_OFF, SNOOZE, BEDTIME }

/** One moment a reminder is due. [key] names it: the same reminder always has the same key. */
data class ReminderOccurrence(
    val key: String,
    val source: ReminderSource,
    val fireAt: Instant,
    /** The user's title (one-off), else null: the reminder is named after [categoryId] or bedtime. */
    val title: String?,
    val categoryId: String?,
    val note: String?,
    val activityStartAt: Instant?,
    val activityEndAt: Instant?,
    val action: StartAction,
)

/** Everything reminders are worked out from. */
data class ReminderSources(
    /** Every planned entry that may matter (with or without a reminder: a day's own plan hides the schedule). */
    val planned: List<TimeEntryEntity>,
    val schedules: List<ScheduleWithBlocks>,
    val reminders: List<ReminderEntity>,
    /** The bedtime reminder's minute of the day; null = off. */
    val bedtimeMinute: Int?,
    /** When the bedtime reminder was last set. */
    val bedtimeSetAt: Instant?,
)

/** What is in progress when a reminder comes due. */
data class RunningNow(val exerciseOpen: Boolean, val quietOpen: Boolean, val currentCategoryId: String?)

/** A due reminder to show. [minutesUntilStart] is set while its activity has not started yet. */
data class ShownReminder(val occurrence: ReminderOccurrence, val minutesUntilStart: Int?)

/**
 * Pure reminder planning. Reminders exist only where the user set one; nothing here invents any.
 *
 * - A planned entry reminds at its start minus its lead time. Entries are instants, so they keep
 *   their moment when the time zone changes.
 * - A schedule block reminds on the days the Planned lane shows it ([PlannedLayer.scheduleFor]),
 *   at wall-clock time in the current zone: a time skipped by the clocks going forward moves to
 *   the shifted time, and one repeated by the clocks going back happens once (the first time).
 * - The bedtime reminder is a wall-clock time every day, treated the same way.
 * - A reminder set after its own moment has passed never fires (lead time longer than the time left).
 */
object ReminderPlanner {

    /** Occurrences after [after] and within the planning horizon of [now], earliest first. */
    fun occurrences(sources: ReminderSources, after: Instant, now: Instant, zone: ZoneId): List<ReminderOccurrence> {
        val result = mutableListOf<ReminderOccurrence>()
        val firstDay = minOf(after, now).atZone(zone).toLocalDate().minusDays(1)
        val lastDay = now.atZone(zone).toLocalDate().plusDays(ReminderRules.HORIZON_DAYS)

        sources.planned.forEach { entry ->
            val lead = entry.reminderMinutes ?: return@forEach
            val fireAt = entry.startAt.minus(Duration.ofMinutes(lead.toLong()))
            if (fireAt > entry.updatedAt) result += planned("entry:${entry.id}", fireAt, entry.categoryId, entry.note, entry.startAt, entry.endAt)
        }

        var day = firstDay
        while (day <= lastDay) {
            val schedule = PlannedLayer.activeOn(sources.schedules, day)
            if (schedule != null) {
                PlannedLayer.scheduleFor(day, zone, sources.planned, sources.schedules).forEach { span ->
                    val lead = span.reminderMinutes ?: return@forEach
                    val fireAt = span.start.minus(Duration.ofMinutes(lead.toLong()))
                    if (fireAt > schedule.schedule.updatedAt) result += planned(span.id, fireAt, span.categoryId, null, span.start, span.end)
                }
            }
            sources.bedtimeMinute?.let { minute ->
                val fireAt = day.atStartOfDay().plusMinutes(minute.toLong()).atZone(zone).toInstant()
                if (sources.bedtimeSetAt == null || fireAt > sources.bedtimeSetAt) {
                    result += ReminderOccurrence("bedtime:$day", ReminderSource.BEDTIME, fireAt, null, TimeRules.SLEEP_ID, null, null, null, StartAction.SLEEP)
                }
            }
            day = day.plusDays(1)
        }

        sources.reminders.forEach { r ->
            val snooze = r.kind == ReminderKind.SNOOZE
            result += ReminderOccurrence(
                key = (if (snooze) "snooze:" else "oneoff:") + r.id,
                source = if (snooze) ReminderSource.SNOOZE else ReminderSource.ONE_OFF,
                fireAt = r.fireAt,
                title = r.title,
                categoryId = r.categoryId,
                note = r.note,
                activityStartAt = r.activityStartAt,
                activityEndAt = r.activityEndAt,
                action = r.action,
            )
        }

        val horizon = now.plus(Duration.ofDays(ReminderRules.HORIZON_DAYS + 1))
        return result
            .filter { it.fireAt > after && (it.fireAt <= horizon || it.source == ReminderSource.ONE_OFF) }
            .distinctBy { it.key }
            .sortedWith(compareBy<ReminderOccurrence> { it.fireAt }.thenBy { it.key })
    }

    private fun planned(key: String, fireAt: Instant, categoryId: String, note: String?, start: Instant, end: Instant) =
        ReminderOccurrence(key, ReminderSource.PLANNED, fireAt, null, categoryId, note, start, end, ReminderRules.actionFor(categoryId))

    /**
     * Whether a due [occurrence] is shown at [now], and how. Null = dropped silently:
     * - what it would start is already in progress;
     * - a planned reminder whose activity has already started (it came too late to be "before");
     * - any other reminder that is half an hour late or more.
     */
    fun present(occurrence: ReminderOccurrence, now: Instant, running: RunningNow): ShownReminder? {
        if (alreadyDoing(occurrence, running)) return null
        val start = occurrence.activityStartAt
        val untilStart = start?.takeIf { it > now }?.let { minutesUp(Duration.between(now, it)) }
        return when (occurrence.source) {
            ReminderSource.PLANNED -> untilStart?.let { ShownReminder(occurrence, it) }
            ReminderSource.ONE_OFF, ReminderSource.SNOOZE, ReminderSource.BEDTIME ->
                if (Duration.between(occurrence.fireAt, now) >= ReminderRules.LATE_LIMIT) null else ShownReminder(occurrence, untilStart)
        }
    }

    private fun alreadyDoing(occurrence: ReminderOccurrence, running: RunningNow): Boolean = when (occurrence.action) {
        StartAction.NONE -> false
        StartAction.EXERCISE -> running.exerciseOpen
        StartAction.QUIET -> running.quietOpen
        StartAction.SLEEP -> running.currentCategoryId == TimeRules.SLEEP_ID
        StartAction.NOW_DOING -> occurrence.categoryId != null && running.currentCategoryId == occurrence.categoryId
    }

    /** Whole minutes, rounded up: 4 min 10 s reads "in 5 minutes". */
    private fun minutesUp(duration: Duration): Int = ((duration.seconds + 59) / 60).toInt().coerceAtLeast(1)

    /** True when a reminder [leadMinutes] before [start] can no longer fire at [now]. */
    fun leadPassed(start: Instant, leadMinutes: Int?, now: Instant): Boolean =
        leadMinutes != null && start.minus(Duration.ofMinutes(leadMinutes.toLong())) <= now
}
