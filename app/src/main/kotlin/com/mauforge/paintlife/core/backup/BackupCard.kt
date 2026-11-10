package com.mauforge.paintlife.core.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** File types offered when choosing a backup; some file apps label JSON as plain text or "unknown". */
private val ImportTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * "Your data" on Progress: export and import through the system file picker. Wires the pickers
 * to [BackupViewModel]; what is shown is [BackupCard] and [ImportConfirmDialog].
 */
@Composable
fun BackupSection(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), viewModel::exportTo)
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::importFrom)
    val export = {
        try {
            exportLauncher.launch(viewModel.suggestedFileName())
        } catch (_: ActivityNotFoundException) {
            viewModel.pickerUnavailable()
        }
    }

    // Import opens the picker only once the ViewModel has checked that nothing is in progress.
    val openPicker = state.openPicker
    LaunchedEffect(openPicker) {
        if (openPicker == null) return@LaunchedEffect
        viewModel.pickerOpened(openPicker)
        try {
            importLauncher.launch(ImportTypes)
        } catch (_: ActivityNotFoundException) {
            viewModel.pickerUnavailable()
        }
    }

    BackupCard(state = state, onExport = export, onImport = viewModel::requestImport, modifier = modifier)
    state.preview?.let { preview ->
        ImportConfirmDialog(
            preview = preview,
            busy = state.busy,
            onExportFirst = export,
            onReplace = viewModel::confirmImport,
            onCancel = viewModel::cancelImport,
        )
    }
}

/** The card: what a backup is, the two actions, and what the last one did. Stateless. */
@Composable
fun BackupCard(
    state: BackupUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(modifier = modifier.fillMaxWidth().testTag("backup_card")) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(text = stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(text = stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            SecondaryButton(stringResource(R.string.backup_export), onExport, Modifier.fillMaxWidth(), enabled = !state.busy)
            SecondaryButton(stringResource(R.string.backup_import), onImport, Modifier.fillMaxWidth(), enabled = !state.busy)
            // Not shown while the confirmation is open: there, the message has its own line.
            state.message?.takeIf { state.preview == null }?.let { message ->
                val failed = message is BackupMessage.ImportFailed || message is BackupMessage.ExportFailed || message is BackupMessage.NoFilePicker
                Text(
                    text = messageText(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) colors.danger else colors.text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("backup_message"),
                )
            }
        }
    }
}

/**
 * Before an import replaces anything: what is in the file, what is on the phone now, and three
 * choices. Only "Replace everything" writes; "Export current data first" saves a copy and comes
 * back here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportConfirmDialog(
    preview: ImportPreview,
    busy: Boolean,
    onExportFirst: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val title = stringResource(R.string.backup_import_title)
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale())
    val exported = preview.exportedAt.atZone(preview.exportedZone).format(formatter)
    BasicAlertDialog(onDismissRequest = onCancel, modifier = modifier.semantics { paneTitle = title }.testTag("import_dialog")) {
        Surface(shape = PaintLifeTheme.shapes.card, color = colors.raised, contentColor = colors.text) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(spacing.xl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(text = stringResource(R.string.backup_import_file, exported), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.backup_import_in_file, summaryText(preview.inFile)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("import_in_file"),
                )
                Text(
                    text = stringResource(R.string.backup_import_on_phone, summaryText(preview.onPhone)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("import_on_phone"),
                )
                Text(text = stringResource(R.string.backup_import_warning), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                if (preview.currentSaved) {
                    Text(
                        text = stringResource(R.string.backup_import_current_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("import_current_saved"),
                    )
                }
                SecondaryButton(stringResource(R.string.backup_import_export_first), onExportFirst, Modifier.fillMaxWidth().padding(top = spacing.sm), enabled = !busy)
                DestructiveButton(stringResource(R.string.backup_import_replace), onReplace, Modifier.fillMaxWidth(), enabled = !busy)
                QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), enabled = !busy)
            }
        }
    }
}

/** "3 priorities, 12 exercise sessions, …", leaving out what there is none of. */
@Composable
private fun summaryText(summary: BackupSummary): String {
    if (summary.isEmpty) return stringResource(R.string.backup_nothing)
    return listOf(
        R.plurals.backup_count_priorities to summary.priorities,
        R.plurals.backup_count_exercise to summary.exerciseSessions,
        R.plurals.backup_count_quiet to summary.quietSessions,
        R.plurals.backup_count_entries to summary.timeEntries,
        R.plurals.backup_count_schedules to summary.schedules,
    ).filter { it.second > 0 }.map { (plural, count) -> pluralStringResource(plural, count, count) }.joinToString(", ")
}

@Composable
private fun messageText(message: BackupMessage): String = when (message) {
    is BackupMessage.Exported -> stringResource(R.string.backup_exported, summaryText(message.summary))
    is BackupMessage.Imported -> stringResource(R.string.backup_imported, summaryText(message.summary))
    is BackupMessage.Blocked -> stringResource(
        R.string.backup_blocked,
        InProgress.entries.filter { it in message.inProgress }.map {
            stringResource(
                when (it) {
                    InProgress.Exercise -> R.string.backup_blocked_exercise
                    InProgress.Quiet -> R.string.backup_blocked_quiet
                    InProgress.NowDoing -> R.string.backup_blocked_now_doing
                },
            )
        }.joinToString(", "),
    )
    BackupMessage.ExportFailed -> stringResource(R.string.backup_export_failed)
    BackupMessage.NoFilePicker -> stringResource(R.string.backup_no_picker)
    is BackupMessage.ImportFailed -> when (message.problem) {
        BackupProblem.NotABackup -> stringResource(R.string.backup_problem_not_a_backup)
        BackupProblem.TooNew -> stringResource(R.string.backup_problem_too_new)
        BackupProblem.TooLarge -> stringResource(R.string.backup_problem_too_large)
        BackupProblem.Unreadable -> stringResource(R.string.backup_problem_unreadable)
        else -> stringResource(
            R.string.backup_problem_invalid,
            stringResource(
                when (message.problem) {
                    BackupProblem.BadTime -> R.string.backup_reason_bad_time
                    BackupProblem.UnknownValue -> R.string.backup_reason_unknown_value
                    BackupProblem.DuplicateId -> R.string.backup_reason_duplicate
                    BackupProblem.BrokenLink -> R.string.backup_reason_broken_link
                    BackupProblem.Priorities -> R.string.backup_reason_priorities
                    BackupProblem.CatLeague -> R.string.backup_reason_cat_league
                    BackupProblem.Sessions -> R.string.backup_reason_sessions
                    BackupProblem.Categories -> R.string.backup_reason_categories
                    BackupProblem.TimeEntries -> R.string.backup_reason_time_entries
                    BackupProblem.Overlap -> R.string.backup_reason_overlap
                    BackupProblem.Schedules -> R.string.backup_reason_schedules
                    else -> R.string.backup_reason_damaged
                },
            ),
        )
    }
}
