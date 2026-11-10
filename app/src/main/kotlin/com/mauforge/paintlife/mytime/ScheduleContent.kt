package com.mauforge.paintlife.mytime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.CategoryChip
import com.mauforge.paintlife.core.designsystem.component.ConfirmDialog
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.LoadingState
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PaintLifeSheet
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.paintLifeFieldColors
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun ScheduleScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    BackHandler { if (viewModel.requestLeave()) onDone() }
    LaunchedEffect(state.approved) { if (state.approved) onDone() }

    Box(modifier.fillMaxSize()) {
        ScheduleContent(
            state = state.content,
            onNameChange = viewModel::setName,
            onOpenBlock = viewModel::openBlock,
            onAddBlock = viewModel::addBlock,
            onApprove = viewModel::approve,
            onDiscard = { if (viewModel.requestLeave()) onDone() },
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(PaintLifeTheme.spacing.md)) { data ->
            val colors = PaintLifeTheme.colors
            Snackbar(snackbarData = data, containerColor = colors.raised, contentColor = colors.text, actionColor = colors.accent, shape = PaintLifeTheme.shapes.tile)
        }
    }

    state.editor?.let { editor ->
        PaintLifeSheet(onDismiss = viewModel::closeBlock) {
            BlockEditorContent(
                state = editor,
                onSelectCategory = viewModel::selectCategory,
                onNudgeStart = viewModel::nudgeStart,
                onNudgeEnd = viewModel::nudgeEnd,
                onToggleDay = viewModel::toggleDay,
                onSave = viewModel::saveBlock,
                onDelete = viewModel::deleteBlock,
                onCancel = viewModel::closeBlock,
            )
        }
    }

    if (state.confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.schedule_discard_title),
            body = stringResource(R.string.schedule_discard_body),
            confirmLabel = stringResource(R.string.schedule_discard_confirm),
            dismissLabel = stringResource(R.string.schedule_keep_editing),
            onConfirm = onDone,
            onDismiss = viewModel::keepEditing,
            modifier = Modifier.testTag("confirm_dialog"),
        )
    }

    val undo = state.undoToken
    LaunchedEffect(undo) {
        if (undo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = resources.getString(R.string.schedule_block_removed),
            actionLabel = resources.getString(R.string.action_undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(undo) else viewModel.undoExpired(undo)
    }

    val invalid = state.invalidToken
    LaunchedEffect(invalid) {
        if (invalid == null) return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(resources.getString(R.string.schedule_invalid), duration = SnackbarDuration.Short)
        } finally {
            viewModel.invalidShown(invalid)
        }
    }
}

/**
 * The suggested schedule as an editable proposal. It states what it is based on, shows each
 * block's evidence, and is saved only by Approve. Stateless.
 */
