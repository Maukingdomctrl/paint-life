package com.mauforge.paintlife.mytime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SegmentedPicker
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * What Android currently allows reminders to do, and how to change it. [askNotifications] asks
 * for the notification permission at the moment a reminder is first switched on (API 33+); a
 * refusal is fine, the reminder is kept and the screen says notifications are off.
 */
@Immutable
data class ReminderAccess(
    val notificationsOn: Boolean = true,
    /** Alarms can be exact ("Alarms & reminders" access). When false, reminders may be minutes late. */
    val exactOn: Boolean = true,
    val askNotifications: () -> Unit = {},
    val openNotificationSettings: () -> Unit = {},
    val openExactSettings: () -> Unit = {},
)

/** Re-checked whenever the screen comes back, e.g. from Android's settings. */
@Composable
fun rememberReminderAccess(): ReminderAccess {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(true) }
    var exactOn by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = ReminderNotifications.canNotify(context)
        exactOn = ReminderNotifications.canScheduleExact(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted && ReminderNotifications.canNotify(context)
    }
    return ReminderAccess(
        notificationsOn = notificationsOn,
        exactOn = exactOn,
        askNotifications = {
            val missing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (missing) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        openNotificationSettings = {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
        openExactSettings = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }
        },
    )
}

/**
 * Says plainly when reminders cannot be shown (notifications off) or may be late (no exact-alarm
 * access), each with the way to change it. Shows nothing when all is well.
 */
@Composable
fun ReminderAccessNotes(access: ReminderAccess, modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    when {
        !access.notificationsOn -> Column(modifier.testTag("reminder_notifications_off"), verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
            Text(text = stringResource(R.string.reminder_notifications_off), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            QuietButton(stringResource(R.string.reminder_open_notification_settings), access.openNotificationSettings, Modifier.fillMaxWidth())
        }
        !access.exactOn -> Column(modifier.testTag("reminder_inexact"), verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
            Text(text = stringResource(R.string.reminder_inexact), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            QuietButton(stringResource(R.string.reminder_allow_exact), access.openExactSettings, Modifier.fillMaxWidth())
        }
    }
}

/**
 * "Remind me": off, or 5, 10, 15 or 30 minutes before. Off until the user chooses otherwise.
 * [tooLate] says the chosen moment has already passed, so nothing will be shown.
 */
@Composable
fun ReminderPicker(
    minutes: Int?,
    onChange: (Int?) -> Unit,
    access: ReminderAccess,
    modifier: Modifier = Modifier,
    tooLate: Boolean = false,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(modifier.testTag("reminder_picker"), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = stringResource(R.string.reminder_remind_me), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        SegmentedPicker(
            options = listOf(0) + ReminderRules.LEADS,
            selected = minutes ?: 0,
            onSelect = { chosen ->
                if (chosen != 0) access.askNotifications()
                onChange(chosen.takeIf { it != 0 })
            },
            label = { if (it == 0) stringResource(R.string.reminder_off) else stringResource(R.string.reminder_minutes_short, it) },
        )
        if (minutes != null) {
            if (tooLate) {
                Text(
                    text = pluralStringResource(R.plurals.reminder_too_late, minutes, minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("reminder_too_late"),
                )
            }
            ReminderAccessNotes(access)
        }
    }
}
