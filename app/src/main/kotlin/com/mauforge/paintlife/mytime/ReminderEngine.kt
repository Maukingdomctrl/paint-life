package com.mauforge.paintlife.mytime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes reminders happen. There is one alarm, for the earliest reminder still to come; everything
 * is worked out again from what is stored ([ReminderPlanner]) whenever [sync] runs:
 * - when the alarm fires;
 * - when a plan, schedule, reminder or the bedtime changes (watched by [start]), including a
 *   backup import;
 * - when the app starts, the phone restarts, the time zone or clock changes, the app is updated,
 *   or exact-alarm access is granted (see [ReminderReceiver]).
 *
 * [sync] shows what came due since it last ran, then sets the alarm again. The "handled up to"
 * moment it keeps means nothing is shown twice, however often it runs. If the clock is set back,
 * that moment moves back with it, so a reminder whose time comes round again is shown again.
 *
 * Timing is exact when the user has given "Alarms & reminders" access (always, before Android 12).
 * Without it the alarm is inexact and may be minutes late; the reminder screens say so. No
 * internet, no wake lock of its own, no foreground service.
 */
@Singleton
class ReminderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PaintLifeDatabase,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val reminders = db.reminderDao()
    private val times = db.timeDao()
    private val mutex = Mutex()

    /** Watches everything reminders depend on, for as long as the process lives. */
    fun start() {
        scope.launch {
            val from = time.now().minus(Duration.ofDays(1))
            combine(
                reminders.observePlanned(from),
                times.observeSchedules(),
                reminders.observeAll(),
                preferences.bedtimeReminderMinute,
            ) { _, _, _, _ -> }
                .conflate()
                .collect { sync() }
        }
    }

    /** Shows the reminders that have come due, removes spent ones and sets the alarm for the next. */
    suspend fun sync() = mutex.withLock {
        val now = time.now()
        val zone = time.zone()
        val state = preferences.reminderState()
        val after = minOf(state.handledUpTo ?: now, now)
        val window = Duration.ofDays(ReminderRules.HORIZON_DAYS + 2)
        val from = after.minus(Duration.ofDays(2))
        val sources = ReminderSources(
            planned = times.overlapping(TimeEntryKind.PLANNED, from, now.plus(window), TimeRules.earliestStart(from)),
            schedules = times.schedules(),
            reminders = reminders.all(),
            bedtimeMinute = state.bedtimeMinute,
            bedtimeSetAt = state.bedtimeSetAt,
        )
        val (due, later) = ReminderPlanner.occurrences(sources, after, now, zone).partition { it.fireAt <= now }
        if (due.isNotEmpty()) show(due, now, zone)
        reminders.deleteFired(now)
        preferences.setRemindersHandledUpTo(now)
        setAlarm(later.firstOrNull()?.fireAt)
    }

    private suspend fun show(due: List<ReminderOccurrence>, now: Instant, zone: ZoneId) {
        val running = RunningNow(
            exerciseOpen = reminders.openExerciseSessions() > 0,
            quietOpen = reminders.openQuietSessions() > 0,
            currentCategoryId = times.current()?.categoryId,
        )
        val shown = due.mapNotNull { ReminderPlanner.present(it, now, running) }
        shown.forEachIndexed { index, reminder ->
            val name = reminder.occurrence.categoryId?.let { times.category(it)?.name }
            ReminderNotifications.show(context, reminder, name, now, zone, silent = index > 0)
        }
    }

    /**
     * Snooze: the reminder described by [intent] (the notification's own details) is shown again
     * in five minutes. Stored, so it survives a restart.
     */
    suspend fun snooze(intent: Intent) {
        val id = intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0)
        ReminderNotifications.cancel(context, id)
        val now = time.now()
        val zone = time.zone()
        fun instant(name: String) = intent.getLongExtra(name, ReminderNotifications.NO_TIME).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        reminders.insert(
            ReminderEntity(
                id = UUID.randomUUID().toString(),
                kind = ReminderKind.SNOOZE,
                title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE),
                categoryId = intent.getStringExtra(ReminderNotifications.EXTRA_CATEGORY),
                note = intent.getStringExtra(ReminderNotifications.EXTRA_NOTE),
                fireAt = now.plus(ReminderRules.SNOOZE),
                zoneId = zone,
                activityStartAt = instant(ReminderNotifications.EXTRA_ACTIVITY_START),
                activityEndAt = instant(ReminderNotifications.EXTRA_ACTIVITY_END),
                action = StartAction.entries.firstOrNull { it.name == intent.getStringExtra(ReminderNotifications.EXTRA_ACTION) } ?: StartAction.NONE,
                createdAt = now,
                createdZoneId = zone,
            ),
        )
        sync()
    }

    /** One alarm for [at] (none when null): exact when allowed, otherwise inexact. */
    private fun setAlarm(at: Instant?) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.cancel(pending)
        if (at == null) return
        val millis = at.toEpochMilli()
        if (ReminderNotifications.canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                return
            } catch (_: SecurityException) {
                // Access was withdrawn between the check and the call: fall back to inexact.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }
}
