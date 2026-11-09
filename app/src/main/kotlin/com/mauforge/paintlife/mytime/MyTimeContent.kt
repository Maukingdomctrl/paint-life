package com.mauforge.paintlife.mytime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.CategoryChip
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.DistractionTag
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SegmentedPicker
import com.mauforge.paintlife.core.designsystem.component.hatched
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MinuteHeight: Dp = 1.2.dp
private val HourRailWidth: Dp = 52.dp
private val MinBlockHeight: Dp = 20.dp

/**
 * My Time for one day: date switcher, layer picker, the observation banner or insights, and a
 * vertical 24-hour timeline. Actual entries are filled blocks, planned ones are outlined in their
 * own lane, and time that passed unrecorded is hatched. Stateless; wired in Iteration 6.
 */
@Composable
fun MyTimeContent(
    state: MyTimeUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onLayerChange: (TimeLayer) -> Unit,
    onLogActivity: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onSuggestSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("my_time_content"), verticalSpacing = spacing.lg) {
        item(key = "header") { DayHeader(state, onPreviousDay, onNextDay) }
        item(key = "layer") {
            SegmentedPicker(
                options = TimeLayer.entries,
                selected = state.layer,
                onSelect = onLayerChange,
                label = { layerLabel(it) },
            )
        }
        state.observationDay?.let { day ->
            item(key = "observation") {
                PaintLifeCard(Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.my_time_observation_title, day), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.my_time_observation_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaintLifeTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }
        }
        if (state.insightsReady) {
            item(key = "insights") {
                PaintLifeCard(Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.my_time_insights_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.my_time_insights_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaintLifeTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xs, bottom = spacing.md),
                    )
                    SecondaryButton(stringResource(R.string.my_time_see_insights), onOpenInsights, Modifier.fillMaxWidth())
                    QuietButton(stringResource(R.string.my_time_suggest_schedule), onSuggestSchedule, Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "log") {
            PrimaryButton(stringResource(R.string.action_log_activity), onLogActivity, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_add))
        }
        item(key = "legend") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                val colors = PaintLifeTheme.colors
                Box(
                    Modifier
                        .width(28.dp)
                        .height(14.dp)
                        .clip(PaintLifeTheme.shapes.bar)
                        .hatched(colors.hatchBase, colors.hatchLine)
                        .border(1.dp, colors.border, PaintLifeTheme.shapes.bar),
                )
                Text(text = stringResource(R.string.my_time_legend_unrecorded), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        item(key = "timeline") { Timeline(state, onOpenEntry) }
    }
}

@Composable
private fun layerLabel(layer: TimeLayer): String = stringResource(
    when (layer) {
        TimeLayer.Actual -> R.string.my_time_layer_actual
        TimeLayer.Planned -> R.string.my_time_layer_planned
        TimeLayer.Both -> R.string.my_time_layer_both
    },
)

@Composable
private fun DayHeader(state: MyTimeUiState, onPreviousDay: () -> Unit, onNextDay: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale())
    Column {
        Text(text = stringResource(R.string.title_my_time), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousDay) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.my_time_previous_day), tint = colors.text)
            }
            Column(Modifier.weight(1f)) {
                Text(text = state.date.format(formatter), style = MaterialTheme.typography.titleMedium)
                if (state.isToday) {
                    Text(text = stringResource(R.string.my_time_today), style = MaterialTheme.typography.bodySmall, color = colors.accent)
                }
            }
            IconButton(onClick = onNextDay, enabled = !state.isToday) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = stringResource(R.string.my_time_next_day),
                    tint = if (state.isToday) colors.border else colors.text,
                )
            }
        }
    }
}

