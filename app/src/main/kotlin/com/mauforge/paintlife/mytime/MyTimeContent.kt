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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.mauforge.paintlife.core.designsystem.component.paintLifeFieldColors
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
 * own lane, and time that passed unrecorded is hatched (tap it to log that stretch). Stateless.
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
    onFillGap: (startMinute: Int, endMinute: Int) -> Unit = { _, _ -> },
    onStartNow: (QuickActivity) -> Unit = {},
    onStopNow: () -> Unit = {},
) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("my_time_content"), verticalSpacing = spacing.lg) {
        item(key = "header") { DayHeader(state, onPreviousDay, onNextDay) }
        if (state.layersAvailable) {
            item(key = "layer") {
                SegmentedPicker(
                    options = TimeLayer.entries,
                    selected = state.layer,
                    onSelect = onLayerChange,
                    label = { layerLabel(it) },
                )
            }
        }
        state.observedDays?.let { days ->
            item(key = "observation") {
                PaintLifeCard(Modifier.fillMaxWidth().testTag("observation_banner")) {
                    Text(text = stringResource(R.string.my_time_observation_title, days), style = MaterialTheme.typography.titleMedium)
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
        if (state.isToday) {
            item(key = "now_doing") { NowDoing(state, onStartNow, onStopNow) }
        }
        item(key = "log") {
            PrimaryButton(
                stringResource(if (state.isToday) R.string.action_log_since_last else R.string.action_log_activity),
                onLogActivity,
                Modifier.fillMaxWidth(),
                icon = painterResource(R.drawable.ic_add),
            )
        }
        state.shownZone?.let { zone ->
            item(key = "zone") {
                Text(
                    text = stringResource(R.string.my_time_zone_note, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = PaintLifeTheme.colors.textSecondary,
                )
            }
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
        item(key = "timeline") { Timeline(state, onOpenEntry, onFillGap) }
    }
}

/** "Now doing": the running activity with Stop, and one-tap activities to start or switch to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowDoing(state: MyTimeUiState, onStartNow: (QuickActivity) -> Unit, onStopNow: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(Modifier.fillMaxWidth().testTag("now_doing")) {
        val running = state.nowDoing
        if (running != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Box(Modifier.width(12.dp).height(12.dp).clip(PaintLifeTheme.shapes.bar).background(colors.category(running.colorKey)))
                Text(
                    text = stringResource(R.string.now_doing_running, activityLabel(running.categoryName, running.note)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.now_doing_since, running.startLabel, DurationFormat.hoursMinutes(running.elapsedMinutes.toInt())),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.sm),
            )
            SecondaryButton(stringResource(R.string.action_stop_now), onStopNow, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_pause))
        } else {
            Text(text = stringResource(R.string.now_doing_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = stringResource(if (running != null) R.string.now_doing_switch else R.string.now_doing_start),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xs),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            state.quickActivities.take(QUICK_SHOWN).forEach { activity ->
                CategoryChip(
                    name = activityLabel(activity.categoryName, activity.note),
                    color = colors.category(activity.colorKey),
                    isDistraction = false,
                    selected = false,
                    onClick = { onStartNow(activity) },
                )
            }
        }
    }
}

private const val QUICK_SHOWN = 10

/** "Work" or "Work · emails". */
internal fun activityLabel(categoryName: String, note: String?): String = if (note.isNullOrBlank()) categoryName else "$categoryName · $note"

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
private fun Timeline(state: MyTimeUiState, onOpenEntry: (String) -> Unit, onFillGap: (Int, Int) -> Unit) {
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
                val description = stringResource(R.string.my_time_gap_description, minuteLabel(state, gap.start), minuteLabel(state, gap.end))
                Box(
                    Modifier
                        .offset(x = HourRailWidth, y = MinuteHeight * gap.start)
                        .width(actualWidth)
                        .height(maxOf(MinuteHeight * gap.length, 1.dp))
                        .clip(PaintLifeTheme.shapes.bar)
                        .hatched(colors.hatchBase, colors.hatchLine)
                        .clickable(role = Role.Button, onClick = { onFillGap(gap.start, gap.end) })
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                            onClick { onFillGap(gap.start, gap.end); true }
                        }
                        .testTag("gap_${gap.start}"),
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

/** The wall-clock label of [minute] into the shown day, from its hour marks. */
private fun minuteLabel(state: MyTimeUiState, minute: Int): String {
    val mark = state.hourMarks.lastOrNull { it.minuteOffset <= minute }
    val hour = mark?.hour ?: (minute / 60)
    val minutes = minute - (mark?.minuteOffset ?: (minute / 60 * 60))
    return "%02d:%02d".format(Locale.ROOT, hour, minutes)
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
        block.note?.let { append(", ").append(it) }
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
                    text = listOfNotNull("${block.startLabel}–${block.endLabel}", block.note).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The log/edit sheet body. Times move with steppers on real elapsed time (5-minute grid), so a DST
 * day works naturally: a time that happens twice is marked "(1st)" or "(2nd)".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogActivityContent(
    state: LogActivityUiState,
    onLayerChange: (TimeLayer) -> Unit,
    onSelectCategory: (String) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onNoteChange: (String) -> Unit = {},
    onUseActivity: (QuickActivity) -> Unit = {},
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("log_activity"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Column {
                Text(
                    text = stringResource(
                        when {
                            state.confirmingRunning -> R.string.my_time_confirm_title
                            state.isEditing -> R.string.my_time_edit_title
                            else -> R.string.my_time_log_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (state.confirmingRunning) {
                    Text(
                        text = stringResource(R.string.my_time_confirm_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }
        }
        if (state.layersAvailable) {
            item(key = "layer") {
                SegmentedPicker(
                    options = listOf(TimeLayer.Actual, TimeLayer.Planned),
                    selected = state.layer,
                    onSelect = onLayerChange,
                    label = { layerLabel(it) },
                )
            }
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = stringResource(R.string.my_time_recent), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        state.recent.forEach { activity ->
                            CategoryChip(
                                name = activityLabel(activity.categoryName, activity.note),
                                color = colors.category(activity.colorKey),
                                isDistraction = false,
                                selected = activity.categoryId == state.selectedCategoryId && activity.note == state.note,
                                onClick = { onUseActivity(activity) },
                            )
                        }
                    }
                }
            }
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
        item(key = "note") {
            OutlinedTextField(
                value = state.note,
                onValueChange = { onNoteChange(it.replace('\n', ' ')) },
                label = { Text(stringResource(R.string.my_time_note)) },
                singleLine = true,
                colors = paintLifeFieldColors(),
                modifier = Modifier.fillMaxWidth().testTag("note_field"),
            )
        }
        item(key = "times") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                TimeStepper(
                    label = stringResource(R.string.my_time_start),
                    value = timeText(state.startLabel, state.startOccurrence),
                    onNudge = onNudgeStart,
                    tag = "start",
                )
                val end = timeText(state.endLabel, state.endOccurrence)
                TimeStepper(
                    label = stringResource(R.string.my_time_end),
                    value = if (state.endsNextDay) stringResource(R.string.my_time_next_day_suffix, end) else end,
                    onNudge = onNudgeEnd,
                    tag = "end",
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
                if (state.confirmingRunning) SecondaryButton(stringResource(R.string.action_discard_running), onDelete, Modifier.fillMaxWidth())
                QuietButton(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun timeText(label: String, occurrence: Int?): String = when (occurrence) {
    1 -> stringResource(R.string.my_time_first_occurrence, label)
    2 -> stringResource(R.string.my_time_second_occurrence, label)
    else -> label
}

/** A time with steppers: an hour and five minutes either way. */
@Composable
private fun TimeStepper(label: String, value: String, onNudge: (Long) -> Unit, tag: String) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PaintLifeTheme.shapes.tile)
            .background(colors.raised)
            .border(1.dp, colors.border, PaintLifeTheme.shapes.tile)
            .padding(horizontal = spacing.md, vertical = spacing.sm)
            .testTag("time_$tag"),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            StepButton("−1h", stringResource(R.string.my_time_earlier_hour, label)) { onNudge(-60) }
            StepButton("−5", stringResource(R.string.my_time_earlier_five, label)) { onNudge(-5) }
            Text(
                text = value,
                style = PaintLifeTheme.numbers.small,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label $value" },
            )
            StepButton("+5", stringResource(R.string.my_time_later_five, label)) { onNudge(5) }
            StepButton("+1h", stringResource(R.string.my_time_later_hour, label)) { onNudge(60) }
        }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = PaintLifeTheme.colors.accent)
    }
}

/**
 * Saving would overlap other actual entries, which never overlap. Lists what happens to each:
 * fitting it in trims partial neighbours, splits one it sits inside and replaces covered ones;
 * replacing removes them all. Undo follows either way.
 */
@Composable
fun OverlapContent(
    state: OverlapUiState,
    onTrim: () -> Unit,
    onReplace: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    val colors = PaintLifeTheme.colors
    PaintLifeScreen(modifier = modifier.testTag("overlap"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Text(text = stringResource(R.string.overlap_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        }
        item(key = "body") {
            Text(
                text = stringResource(R.string.overlap_body, state.newCategoryName, state.newRange),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item(key = "rows") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.neighbours.forEach { row ->
                    val outcome = when (row.outcome) {
                        OverlapOutcome.Trimmed -> stringResource(R.string.overlap_outcome_trimmed, row.kept.first())
                        OverlapOutcome.Split -> stringResource(R.string.overlap_outcome_split, row.kept[0], row.kept[1])
                        OverlapOutcome.Removed -> stringResource(R.string.overlap_outcome_removed)
                    }
                    PaintLifeCard(Modifier.fillMaxWidth()) {
                        Text(text = "${row.categoryName} · ${row.range}", style = MaterialTheme.typography.titleSmall)
                        Text(text = outcome, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                PrimaryButton(stringResource(R.string.overlap_fit), onTrim, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.overlap_replace_all), onReplace, Modifier.fillMaxWidth())
                QuietButton(stringResource(R.string.overlap_go_back), onGoBack, Modifier.fillMaxWidth())
            }
        }
    }
}
