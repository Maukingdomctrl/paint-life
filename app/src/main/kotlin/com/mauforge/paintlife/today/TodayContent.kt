package com.mauforge.paintlife.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.DayStrip
import com.mauforge.paintlife.core.designsystem.component.DayStripBlock
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.HeroPanel
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SectionHeader
import com.mauforge.paintlife.core.designsystem.component.Swatch
import com.mauforge.paintlife.core.designsystem.component.SwatchSegment
import com.mauforge.paintlife.core.designsystem.component.Tray
import com.mauforge.paintlife.core.designsystem.component.TrayDivider
import com.mauforge.paintlife.core.designsystem.component.paintedOrEmpty
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.format.DateTimeFormatter

/**
 * Today: "What matters today?" Date and greeting, today's three swatches, up to three
 * priorities, quick actions and a strip of the day so far. Stateless; wired in Iteration 3.
 */
@Composable
fun TodayContent(
    state: TodayUiState,
    onTogglePriority: (String) -> Unit,
    onEditPriority: (String) -> Unit,
    onAddPriority: () -> Unit,
    onStartExercise: () -> Unit,
    onStartQuiet: () -> Unit,
    onLogTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("today_content")) {
        item(key = "header") { TodayHeader(state) }
        item(key = "canvas") { TodayCanvas(state) }
        item(key = "priorities") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                SectionHeader(
                    title = stringResource(R.string.today_priorities),
                    trailing = if (state.priorities.isEmpty()) null else {
                        {
                            Text(
                                text = pluralStringResource(R.plurals.today_priorities_done, state.prioritiesDone, state.prioritiesDone, state.priorities.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PaintLifeTheme.colors.textSecondary,
                            )
                        }
                    },
                )
                Priorities(state, onTogglePriority, onEditPriority, onAddPriority)
            }
        }
        item(key = "actions") { QuickActions(onStartExercise, onStartQuiet, onLogTime) }
        item(key = "strip") { TodaySoFar(state) }
    }
}

@Composable
private fun TodayHeader(state: TodayUiState) {
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale())
    Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xxs)) {
        Text(
            text = state.date.format(formatter),
            style = MaterialTheme.typography.titleSmall,
            color = PaintLifeTheme.colors.textSecondary,
        )
        Text(
            text = stringResource(
                when (state.greeting) {
                    Greeting.Morning -> R.string.greeting_morning
                    Greeting.Afternoon -> R.string.greeting_afternoon
                    Greeting.Evening -> R.string.greeting_evening
                },
            ),
            style = MaterialTheme.typography.displaySmall,
            color = PaintLifeTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun TodayCanvas(state: TodayUiState) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    HeroPanel(Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.today_canvas), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            val exerciseValue = if (state.exerciseSessionsToday > 0) {
                pluralStringResource(R.plurals.reps, state.exerciseRepsToday, state.exerciseRepsToday)
            } else {
                stringResource(R.string.today_not_yet)
            }
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_exercise),
                value = exerciseValue,
                segments = paintedOrEmpty(state.exerciseSessionsToday > 0, colors.swatchExercise),
                modifier = Modifier.weight(1f),
            )
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_quiet),
                value = stringResource(if (state.quietCompletedToday) R.string.today_done else R.string.today_not_yet),
                segments = paintedOrEmpty(state.quietCompletedToday, colors.swatchQuiet),
                modifier = Modifier.weight(1f),
            )
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_time),
                value = if (state.timeLoggedMinutes > 0) DurationFormat.hoursMinutes(state.timeLoggedMinutes) else stringResource(R.string.today_not_yet),
                segments = state.timeByCategory.map {
                    SwatchSegment(colors.category(it.colorKey), it.minutes.toFloat() / state.dayLengthMinutes)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CanvasSwatch(label: String, value: String, segments: List<SwatchSegment>, modifier: Modifier) {
    val colors = PaintLifeTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { },
        verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm),
    ) {
        Swatch(segments, contentDescription = null, modifier = Modifier.fillMaxWidth().height(76.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Text(
                text = value,
                style = if (segments.isEmpty()) MaterialTheme.typography.titleMedium else PaintLifeTheme.numbers.small,
                color = if (segments.isEmpty()) colors.textSecondary else colors.text,
            )
        }
    }
}

@Composable
private fun Priorities(
    state: TodayUiState,
    onToggle: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    if (state.priorities.isEmpty()) {
        Tray(Modifier.fillMaxWidth()) {
            EmptyState(
                title = stringResource(R.string.today_priorities_empty_title),
                body = stringResource(R.string.today_priorities_empty_body),
                modifier = Modifier.padding(horizontal = PaintLifeTheme.spacing.lg),
                action = { SecondaryButton(text = stringResource(R.string.today_add_priority), onClick = onAdd, icon = painterResource(R.drawable.ic_add)) },
            )
        }
        return
    }
    Tray(Modifier.fillMaxWidth()) {
        state.priorities.forEachIndexed { index, priority ->
            if (index > 0) TrayDivider()
            PriorityRow(priority, onToggle = { onToggle(priority.id) }, onEdit = { onEdit(priority.id) })
        }
        if (state.canAddPriority) {
            TrayDivider()
            QuietButton(
                text = stringResource(R.string.today_add_priority),
                onClick = onAdd,
                icon = painterResource(R.drawable.ic_add),
                modifier = Modifier.padding(horizontal = PaintLifeTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun PriorityRow(priority: PriorityItem, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .toggleable(value = priority.done, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(priority.done)
            Text(
                text = priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (priority.done) colors.textSecondary else colors.text,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(R.string.today_edit_priority, priority.title),
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
internal fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(colors.accent) else Modifier.border(2.dp, colors.textSecondary, CircleShape),
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onHighlight, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(onStartExercise: () -> Unit, onStartQuiet: () -> Unit, onLogTime: () -> Unit) {
    val spacing = PaintLifeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionHeader(title = stringResource(R.string.today_quick_actions))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            maxItemsInEachRow = 3,
        ) {
            PrimaryButton(text = stringResource(R.string.action_start_exercise), onClick = onStartExercise, modifier = Modifier.weight(1f))
            SecondaryButton(text = stringResource(R.string.action_quiet_time), onClick = onStartQuiet, modifier = Modifier.weight(1f))
            SecondaryButton(text = stringResource(R.string.action_log_time), onClick = onLogTime, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodaySoFar(state: TodayUiState) {
    val colors = PaintLifeTheme.colors
    val recorded = state.strip.filter { it.colorKey != null }.sumOf { it.endMinute - it.startMinute }
    val unrecorded = state.strip.filter { it.colorKey == null }.sumOf { it.endMinute - it.startMinute }
    Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
        Text(text = stringResource(R.string.today_so_far), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        DayStrip(
            blocks = state.strip.map { DayStripBlock(it.startMinute, it.endMinute, it.colorKey?.let(colors::category)) },
            contentDescription = stringResource(
                R.string.today_strip_description,
                DurationFormat.hoursMinutes(recorded),
                DurationFormat.hoursMinutes(unrecorded),
            ),
            dayLengthMinutes = state.dayLengthMinutes,
        )
        Text(
            text = stringResource(R.string.today_strip_legend),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
