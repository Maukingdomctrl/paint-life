package com.mauforge.paintlife.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/** Bottom sheet frame shared by the Today sheets: surface colour, gutter, scrolls at large text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodaySheet(onDismiss: () -> Unit, testTag: String, content: @Composable () -> Unit) {
    val colors = PaintLifeTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PaintLifeTheme.spacing.screenGutter)
                .padding(bottom = PaintLifeTheme.spacing.xl)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.lg),
        ) {
            content()
        }
    }
}

/**
 * Add or edit one priority. Editing also offers Move up / Move down (applied at once) and
 * Delete, which needs no confirmation because the screen then offers Undo.
 */
@Composable
fun PriorityEditorSheet(
    editor: PriorityEditorUi,
    onSave: (String) -> Unit,
    onMove: (MoveDirection) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    TodaySheet(onDismiss = onDismiss, testTag = "priority_editor") {
        PriorityEditorBody(editor, onSave, onMove, onDelete)
    }
}

@Composable
internal fun PriorityEditorBody(
    editor: PriorityEditorUi,
    onSave: (String) -> Unit,
    onMove: (MoveDirection) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    var title by rememberSaveable(editor.id) { mutableStateOf(editor.initialTitle) }
    val canSave = PriorityTitle.clean(title) != null && !editor.saving
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editor.id) { if (editor.id == null) focusRequester.requestFocus() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        Text(
            text = stringResource(if (editor.id == null) R.string.today_add_title else R.string.today_edit_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val counterDescription = pluralStringResource(R.plurals.today_priority_counter_description, title.length, title.length, PriorityTitle.MAX_LENGTH)
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= PriorityTitle.MAX_LENGTH) title = it.replace('\n', ' ') },
            label = { Text(stringResource(R.string.today_priority_label)) },
            supportingText = {
                Text(
                    text = stringResource(R.string.today_priority_counter, title.length, PriorityTitle.MAX_LENGTH),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = counterDescription },
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(title) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                focusedLabelColor = colors.accent,
                unfocusedLabelColor = colors.textSecondary,
                cursorColor = colors.accent,
                focusedContainerColor = colors.sunken,
                unfocusedContainerColor = colors.sunken,
                focusedSupportingTextColor = colors.textSecondary,
                unfocusedSupportingTextColor = colors.textSecondary,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("priority_title_field"),
        )
        PrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = { onSave(title) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editor.id != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SecondaryButton(
                    text = stringResource(R.string.today_move_up),
                    onClick = { onMove(MoveDirection.Up) },
                    enabled = editor.canMoveUp,
                    icon = painterResource(R.drawable.ic_arrow_up),
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.today_move_down),
                    onClick = { onMove(MoveDirection.Down) },
                    enabled = editor.canMoveDown,
                    icon = painterResource(R.drawable.ic_arrow_down),
                    modifier = Modifier.weight(1f),
                )
            }
            DestructiveButton(
                text = stringResource(R.string.today_delete_priority),
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Pick which unfinished priorities to copy to today. Nothing is preselected; at most
 * [CarryOverUi.freePlaces] can be chosen. The originals stay on their own day.
 */
@Composable
fun CarryOverSheet(
    offer: CarryOverUi,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    TodaySheet(onDismiss = onDismiss, testTag = "carry_over_sheet") {
        CarryOverBody(offer, onConfirm, onCancel = onDismiss)
    }
}

@Composable
internal fun CarryOverBody(
    offer: CarryOverUi,
    onConfirm: (List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val full = selected.size >= offer.freePlaces

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.today_carry_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = pluralStringResource(R.plurals.today_carry_room, offer.freePlaces, offer.freePlaces) + " " +
                stringResource(R.string.today_carry_note),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Column {
            offer.items.forEach { item ->
                val checked = item.id in selected
                val enabled = checked || !full
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { on -> selected = if (on) selected + item.id else selected - item.id },
                        )
                        .padding(vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckCircle(checked)
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) colors.text else colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        PrimaryButton(
            text = if (selected.isEmpty()) {
                stringResource(R.string.today_carry_sheet_title)
            } else {
                pluralStringResource(R.plurals.today_carry_confirm, selected.size, selected.size)
            },
            onClick = { onConfirm(selected) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        QuietButton(
            text = stringResource(R.string.action_cancel),
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
