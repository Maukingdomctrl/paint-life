package com.mauforge.paintlife.today

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.LoadingState
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

@Composable
fun TodayScreen(
    onStartExercise: () -> Unit,
    onOpenQuiet: () -> Unit,
    onOpenMyTime: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    Box(modifier.fillMaxSize()) {
        val content = state.content
        if (content == null) {
            LoadingState(Modifier.fillMaxSize())
        } else {
            TodayContent(
                state = content,
                onSetCompleted = viewModel::setCompleted,
                onEditPriority = viewModel::openPriority,
                onAddPriority = viewModel::openNewPriority,
                onReviewCarryOver = viewModel::openCarryOver,
                onDismissCarryOver = viewModel::dismissCarryOver,
                // Only actions whose feature is built are offered (see TodayViewModel).
                onQuickAction = { action ->
                    when (action) {
                        QuickAction.StartExercise -> onStartExercise()
                        QuickAction.QuietTime -> onOpenQuiet()
                        QuickAction.LogTime -> viewModel.logTime()
                    }
                },
                onLogSleep = viewModel::logSleep,
                onAdjustSleep = viewModel::adjustSleep,
                onDismissSleep = viewModel::dismissSleep,
                onStopNow = viewModel::stopNow,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(PaintLifeTheme.spacing.md).testTag("today_snackbar"),
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

    state.editor?.let { editor ->
        PriorityEditorSheet(
            editor = editor,
            onSave = viewModel::save,
            onMove = { direction -> editor.id?.let { viewModel.move(it, direction) } },
            onDelete = { editor.id?.let(viewModel::delete) },
            onDismiss = viewModel::closeEditor,
        )
    }

    val offer = state.content?.carryOver
    if (state.carryOverPickerOpen && offer != null) {
        CarryOverSheet(
            offer = offer,
            onConfirm = viewModel::carryOver,
            onDismiss = viewModel::closeCarryOver,
        )
    }

    // Keyed by token: a new deletion replaces the previous offer. If the screen leaves before
    // the snackbar ends, the offer is shown again on return; it never undoes on its own.
    val undo = state.undo
    LaunchedEffect(undo?.token) {
        if (undo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.today_deleted, undo.title),
            actionLabel = resources.getString(R.string.action_undo),
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(undo.token) else viewModel.undoExpired(undo.token)
    }

    // My Time has a sheet waiting (log since last entry, or a sleep that needs adjusting).
    val openMyTime = state.openMyTime
    LaunchedEffect(openMyTime) {
        if (openMyTime == null) return@LaunchedEffect
        viewModel.myTimeOpened(openMyTime)
        onOpenMyTime()
    }

    val notice = state.notice
    LaunchedEffect(notice?.token) {
        if (notice == null) return@LaunchedEffect
        val message = when (notice.kind) {
            TodayNotice.Kind.LimitReached -> R.string.today_notice_limit
            TodayNotice.Kind.RestoreFailed -> R.string.today_notice_restore_failed
            TodayNotice.Kind.SleepLogged -> R.string.today_sleep_logged
        }
        // Cleared only once shown: clearing first would change this effect's key and cancel it.
        try {
            snackbarHostState.showSnackbar(resources.getString(message), duration = SnackbarDuration.Short)
        } finally {
            viewModel.noticeShown(notice.token)
        }
    }
}
