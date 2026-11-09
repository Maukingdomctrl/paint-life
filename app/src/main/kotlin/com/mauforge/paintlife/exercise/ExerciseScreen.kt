package com.mauforge.paintlife.exercise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.ConfirmDialog
import com.mauforge.paintlife.core.designsystem.component.LoadingState

/**
 * The Exercise tab. A win whose victory moment hasn't been shown yet (for example one applied
 * at app start after the app was closed during the undo window) opens its result once.
 */
@Composable
fun ExerciseScreen(
    onOpenSession: () -> Unit,
    onStartTimedChallenge: (level: Int) -> Unit,
    onOpenCat: (level: Int) -> Unit,
    onOpenResult: (sessionId: String) -> Unit,
    onOpenSafety: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingVictory by viewModel.pendingVictory.collectAsStateWithLifecycle()
    val openResult by rememberUpdatedState(onOpenResult)
    LaunchedEffect(pendingVictory) { pendingVictory?.let { openResult(it) } }

    val content = state
    if (content == null) {
        LoadingState(modifier.fillMaxSize())
        return
    }
    ExerciseContent(
        state = content,
        onStartSession = onOpenSession,
        onResumeSession = onOpenSession,
        onStartTimedChallenge = onStartTimedChallenge,
        onOpenCat = onOpenCat,
        onOpenSafety = onOpenSafety,
        onOpenHistory = onOpenHistory,
        modifier = modifier,
    )
}

/** One cat, from the ladder. Start / Replay opens the counter for a timed challenge. */
@Composable
fun CatDetailScreen(
    onStartTimedChallenge: (level: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CatDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state
    if (detail == null) {
        LoadingState(modifier.fillMaxSize())
        return
    }
    CatDetailContent(
        entry = detail.entry,
        previous = detail.previous,
        onStartTimedChallenge = { onStartTimedChallenge(detail.entry.cat.level) },
        canStart = detail.canStart,
        modifier = modifier,
    )
}

/** The result of a timed challenge: the victory moment for a win, otherwise "1,000 reps done". */
@Composable
fun ChallengeResultScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseResultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val done by rememberUpdatedState(onContinue)
    when (val result = state) {
        null -> LoadingState(modifier.fillMaxSize())
        is ChallengeResultUi.Victory -> VictoryContent(result.state, onContinue = onContinue, modifier = modifier)
        is ChallengeResultUi.Complete -> ChallengeCompleteContent(result.state, onContinue = onContinue, modifier = modifier)
        ChallengeResultUi.Missing -> LaunchedEffect(Unit) { done() }
    }
}

/** The safety guidance, opened from the Exercise tab. "I've read this" remembers it and closes. */
@Composable
fun ExerciseSafetyScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseSafetyViewModel = hiltViewModel(),
) {
    SafetyGuidanceContent(onDone = { viewModel.markRead(onDone) }, modifier = modifier)
}

/**
 * The counter route: safety first on the very first session, then the counter with its dialogs
 * and the correction sheet. Closes itself once the session is finished, stopped or discarded;
 * after a timed challenge reaches 1,000 and its undo window closes, the result takes over. The
 * screen stays on while counting, and not while paused.
 */
@Composable
fun ExerciseSessionScreen(
    onClose: () -> Unit,
    onShowResult: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)
    val showResult by rememberUpdatedState(onShowResult)

    if (state.keepScreenOn) KeepScreenOn()

    when (val phase = state.phase) {
        SessionPhase.Loading -> LoadingState(modifier.fillMaxSize())
        SessionPhase.SafetyFirst -> SafetyGuidanceContent(onDone = viewModel::safetyRead, modifier = modifier)
        SessionPhase.Closed -> LaunchedEffect(Unit) { close() }
        is SessionPhase.Result -> LaunchedEffect(phase.sessionId) { showResult(phase.sessionId) }
        is SessionPhase.Counting -> {
            ExerciseSessionContent(
                state = phase.content,
                onAdd = viewModel::add,
                onUndo = viewModel::undo,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onFinish = viewModel::finish,
                onCorrectCount = viewModel::openCorrection,
                onReset = viewModel::requestReset,
                onUndoFinish = viewModel::undoFinish,
                modifier = modifier,
            )
            if (state.correcting) {
                CorrectCountSheet(
                    currentReps = phase.content.reps,
                    maxReps = phase.content.maxReps,
                    onSave = viewModel::correct,
                    onDismiss = viewModel::closeCorrection,
                )
            }
            SessionDialogs(state.dialog, viewModel)
        }
    }
}

@Composable
private fun SessionDialogs(dialog: SessionDialog?, viewModel: ExerciseSessionViewModel) {
    val tagged = Modifier.testTag("confirm_dialog")
    when (dialog) {
        SessionDialog.ConfirmReset -> ConfirmDialog(
            title = stringResource(R.string.session_reset_title),
            body = stringResource(R.string.session_reset_body),
            confirmLabel = stringResource(R.string.session_reset_confirm),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = viewModel::confirmReset,
            onDismiss = viewModel::dismissDialog,
            modifier = tagged,
        )
        SessionDialog.DiscardEmpty -> ConfirmDialog(
            title = stringResource(R.string.session_discard_title),
            body = stringResource(R.string.session_discard_body),
            confirmLabel = stringResource(R.string.session_discard_confirm),
            dismissLabel = stringResource(R.string.session_keep_going),
            onConfirm = viewModel::discard,
            onDismiss = viewModel::dismissDialog,
            modifier = tagged,
        )
        SessionDialog.ConfirmStop -> ConfirmDialog(
            title = stringResource(R.string.session_stop_title),
            body = stringResource(R.string.session_stop_body),
            confirmLabel = stringResource(R.string.session_stop_challenge),
            dismissLabel = stringResource(R.string.session_keep_going),
            onConfirm = viewModel::confirmStop,
            onDismiss = viewModel::dismissDialog,
            modifier = tagged,
        )
        null -> Unit
    }
}

/** Keeps the display on while this is in the composition. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Finished sessions, with the edit sheet and the delete confirmation. */
@Composable
fun ExerciseHistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: ExerciseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val content = state.content
    if (content == null) {
        LoadingState(modifier.fillMaxSize())
        return
    }
    ExerciseHistoryContent(state = content, onOpenSession = viewModel::open, modifier = modifier)
    state.editor?.let { editor ->
        HistoryEditorSheet(editor = editor, onSave = viewModel::save, onDelete = viewModel::requestDelete, onDismiss = viewModel::close)
        if (state.confirmingDelete) {
            ConfirmDialog(
                title = stringResource(R.string.history_delete_title),
                body = stringResource(R.string.history_delete_body, pluralStringResource(R.plurals.reps, editor.reps, editor.reps)),
                confirmLabel = stringResource(R.string.history_delete_confirm),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::cancelDelete,
                modifier = Modifier.testTag("confirm_dialog"),
            )
        }
    }
}
