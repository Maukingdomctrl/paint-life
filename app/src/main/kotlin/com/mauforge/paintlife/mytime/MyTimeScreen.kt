package com.mauforge.paintlife.mytime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.mauforge.paintlife.core.designsystem.component.PaintLifeSheet
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

@Composable
fun MyTimeScreen(
    modifier: Modifier = Modifier,
    onOpenInsights: () -> Unit = {},
    onSuggestSchedule: () -> Unit = {},
    viewModel: MyTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    Box(modifier.fillMaxSize()) {
        val content = state.content
        if (content == null) {
            LoadingState(Modifier.fillMaxSize())
        } else {
            MyTimeContent(
                state = content,
                onPreviousDay = viewModel::previousDay,
                onNextDay = viewModel::nextDay,
                onLayerChange = viewModel::setLayer,
                onLogActivity = viewModel::openNew,
                onOpenEntry = viewModel::openEntry,
                onOpenInsights = onOpenInsights,
                onSuggestSchedule = onSuggestSchedule,
                onFillGap = viewModel::fillGap,
                onStartNow = viewModel::startNow,
                onStopNow = viewModel::stopNow,
                onOpenCategories = viewModel::openCategories,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(PaintLifeTheme.spacing.md).testTag("my_time_snackbar"),
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

    val editor = state.editor
    if (editor != null) {
        MyTimeSheet(onDismiss = viewModel::closeEditor) {
            val overlap = state.overlap
            if (overlap != null) {
                OverlapContent(
                    state = overlap,
                    onTrim = { viewModel.resolve(Resolution.Fit) },
                    onReplace = { viewModel.resolve(Resolution.Replace) },
                    onGoBack = viewModel::backToEditor,
                )
            } else {
                LogActivityContent(
                    state = editor,
                    onLayerChange = viewModel::setEditorLayer,
                    onSelectCategory = viewModel::selectCategory,
                    onNudgeStart = viewModel::nudgeStart,
                    onNudgeEnd = viewModel::nudgeEnd,
                    onSave = viewModel::save,
                    onDelete = viewModel::delete,
                    onCancel = viewModel::closeEditor,
                    onNoteChange = viewModel::setNote,
                    onUseActivity = viewModel::useActivity,
                )
            }
        }
    }

    state.schedulePrompt?.let { prompt ->
        PaintLifeSheet(onDismiss = viewModel::closeSchedulePrompt) {
            SchedulePromptContent(prompt, onChangeDay = viewModel::changeScheduleDay, onCancel = viewModel::closeSchedulePrompt)
        }
    }

    state.categories?.let { categories ->
        PaintLifeSheet(onDismiss = viewModel::closeCategories) {
            CategoriesContent(categories, onToggle = viewModel::setDistraction, onDone = viewModel::closeCategories)
        }
    }

    // Keyed by token: a new offer replaces the previous one. It never undoes on its own.
    val undo = state.undo
    LaunchedEffect(undo?.token) {
        if (undo == null) return@LaunchedEffect
        val message = when (undo.kind) {
            MyTimeUndo.Kind.Deleted -> R.string.my_time_deleted
            MyTimeUndo.Kind.Fitted -> R.string.my_time_fitted
            MyTimeUndo.Kind.Copied -> R.string.my_time_copied
        }
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(message),
            actionLabel = resources.getString(R.string.action_undo),
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(undo.token) else viewModel.undoExpired(undo.token)
    }

    val notice = state.notice
    LaunchedEffect(notice?.token) {
        if (notice == null) return@LaunchedEffect
        val message = when (notice.kind) {
            MyTimeNotice.Kind.Invalid -> R.string.my_time_notice_invalid
            MyTimeNotice.Kind.Missing -> R.string.my_time_notice_missing
            MyTimeNotice.Kind.UndoFailed -> R.string.my_time_notice_undo_failed
            MyTimeNotice.Kind.Future -> R.string.my_time_notice_future
        }
        // Cleared only once shown: clearing first would change this effect's key and cancel it.
        try {
            snackbarHostState.showSnackbar(resources.getString(message), duration = SnackbarDuration.Short)
        } finally {
            viewModel.noticeShown(notice.token)
        }
    }
}

/**
 * The log and overlap sheets. Their bodies are scrolling lists of their own, so they sit directly
 * in the bottom sheet (which bounds their height) rather than in [PaintLifeSheet]'s scrolling column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyTimeSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = PaintLifeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.canvas,
        contentColor = colors.text,
    ) {
        Box(Modifier.testTag("log_sheet")) { content() }
    }
}