@Composable
fun ScheduleContent(
    state: ScheduleContentUi,
    onNameChange: (String) -> Unit,
    onOpenBlock: (String) -> Unit,
    onAddBlock: () -> Unit,
    onApprove: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    when (state) {
        ScheduleContentUi.Loading -> LoadingState(modifier.fillMaxSize())
        is ScheduleContentUi.NotEnough -> PaintLifeScreen(modifier = modifier.testTag("schedule_not_enough")) {
            item(key = "title") { ScheduleTitle() }
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.schedule_not_enough_title),
                    body = pluralStringResource(R.plurals.schedule_not_enough_body, state.recordedDays, state.recordedDays, ScheduleSuggester.MIN_DAYS),
                )
            }
        }
        is ScheduleContentUi.Proposal -> PaintLifeScreen(modifier = modifier.testTag("schedule_proposal"), verticalSpacing = spacing.lg) {
            item(key = "title") { ScheduleTitle() }
            item(key = "basis") {
                val format = DateTimeFormatter.ofPattern("d MMM", currentLocale())
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.schedule_basis,
                            state.recordedDays,
                            state.recordedDays,
                            state.basedOnFrom.format(format),
                            state.basedOnTo.format(format),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("schedule_basis"),
                    )
                    if (state.unrecordedPercent > 0) {
                        Text(
                            text = stringResource(R.string.schedule_unrecorded, state.unrecordedPercent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    Text(
                        text = stringResource(if (state.split) R.string.schedule_split else R.string.schedule_one_pattern),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(text = stringResource(R.string.schedule_how), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            item(key = "name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onNameChange(it.replace('\n', ' ')) },
                    label = { Text(stringResource(R.string.schedule_name)) },
                    singleLine = true,
                    colors = paintLifeFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_name"),
                )
            }
            if (state.blocks.isEmpty()) {
                item(key = "no_blocks") {
                    PaintLifeCard(Modifier.fillMaxWidth().testTag("schedule_empty")) {
                        Text(text = stringResource(R.string.schedule_no_pattern), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.blocks.forEach { block ->
                item(key = block.id) { BlockRow(block, onClick = { onOpenBlock(block.id) }) }
            }
            item(key = "add") { SecondaryButton(stringResource(R.string.schedule_add_block), onAddBlock, Modifier.fillMaxWidth()) }
            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (state.replacesCurrent) {
                        Text(text = stringResource(R.string.schedule_replaces), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                    Text(text = stringResource(R.string.schedule_approve_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    PrimaryButton(stringResource(R.string.schedule_approve), onApprove, Modifier.fillMaxWidth(), enabled = state.canApprove)
                    QuietButton(stringResource(R.string.schedule_not_now), onDiscard, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ScheduleTitle() {
    Text(text = stringResource(R.string.schedule_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun BlockRow(block: ProposalBlockUi, onClick: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val evidence = block.seenDays?.let { seen ->
        val total = block.groupDays ?: 0
        pluralStringResource(
            when (block.group) {
                EvidenceGroup.Weekdays -> R.plurals.schedule_evidence_weekdays
                EvidenceGroup.Weekend -> R.plurals.schedule_evidence_weekend
                EvidenceGroup.Days -> R.plurals.schedule_evidence_days
            },
            total,
            seen,
            total,
        )
    } ?: stringResource(R.string.schedule_evidence_yours)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PaintLifeTheme.shapes.card)
            .background(colors.surface)
            .border(1.dp, colors.border, PaintLifeTheme.shapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.md)
            .testTag("schedule_block_${block.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(Modifier.size(14.dp).clip(PaintLifeTheme.shapes.bar).background(colors.category(block.colorKey)))
        Column(Modifier.weight(1f)) {
            Text(text = block.categoryName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${ScheduleViewModel.clock(block.startMinute)}–${ScheduleViewModel.clock(block.endMinute)} · ${daysLabel(block.daysMask)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = evidence, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun daysLabel(mask: Int): String = when (mask) {
    ScheduleRules.ALL_DAYS -> stringResource(R.string.schedule_every_day)
    ScheduleRules.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
    ScheduleRules.WEEKEND -> stringResource(R.string.schedule_weekend)
    else -> {
        val locale = currentLocale()
        DayOfWeek.entries.filter { ScheduleRules.hasDay(mask, it) }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

/** Edit one block of the proposal: category, times (5-minute steps) and days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockEditorContent(
    state: BlockEditorUi,
    onSelectCategory: (String) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onToggleDay: (Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(modifier.testTag("block_editor"), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(if (state.isNew) R.string.schedule_add_block else R.string.schedule_edit_block),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            state.categories.forEach { option ->
                CategoryChip(
                    name = option.name,
                    color = colors.category(option.colorKey),
                    isDistraction = option.isDistraction,
                    selected = option.id == state.selectedCategoryId,
                    onClick = { onSelectCategory(option.id) },
                )
            }
        }
        TimeStepper(stringResource(R.string.my_time_start), ScheduleViewModel.clock(state.startMinute), onNudgeStart, "block_start")
        val end = ScheduleViewModel.clock(state.endMinute)
        TimeStepper(
            stringResource(R.string.my_time_end),
            if (state.endMinute < state.startMinute) stringResource(R.string.my_time_next_day_suffix, end) else end,
            onNudgeEnd,
            "block_end",
        )
        Text(text = stringResource(R.string.schedule_days), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        val locale = currentLocale()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            DayOfWeek.entries.forEachIndexed { index, day ->
                val on = state.daysMask and (1 shl index) != 0
                Box(
                    modifier = Modifier
                        .widthIn(min = spacing.minTouchTarget)
                        .heightIn(min = spacing.minTouchTarget)
                        .clip(PaintLifeTheme.shapes.pill)
                        .background(if (on) colors.accentMuted else colors.sunken)
                        .border(if (on) 2.dp else 1.dp, if (on) colors.accent else colors.border, PaintLifeTheme.shapes.pill)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggleDay(index) })
                        .padding(horizontal = spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = day.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
        state.problem?.let { problem ->
            Text(
                text = when (problem) {
                    BlockProblem.SameTimes -> stringResource(R.string.schedule_problem_same)
                    BlockProblem.NoDays -> stringResource(R.string.schedule_problem_days)
                    BlockProblem.Overlaps -> stringResource(R.string.schedule_problem_overlap, state.overlapsWith.orEmpty())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.highlight,
                modifier = Modifier.testTag("block_problem"),
            )
        }
        PrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth(), enabled = state.canSave)
        if (!state.isNew) DestructiveButton(stringResource(R.string.schedule_remove_block), onDelete, Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
    }
}
