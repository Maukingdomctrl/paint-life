package com.mauforge.paintlife.quiet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.LoadingState
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat

@Composable
fun QuietScreen(
    modifier: Modifier = Modifier,
    viewModel: QuietViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    // Re-checked whenever the screen comes back, e.g. from the notification settings.
    var notificationsOff by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsOff = !QuietNotifications.canNotify(context)
        onPauseOrDispose { }
    }

    // Asked once, at the moment it is first needed. Whatever the answer, the session starts.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOff = !granted || !QuietNotifications.canNotify(context)
        viewModel.start()
    }
    val onStart = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && !state.notificationsAsked) {
            viewModel.notificationsAsked()
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.start()
        }
    }

    Box(modifier.fillMaxSize()) {
        val content = state.content
        if (content == null) {
            LoadingState(Modifier.fillMaxSize())
        } else {
            QuietContent(
                state = content,
                onStart = onStart,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onFinish = viewModel::finish,
                onCancel = viewModel::cancel,
                onDone = viewModel::done,
                notificationsOff = notificationsOff,
                onOpenNotificationSettings = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(PaintLifeTheme.spacing.md).testTag("quiet_snackbar"),
        ) { data ->
            val colors = PaintLifeTheme.colors
            Snackbar(
                snackbarData = data,
                containerColor = colors.raised,
                contentColor = colors.text,
                actionColor = colors.accent,
                shape = PaintLifeTheme.shapes.tile,
            )
        }
    }

    // Keyed by token: if the screen leaves before the snackbar ends, the offer is shown again on
    // return; it never undoes on its own.
    val undo = state.undo
    LaunchedEffect(undo?.token) {
        if (undo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.quiet_cancelled),
            actionLabel = resources.getString(R.string.action_undo),
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoCancel(undo.token) else viewModel.undoExpired(undo.token)
    }

    val notice = state.notice
    LaunchedEffect(notice?.token) {
        if (notice == null) return@LaunchedEffect
        val message = when (notice.kind) {
            QuietNotice.Kind.EndedEarly -> resources.getString(R.string.quiet_notice_ended_early, DurationFormat.clock(notice.endedEarlyMillis))
            QuietNotice.Kind.NothingSaved -> resources.getString(R.string.quiet_notice_nothing_saved)
            QuietNotice.Kind.RestoreFailed -> resources.getString(R.string.quiet_notice_restore_failed)
        }
        // Cleared only once shown: clearing first would change this effect's key and cancel it.
        try {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        } finally {
            viewModel.noticeShown(notice.token)
        }
    }
}