@Composable
private fun Timeline(state: MyTimeUiState, onOpenEntry: (String) -> Unit) {
    val colors = PaintLifeTheme.colors
    val showActual = state.layer != TimeLayer.Planned
    val showPlanned = state.layer != TimeLayer.Actual
    val gaps = if (showActual) {
        TimelineGeometry.unrecordedGaps(state.actual.map { it.span }, state.nowMinute ?: state.dayLengthMinutes)
    } else {
        emptyList()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(MinuteHeight * state.dayLengthMinutes)
            .testTag("timeline"),
    ) {
        val laneArea = maxWidth - HourRailWidth
        val plannedWidth = when (state.layer) {
            TimeLayer.Planned -> laneArea
            TimeLayer.Both -> laneArea * 0.3f
            TimeLayer.Actual -> 0.dp
        }
        val actualWidth = if (showActual) laneArea - plannedWidth - (if (showPlanned) 6.dp else 0.dp) else 0.dp

        state.hourMarks.forEach { mark ->
            Row(
                Modifier
                    .offset(y = MinuteHeight * mark.minuteOffset)
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "%02d:00".format(Locale.ROOT, mark.hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.width(HourRailWidth).offset(y = (-6).dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(colors.border))
            }
        }
        if (showActual) {
            gaps.forEach { gap ->
                Box(
                    Modifier
                        .offset(x = HourRailWidth, y = MinuteHeight * gap.start)
                        .width(actualWidth)
                        .height(MinuteHeight * gap.length)
                        .clip(PaintLifeTheme.shapes.bar)
                        .hatched(colors.hatchBase, colors.hatchLine)
                        .clearAndSetSemantics { },
                )
            }
            state.actual.forEach { block ->
                TimelineBlockView(block, planned = false, x = HourRailWidth, width = actualWidth, onClick = { onOpenEntry(block.id) })
            }
        }
        if (showPlanned) {
            state.planned.forEach { block ->
                TimelineBlockView(block, planned = true, x = maxWidth - plannedWidth, width = plannedWidth, onClick = { onOpenEntry(block.id) })
            }
        }
        state.nowMinute?.let { now ->
            Box(
                Modifier
                    .offset(x = HourRailWidth - 4.dp, y = MinuteHeight * now - 1.dp)
                    .width(laneArea + 4.dp)
                    .height(2.dp)
                    .background(colors.info)
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun TimelineBlockView(block: TimelineBlock, planned: Boolean, x: Dp, width: Dp, onClick: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val category = colors.category(block.colorKey)
    val height = maxOf(MinuteHeight * block.span.length, MinBlockHeight)
    val description = buildString {
        append(
            stringResource(
                if (planned) R.string.my_time_block_planned else R.string.my_time_block_actual,
                block.categoryName,
                block.startLabel,
                block.endLabel,
            ),
        )
        if (block.isDistraction) append(", ").append(stringResource(R.string.category_distraction))
    }
    val shape = PaintLifeTheme.shapes.bar
    Row(
        modifier = Modifier
            .offset(x = x, y = MinuteHeight * block.span.start)
            .width(width)
            .height(height)
            .padding(vertical = 1.dp)
            .clip(shape)
            .background(if (planned) colors.canvas else lerp(colors.surface, category, 0.28f))
            .then(if (planned) Modifier.border(BorderStroke(1.5.dp, category), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        if (!planned) Box(Modifier.width(4.dp).fillMaxHeight().background(category))
        Column(Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs).clearAndSetSemantics { }) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (block.isDistraction && !planned && height >= 40.dp) DistractionTag()
            }
            if (height >= 40.dp) {
                Text(
                    text = "${block.startLabel}–${block.endLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The log/edit sheet body. Times open the system time picker (wired in Iteration 6). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogActivityContent(
    state: LogActivityUiState,
    onLayerChange: (TimeLayer) -> Unit,
    onSelectCategory: (String) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("log_activity"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Text(
                text = stringResource(if (state.isEditing) R.string.my_time_edit_title else R.string.my_time_log_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item(key = "layer") {
            SegmentedPicker(
                options = listOf(TimeLayer.Actual, TimeLayer.Planned),
                selected = state.layer,
                onSelect = onLayerChange,
                label = { layerLabel(it) },
            )
        }
        item(key = "categories") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(text = stringResource(R.string.my_time_category), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
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
            }
        }
        item(key = "times") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                TimeField(stringResource(R.string.my_time_start), state.startLabel, onPickStart, Modifier.weight(1f))
                TimeField(
                    stringResource(R.string.my_time_end),
                    if (state.endsNextDay) stringResource(R.string.my_time_next_day_suffix, state.endLabel) else state.endLabel,
                    onPickEnd,
                    Modifier.weight(1f),
                )
            }
        }
        item(key = "duration") {
            Text(
                text = stringResource(R.string.my_time_duration, DurationFormat.hoursMinutes(state.durationMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryButton(stringResource(R.string.action_save), onSave, Modifier.fillMaxWidth(), enabled = state.canSave)
                if (state.isEditing) DestructiveButton(stringResource(R.string.action_delete_entry), onDelete, Modifier.fillMaxWidth())
                QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(
        modifier = modifier
            .clip(PaintLifeTheme.shapes.tile)
            .background(colors.raised)
            .border(1.dp, colors.border, PaintLifeTheme.shapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(text = value, style = PaintLifeTheme.numbers.small)
    }
}

/** Saving would overlap another actual entry. Actual entries never overlap, so the user chooses. */
@Composable
fun OverlapContent(
    state: OverlapUiState,
    onTrim: () -> Unit,
    onReplace: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("overlap"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Text(text = stringResource(R.string.overlap_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        item(key = "body") {
            Text(
                text = stringResource(R.string.overlap_body, state.newCategoryName, state.newRange, state.neighbourCategoryName, state.neighbourRange),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.trimmedNeighbourRange?.let { trimmed ->
                    PrimaryButton(stringResource(R.string.overlap_trim, state.neighbourCategoryName, trimmed), onTrim, Modifier.fillMaxWidth())
                }
                SecondaryButton(stringResource(R.string.overlap_replace, state.neighbourCategoryName), onReplace, Modifier.fillMaxWidth())
                QuietButton(stringResource(R.string.overlap_go_back), onGoBack, Modifier.fillMaxWidth())
            }
        }
    }
}
