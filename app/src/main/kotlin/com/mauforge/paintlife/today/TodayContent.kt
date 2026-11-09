package com.mauforge.paintlife.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
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
import com.mauforge.paintlife.core.time.DayPart
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Today: "What matters today?" Date and greeting, today's three swatches, up to three
 * priorities (with the offer to bring unfinished ones from an earlier day), and, once their
 * features exist, quick actions and a strip of the day so far. Stateless.
 */
@Composable
fun TodayContent(
    state: TodayUiState,
    onSetCompleted: (id: String, completed: Boolean) -> Unit,
    onEditPriority: (String) -> Unit,
    onAddPriority: () -> Unit,
    onReviewCarryOver: () -> Unit,
    onDismissCarryOver: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
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
                state.carryOver?.let { CarryOverCard(it, state.date, onReviewCarryOver, onDismissCarryOver) }
                Priorities(state, onSetCompleted, onEditPriority, onAddPriority)
            }
        }
        if (state.quickActions.isNotEmpty()) {
            item(key = "actions") { QuickActions(state.quickActions, onQuickAction) }
        }
        state.strip?.let { strip -> item(key = "strip") { TodaySoFar(strip, state.dayLengthMinutes) } }
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
                when (state.dayPart) {
                    DayPart.Morning -> R.string.greeting_morning
                    DayPart.Afternoon -> R.string.greeting_afternoon
                    DayPart.Evening -> R.string.greeting_evening
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
            val notTracked = stringResource(R.string.today_not_tracked)
            val exercise = state.exercise
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_exercise),
                value = when {
                    exercise == null -> notTracked
                    exercise.sessions > 0 -> pluralStringResource(R.plurals.reps, exercise.reps, exercise.reps)
                    else -> stringResource(R.string.today_not_yet)
                },
                segments = paintedOrEmpty(exercise != null && exercise.sessions > 0, colors.swatchExercise),
                modifier = Modifier.weight(1f).testTag("swatch_exercise"),
            )
            val quiet = state.quietCompletedToday
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_quiet),
                value = when (quiet) {
                    null -> notTracked
                    true -> stringResource(R.string.today_done)
                    false -> stringResource(R.string.today_not_yet)
                },
                segments = paintedOrEmpty(quiet == true, colors.swatchQuiet),
                modifier = Modifier.weight(1f).testTag("swatch_quiet"),
            )
            val time = state.timeByCategory
            CanvasSwatch(
                label = stringResource(R.string.today_swatch_time),
                value = when {
                    time == null -> notTracked
                    state.timeLoggedMinutes > 0 -> DurationFormat.hoursMinutes(state.timeLoggedMinutes)
                    else -> stringResource(R.string.today_not_yet)
                },
                segments = time.orEmpty().map {
                    SwatchSegment(colors.category(it.colorKey), it.minutes.toFloat() / state.dayLengthMinutes)
                },
                modifier = Modifier.weight(1f).testTag("swatch_time"),
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
    onSetCompleted: (String, Boolean) -> Unit,
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
            PriorityRow(priority, onSetCompleted = { onSetCompleted(priority.id, it) }, onEdit = { onEdit(priority.id) })
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
private fun PriorityRow(priority: PriorityItem, onSetCompleted: (Boolean) -> Unit, onEdit: () -> Unit) {
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
                .toggleable(value = priority.done, role = Role.Checkbox, onValueChange = onSetCompleted)
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(priority.done)
            Text(
                text = priority.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (priority.done) colors.textSecondary else colors.text,
                modifier = Modifier.weight(1f),
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
private fun QuickActions(actions: List<QuickAction>, onAction: (QuickAction) -> Unit) {
    val spacing = PaintLifeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        SectionHeader(title = stringResource(R.string.today_quick_actions))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            maxItemsInEachRow = 3,
        ) {
            actions.forEachIndexed { index, action ->
                val text = stringResource(
                    when (action) {
                        QuickAction.StartExercise -> R.string.action_start_exercise
                        QuickAction.QuietTime -> R.string.action_quiet_time
                        QuickAction.LogTime -> R.string.action_log_time
                    },
                )
                if (index == 0) {
                    PrimaryButton(text = text, onClick = { onAction(action) }, modifier = Modifier.weight(1f))
                } else {
                    SecondaryButton(text = text, onClick = { onAction(action) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The quiet offer to copy unfinished priorities from an earlier day. Neutral wording, no red. */
@Composable
private fun CarryOverCard(offer: CarryOverUi, today: LocalDate, onReview: () -> Unit, onDismiss: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(Modifier.fillMaxWidth().testTag("carry_over_offer"), contentPadding = PaddingValues(spacing.lg)) {
        Text(
            text = if (offer.sourceIsYesterday) {
                stringResource(R.string.today_carry_title_yesterday)
            } else {
                stringResource(R.string.today_carry_title_day, offer.sourceDate.format(dayFormatter(offer.sourceDate, today)))
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
        )
        Text(
            text = pluralStringResource(R.plurals.today_carry_body, offer.items.size, offer.items.size),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.xs),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryButton(text = stringResource(R.string.today_carry_choose), onClick = onReview, modifier = Modifier.weight(1f))
            QuietButton(text = stringResource(R.string.action_not_now), onClick = onDismiss, modifier = Modifier.weight(1f))
        }
    }
}

/** Weekday name within the last week, otherwise the weekday, day and month. */
@Composable
private fun dayFormatter(date: LocalDate, today: LocalDate): DateTimeFormatter {
    val pattern = if (date.isAfter(today.minusDays(7))) "EEEE" else "EEEE d MMMM"
    return DateTimeFormatter.ofPattern(pattern, currentLocale())
}

@Composable
private fun TodaySoFar(strip: List<TodayStripBlock>, dayLengthMinutes: Int) {
    val colors = PaintLifeTheme.colors
    val recorded = strip.filter { it.colorKey != null }.sumOf { it.endMinute - it.startMinute }
    val unrecorded = strip.filter { it.colorKey == null }.sumOf { it.endMinute - it.startMinute }
    Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
        Text(text = stringResource(R.string.today_so_far), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
        DayStrip(
            blocks = strip.map { DayStripBlock(it.startMinute, it.endMinute, it.colorKey?.let(colors::category)) },
            contentDescription = stringResource(
                R.string.today_strip_description,
                DurationFormat.hoursMinutes(recorded),
                DurationFormat.hoursMinutes(unrecorded),
            ),
            dayLengthMinutes = dayLengthMinutes,
        )
        Text(
            text = stringResource(R.string.today_strip_legend),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
