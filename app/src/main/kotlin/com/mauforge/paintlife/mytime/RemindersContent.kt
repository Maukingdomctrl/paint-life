package com.mauforge.paintlife.mytime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SegmentedPicker
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.paintLifeFieldColors
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The Reminders sheet's body, with its view model and the Undo snackbar for a deleted reminder. */
@Composable
fun RemindersSheet(
    access: ReminderAccess,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    RemindersContent(
        state = state,
        access = access,
        onAdd = viewModel::openDraft,
        onTitleChange = viewModel::setTitle,
        onNudge = viewModel::nudge,
        onActionChange = viewModel::setAction,
        onSave = {
            access.askNotifications()
            viewModel.saveDraft()
        },
        onCancelDraft = viewModel::closeDraft,
        onDelete = viewModel::delete,
        onBedtimeOn = { on ->
            if (on) access.askNotifications()
            viewModel.setBedtimeOn(on)
        },
        onNudgeBedtime = viewModel::nudgeBedtime,
        onDone = onDone,
        modifier = modifier,
    )
    // In the sheet itself, so it shows above the sheet rather than behind it.
    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag("reminders_snackbar")) { data ->
        val colors = PaintLifeTheme.colors
        Snackbar(snackbarData = data, containerColor = colors.raised, contentColor = colors.text, actionColor = colors.accent, shape = PaintLifeTheme.shapes.tile)
    }

    val undo = state.undoToken
    LaunchedEffect(undo) {
        if (undo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.reminder_deleted),
            actionLabel = resources.getString(R.string.action_undo),
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(undo) else viewModel.undoExpired(undo)
    }
    val failed = state.undoFailedToken
    LaunchedEffect(failed) {
        if (failed == null) return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(resources.getString(R.string.reminder_undo_failed), duration = SnackbarDuration.Short)
        } finally {
            viewModel.undoFailedShown(failed)
        }
    }
}

/**
 * Reminders: the one-off reminders still to come, the form to add one, and the bedtime reminder.
 * Nothing reminds unless it was set here or on a planned activity. Stateless.
 */
@Composable
fun RemindersContent(
    state: RemindersUiState,
    access: ReminderAccess,
    onAdd: () -> Unit,
    onTitleChange: (String) -> Unit,
    onNudge: (Long) -> Unit,
    onActionChange: (StartAction) -> Unit,
    onSave: () -> Unit,
    onCancelDraft: () -> Unit,
    onDelete: (String) -> Unit,
    onBedtimeOn: (Boolean) -> Unit,
    onNudgeBedtime: (Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(modifier.testTag("reminders_sheet"), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(text = stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(text = stringResource(R.string.reminders_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        ReminderAccessNotes(access)

        Text(text = stringResource(R.string.reminders_one_off_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (state.oneOffs.isEmpty() && state.draft == null) {
            Text(text = stringResource(R.string.reminders_none), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        state.oneOffs.forEach { reminder -> OneOffRow(reminder, onDelete) }

        val draft = state.draft
        if (draft == null) {
            SecondaryButton(stringResource(R.string.action_add_reminder), onAdd, Modifier.fillMaxWidth().testTag("add_reminder"), icon = painterResource(R.drawable.ic_add))
        } else {
            DraftForm(draft, onTitleChange, onNudge, onActionChange, onSave, onCancelDraft)
        }

        Text(text = stringResource(R.string.reminders_bedtime_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouchTarget)
                .toggleable(value = state.bedtime != null, role = Role.Switch, onValueChange = onBedtimeOn)
                .testTag("bedtime_switch"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(R.string.reminders_bedtime_label), style = MaterialTheme.typography.titleSmall)
                Text(text = stringResource(R.string.reminders_bedtime_body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Switch(
                checked = state.bedtime != null,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = colors.canvas,
                    uncheckedTrackColor = colors.sunken,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedBorderColor = colors.border,
                ),
            )
        }
        state.bedtime?.let { TimeStepper(label = stringResource(R.string.reminders_bedtime_time), value = it, onNudge = onNudgeBedtime, tag = "bedtime") }

        PrimaryButton(stringResource(R.string.action_done), onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun OneOffRow(reminder: OneOffUi, onDelete: (String) -> Unit) {
    val colors = PaintLifeTheme.colors
    PaintLifeCard(Modifier.fillMaxWidth().testTag("one_off_reminder")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = reminder.title, style = MaterialTheme.typography.titleSmall)
                val starts = startLabel(reminder.action)
                Text(
                    text = listOfNotNull("${dayLabel(reminder.date)}, ${reminder.time}", starts?.let { stringResource(R.string.reminder_starts, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = { onDelete(reminder.id) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.reminder_delete, reminder.title), tint = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DraftForm(
    draft: ReminderDraftUi,
    onTitleChange: (String) -> Unit,
    onNudge: (Long) -> Unit,
    onActionChange: (StartAction) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(Modifier.testTag("reminder_form"), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.reminder_title_label)) },
            singleLine = true,
            colors = paintLifeFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("reminder_title_field"),
        )
        DayStepper(dayLabel(draft.date), onNudge)
        TimeStepper(label = stringResource(R.string.reminder_time_label), value = draft.time, onNudge = onNudge, tag = "reminder")
        if (draft.notAhead) {
            Text(text = stringResource(R.string.reminder_not_ahead), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Text(text = stringResource(R.string.reminder_start_now_opens), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        SegmentedPicker(
            options = listOf(StartAction.NONE, StartAction.EXERCISE, StartAction.QUIET, StartAction.SLEEP),
            selected = draft.action,
            onSelect = onActionChange,
            label = { startLabel(it) ?: stringResource(R.string.reminder_start_nothing) },
        )
        PrimaryButton(stringResource(R.string.action_save_reminder), onSave, Modifier.fillMaxWidth(), enabled = draft.canSave)
        QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
    }
}

/** The reminder's day, a day earlier or later at a tap. */
@Composable
private fun DayStepper(day: String, onNudge: (Long) -> Unit) {
    val colors = PaintLifeTheme.colors
    Row(Modifier.fillMaxWidth().testTag("reminder_day"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onNudge(-MINUTES_PER_DAY) }) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.reminder_day_earlier), tint = colors.text)
        }
        Text(text = day, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onNudge(MINUTES_PER_DAY) }) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.reminder_day_later), tint = colors.text)
        }
    }
}

private const val MINUTES_PER_DAY = 24 * 60L

@Composable
private fun dayLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", currentLocale()))

/** What "Start now" starts, in words; null for nothing. */
@Composable
private fun startLabel(action: StartAction): String? = when (action) {
    StartAction.NONE, StartAction.NOW_DOING -> null
    StartAction.EXERCISE -> stringResource(R.string.nav_exercise)
    StartAction.QUIET -> stringResource(R.string.reminder_start_quiet)
    StartAction.SLEEP -> stringResource(R.string.reminder_start_sleep)
}
