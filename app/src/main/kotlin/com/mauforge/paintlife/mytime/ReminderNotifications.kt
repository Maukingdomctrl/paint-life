package com.mauforge.paintlife.mytime

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
import com.mauforge.paintlife.quiet.QuietNotifications
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Reminder notifications: one gentle channel of their own, at default importance with the system
 * notification sound, so the ringer mode and Do Not Disturb decide whether they are heard. They
 * never use a full-screen intent or the alarm stream.
 *
 * Each has "Start now" (when there is something to start) and "Snooze 5 min". Tapping it opens the
 * app. On the lock screen only the title and time show; a note stays private.
 */
object ReminderNotifications {
    const val CHANNEL = "reminders"

    const val ACTION_SNOOZE = "com.mauforge.paintlife.reminder.SNOOZE"

    /** MainActivity starts this [StartAction] (by name) when opened with it. */
    const val EXTRA_START = "com.mauforge.paintlife.extra.REMINDER_START"

    /** MainActivity opens this tab ([OPEN_TODAY] or [OPEN_MY_TIME]) when opened with it. */
    const val EXTRA_OPEN = "com.mauforge.paintlife.extra.REMINDER_OPEN"
    const val OPEN_TODAY = "today"
    const val OPEN_MY_TIME = "my_time"

    const val EXTRA_NOTIFICATION_ID = "com.mauforge.paintlife.extra.REMINDER_NOTIFICATION_ID"
    const val EXTRA_TITLE = "com.mauforge.paintlife.extra.REMINDER_TITLE"
    const val EXTRA_CATEGORY = "com.mauforge.paintlife.extra.REMINDER_CATEGORY"
    const val EXTRA_NOTE = "com.mauforge.paintlife.extra.REMINDER_NOTE"
    const val EXTRA_ACTIVITY_START = "com.mauforge.paintlife.extra.REMINDER_ACTIVITY_START"
    const val EXTRA_ACTIVITY_END = "com.mauforge.paintlife.extra.REMINDER_ACTIVITY_END"
    const val EXTRA_ACTION = "com.mauforge.paintlife.extra.REMINDER_ACTION"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.reminder_channel_description)
            // Default sound stays on; vibration follows the ringer mode. Do Not Disturb is never bypassed.
            enableVibration(true)
            setBypassDnd(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when notifications can be shown at all (permission granted, and not turned off). */
    fun canNotify(context: Context): Boolean = QuietNotifications.canNotify(context)

    /** True when alarms can be exact: always before Android 12, else only with "Alarms & reminders" access. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    fun notificationId(key: String): Int = key.hashCode()

    /**
     * Shows [shown]. [categoryName] names its activity; [silent] for all but the first of several
     * due together, so they make one sound. Does nothing when notifications are off.
     */
    fun show(context: Context, shown: ShownReminder, categoryName: String?, now: Instant, zone: ZoneId, silent: Boolean) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val o = shown.occurrence
        val id = notificationId(o.key)
        val name = categoryName ?: context.getString(R.string.app_name)
        val bedtime = o.title == null && o.activityStartAt == null && o.action == StartAction.SLEEP
        val title = when {
            o.title != null -> o.title
            bedtime -> context.getString(R.string.reminder_bedtime_title)
            shown.minutesUntilStart != null ->
                context.resources.getQuantityString(R.plurals.reminder_in_minutes, shown.minutesUntilStart, name, shown.minutesUntilStart)
            else -> name
        }
        val publicText = when {
            o.activityStartAt != null && o.activityEndAt != null ->
                "${TimeRules.wallTime(o.activityStartAt, zone).text}–${TimeRules.wallTime(o.activityEndAt, zone).text}"
            bedtime -> context.getString(R.string.reminder_bedtime_text)
            else -> context.getString(R.string.reminder_one_off_text, TimeRules.wallTime(o.fireAt, zone).text)
        }
        val text = if (o.note.isNullOrBlank()) publicText else "$publicText · ${o.note}"
        val shownFor = o.activityEndAt?.let { Duration.between(now, it) }?.takeIf { it > Duration.ofMinutes(1) } ?: ReminderRules.SHOWN_FOR

        fun base(body: String) = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        val builder = base(text)
            .setContentIntent(open(context, id, if (bedtime) OPEN_TODAY else OPEN_MY_TIME))
            .setAutoCancel(true)
            .setSilent(silent)
            .setTimeoutAfter(shownFor.toMillis())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(publicText).build())
        if (o.action != StartAction.NONE) {
            builder.addAction(R.drawable.ic_play, context.getString(R.string.action_start_now), startNow(context, id, o))
        }
        builder.addAction(0, context.getString(R.string.action_snooze), snooze(context, id, o))
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: nothing to show.
        }
    }

    fun cancel(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun activityIntent(context: Context, id: Int) = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_NOTIFICATION_ID, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun open(context: Context, id: Int, tab: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            id,
            activityIntent(context, id).setAction("open").putExtra(EXTRA_OPEN, tab),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Start now opens the app, which starts the thing: Android does not let a notification start it unseen. */
    private fun startNow(context: Context, id: Int, o: ReminderOccurrence): PendingIntent =
        PendingIntent.getActivity(
            context,
            id,
            activityIntent(context, id).setAction("start")
                .putExtra(EXTRA_START, o.action.name)
                .putExtra(EXTRA_CATEGORY, o.categoryId)
                .putExtra(EXTRA_NOTE, o.note),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun snooze(context: Context, id: Int, o: ReminderOccurrence): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE)
            .putExtra(EXTRA_NOTIFICATION_ID, id)
            .putExtra(EXTRA_TITLE, o.title)
            .putExtra(EXTRA_CATEGORY, o.categoryId)
            .putExtra(EXTRA_NOTE, o.note)
            .putExtra(EXTRA_ACTIVITY_START, o.activityStartAt?.toEpochMilli() ?: NO_TIME)
            .putExtra(EXTRA_ACTIVITY_END, o.activityEndAt?.toEpochMilli() ?: NO_TIME)
            .putExtra(EXTRA_ACTION, o.action.name)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    const val NO_TIME = -1L
}
