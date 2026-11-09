package com.mauforge.paintlife.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.PaintLifeSheet
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.paintLifeFieldColors
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

/** Digits only, up to 5 of them; null when empty or out of 0..[max]. */
internal fun parseReps(text: String, max: Int = SessionCounter.MAX_REPS): Int? =
    text.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 0..max }

/** A count field: number keyboard, digits only, with the allowed range always shown. */
@Composable
private fun RepsField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    max: Int = SessionCounter.MAX_REPS,
) {
    val invalid = value.text.isNotEmpty() && parseReps(value.text, max) == null
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.text.length <= 5 && next.text.all(Char::isDigit)) onValueChange(next) },
        label = { Text(stringResource(R.string.reps_field_label)) },
        supportingText = {
            Text(stringResource(R.string.reps_field_range, NumberFormat.getIntegerInstance().format(max)))
        },
        isError = invalid,
        singleLine = true,
        textStyle = PaintLifeTheme.numbers.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = paintLifeFieldColors(),
        modifier = modifier.fillMaxWidth().testTag("reps_field"),
    )
}

/** Set the session's count by hand. Undo on the counter puts the previous count back. */
@Composable
fun CorrectCountSheet(currentReps: Int, maxReps: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    PaintLifeSheet(onDismiss = onDismiss, modifier = Modifier.testTag("correct_count_sheet")) {
        CorrectCountBody(currentReps, onSave, maxReps = maxReps)
    }
}

@Composable
internal fun CorrectCountBody(
    currentReps: Int,
    onSave: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxReps: Int = SessionCounter.MAX_REPS,
) {
    val initial = currentReps.toString()
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val reps = parseReps(value.text, maxReps)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.lg)) {
        Text(
            text = stringResource(R.string.session_correct_count),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.session_correct_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PaintLifeTheme.colors.textSecondary,
        )
        RepsField(value, { value = it }, onDone = { reps?.let(onSave) }, Modifier.focusRequester(focusRequester), max = maxReps)
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = { reps?.let(onSave) },
            enabled = reps != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Edit one finished session: its count (step-up sessions only) and note. Delete asks for
 * confirmation first (the screen shows the dialog).
 */
@Composable
fun HistoryEditorSheet(
    editor: HistoryEditorUi,
    onSave: (reps: Int, note: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    PaintLifeSheet(onDismiss = onDismiss, modifier = Modifier.testTag("history_editor")) {
        HistoryEditorBody(editor, onSave, onDelete, onCancel = onDismiss)
    }
}

@Composable
internal fun HistoryEditorBody(
    editor: HistoryEditorUi,
    onSave: (reps: Int, note: String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    var repsValue by rememberSaveable(editor.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(editor.reps.toString(), TextRange(editor.reps.toString().length)))
    }
    val repsText = repsValue.text
    var note by rememberSaveable(editor.id) { mutableStateOf(editor.note) }
    val reps = if (editor.canEditReps) parseReps(repsText) else editor.reps
    val canSave = reps != null && !editor.saving

    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.lg)) {
        Column {
            Text(
                text = stringResource(R.string.history_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.history_edit_started,
                    editor.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", currentLocale())),
                    timeLabel(editor.startTime),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        if (editor.canEditReps) {
            RepsField(value = repsValue, onValueChange = { repsValue = it }, onDone = { reps?.let { onSave(it, note) } })
        } else {
            Text(
                text = stringResource(R.string.history_timed_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= SessionNote.MAX_LENGTH) note = it },
            label = { Text(stringResource(R.string.note_field_label)) },
            supportingText = {
                Text(
                    text = stringResource(R.string.note_field_counter, note.length, SessionNote.MAX_LENGTH),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
            },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = paintLifeFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("note_field"),
        )
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = { reps?.let { onSave(it, note) } },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editor.canDelete) {
            DestructiveButton(text = stringResource(R.string.history_delete), onClick = onDelete, enabled = !editor.saving, modifier = Modifier.fillMaxWidth())
        }
        QuietButton(text = stringResource(R.string.action_cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}
