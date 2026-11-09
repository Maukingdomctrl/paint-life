package com.mauforge.paintlife.quiet

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.time.DurationFormat

/**
 * Quiet Time's two notification channels:
 * - "in progress": silent and ongoing, shown by the foreground service while a session is open.
 *   It counts down by itself (a chronometer) and has one action, Pause or Resume.
 * - "finished": the 5:00 alert. It uses the system notification sound and vibration, so the
 *   ringer mode (silent, vibrate) and Do Not Disturb decide whether it is heard. The user can
 *   change it in Android's settings.
 *
 * Tapping either opens Quiet Time.
 */
object QuietNotifications {
    const val CHANNEL_RUNNING = "quiet_running"
    const val CHANNEL_FINISHED = "quiet_finished"
    const val ONGOING_ID = 501
    const val FINISHED_ID = 502

    /** MainActivity opens the Quiet tab when started with this extra set. */
    const val EXTRA_OPEN_QUIET = "com.mauforge.paintlife.extra.OPEN_QUIET"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val running = NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.quiet_channel_running),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.quiet_channel_running_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        val finished = NotificationChannel(
            CHANNEL_FINISHED,
            context.getString(R.string.quiet_channel_finished),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.quiet_channel_finished_description)
            // Default sound stays on; vibration follows the ringer mode.
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(running, finished))
    }

    /** The ongoing notification for an open session with [remainingMillis] left. */
    fun ongoing(context: Context, remainingMillis: Long, paused: Boolean): Notification {
        val left = DurationFormat.countdown(remainingMillis)
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_nav_quiet)
            .setContentTitle(context.getString(R.string.title_quiet))
            .setContentIntent(openQuiet(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(!paused)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (paused) {
            builder
                .setContentText(context.getString(R.string.quiet_notification_paused, left))
                .setUsesChronometer(false)
                .addAction(R.drawable.ic_play, context.getString(R.string.action_resume), serviceAction(context, QuietTimerService.ACTION_RESUME))
        } else {
            // The chronometer counts down on its own; the service re-posts it when anything changes.
            builder
                .setContentText(context.getString(R.string.quiet_notification_running))
                .setWhen(System.currentTimeMillis() + remainingMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(R.drawable.ic_pause, context.getString(R.string.action_pause), serviceAction(context, QuietTimerService.ACTION_PAUSE))
        }
        return builder.build()
    }

    /** The 5:00 alert. Does nothing when notifications are off (the app shows completion itself). */
    fun showFinished(context: Context) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_nav_quiet)
            .setContentTitle(context.getString(R.string.quiet_notification_done_title))
            .setContentText(context.getString(R.string.quiet_notification_done_text))
            .setContentIntent(openQuiet(context))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(FINISHED_ID, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post: the app still shows completion.
        }
    }

    /** True when notifications can be shown (permission granted on API 33+, and not turned off). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openQuiet(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_QUIET, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun serviceAction(context: Context, action: String): PendingIntent {
        val intent = Intent(context, QuietTimerService::class.java).setAction(action)
        return PendingIntent.getService(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
