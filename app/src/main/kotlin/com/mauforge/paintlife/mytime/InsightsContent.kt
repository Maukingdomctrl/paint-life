package com.mauforge.paintlife.mytime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.LoadingState
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PaintLifeSheet
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SectionHeader
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.progress.CategoryBars
import com.mauforge.paintlife.progress.PlannedVsActualBars
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun InsightsScreen(
    onSuggestSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(
        state = state.content,
        onPeriodChange = viewModel::setPeriod,
        onOpenCategories = viewModel::openCategories,
        onSuggestSchedule = onSuggestSchedule,
        modifier = modifier,
    )
    state.categories?.let { categories ->
        PaintLifeSheet(onDismiss = viewModel::closeCategories) {
            CategoriesContent(categories, onToggle = viewModel::setDistraction, onDone = viewModel::closeCategories)
        }
    }
}

/**
 * Where time went, over complete days ending yesterday. Every chart names its categories in
 * text; there are totals only, no scores or judgements. Stateless.
 */
@Composable
fun InsightsContent(
    state: InsightsUiState,
    onPeriodChange: (InsightsPeriod) -> Unit,
    onOpenCategories: () -> Unit,
    onSuggestSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        InsightsUiState.Loading -> LoadingState(modifier.fillMaxSize())
        is InsightsUiState.Locked -> PaintLifeScreen(modifier = modifier.testTag("insights_locked")) {
            item(key = "title") { Title() }
            item(key = "locked") {
                EmptyState(
                    title = stringResource(R.string.my_time_observation_title, state.recordedDays),
                    body = stringResource(R.string.insights_locked_body),
                )
            }
        }
        is InsightsUiState.Ready -> Ready(state.report, onPeriodChange, onOpenCategories, onSuggestSchedule, modifier)
    }
}

@Composable
private fun Title() {
    Text(text = stringResource(R.string.insights_title), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Ready(
    report: InsightsReadyUi,
    onPeriodChange: (InsightsPeriod) -> Unit,
    onOpenCategories: () -> Unit,
    onSuggestSchedule: () -> Unit,
    modifier: Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val dateFormat = DateTimeFormatter.ofPattern("d MMM", currentLocale())
    PaintLifeScreen(modifier = modifier.testTag("insights"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Column {
                Title()
                Text(
                    text = stringResource(R.string.insights_range, report.from.format(dateFormat), report.to.format(dateFormat)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
        item(key = "period") {
            com.mauforge.paintlife.core.designsystem.component.SegmentedPicker(
                options = InsightsPeriod.entries,
                selected = report.period,
                onSelect = onPeriodChange,
                label = {
                    stringResource(
                        when (it) {
                            InsightsPeriod.Week -> R.string.insights_period_week
                            InsightsPeriod.FourWeeks -> R.string.insights_period_four_weeks
                        },
                    )
                },
            )
        }

        item(key = "where") {
            PaintLifeCard(Modifier.fillMaxWidth().testTag("insights_where")) {
                SectionHeader(stringResource(R.string.insights_where))
                Text(
                    text = stringResource(
                        R.string.insights_recorded_summary,
                        DurationFormat.hoursMinutes(report.recordedMinutes.toInt()),
                        DurationFormat.hoursMinutes(report.totalMinutes.toInt()),
                        DurationFormat.hoursMinutes((report.recordedMinutes / report.days.coerceAtLeast(1)).toInt()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("insights_recorded"),
                )
                if (report.categories.isEmpty()) {
                    Text(text = stringResource(R.string.insights_nothing_recorded), style = MaterialTheme.typography.bodyMedium)
                } else {
                    CategoryBars(report.categories, unrecordedMinutes = 0, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item(key = "unrecorded") {
            PaintLifeCard(Modifier.fillMaxWidth().testTag("insights_unrecorded")) {
                SectionHeader(stringResource(R.string.progress_unrecorded))
                Text(
                    text = stringResource(
                        R.string.insights_unrecorded_body,
                        DurationFormat.hoursMinutes(report.unrecordedMinutes.toInt()),
                        DurationFormat.hoursMinutes((report.unrecordedMinutes / report.days.coerceAtLeast(1)).toInt()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "distractions") {
            PaintLifeCard(Modifier.fillMaxWidth().testTag("insights_distractions")) {
                SectionHeader(stringResource(R.string.insights_distractions))
                Text(
                    text = when {
                        !report.anyFlagged -> stringResource(R.string.insights_no_flags)
                        report.distractionMinutes == 0L -> stringResource(R.string.insights_no_distraction_time)
                        else -> stringResource(R.string.insights_distraction_total, DurationFormat.hoursMinutes(report.distractionMinutes.toInt()))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("insights_distraction_total"),
                )
                if (report.distractionMinutes > 0) {
                    CategoryBars(report.distractionCategories, unrecordedMinutes = 0, modifier = Modifier.fillMaxWidth())
                    DayBars(report.distractionByDay)
                }
                QuietButton(stringResource(R.string.insights_choose_distractions), onOpenCategories, Modifier.fillMaxWidth())
            }
        }

        item(key = "planned") {
            PaintLifeCard(Modifier.fillMaxWidth().testTag("insights_planned")) {
                SectionHeader(stringResource(R.string.progress_planned_vs_actual))
                if (report.plannedDays == 0) {
                    Text(text = stringResource(R.string.insights_no_plans), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                } else {
                    Text(
                        text = pluralStringResource(R.plurals.insights_plan_days, report.days, report.plannedDays, report.days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    PlannedVsActualBars(report.planned, Modifier.fillMaxWidth())
                }
            }
        }

        item(key = "hours") {
            PaintLifeCard(Modifier.fillMaxWidth().testTag("insights_hours")) {
                SectionHeader(stringResource(R.string.insights_hours))
                Text(text = stringResource(R.string.insights_hours_body), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    report.hours.forEach { HourRow(it) }
                }
            }
        }

        item(key = "suggest") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SecondaryButton(stringResource(R.string.my_time_suggest_schedule), onSuggestSchedule, Modifier.fillMaxWidth())
                Text(text = stringResource(R.string.progress_time_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}

/** Minutes per day as labelled bars. */
@Composable
private fun DayBars(days: List<Pair<java.time.LocalDate, Long>>) {
    val colors = PaintLifeTheme.colors
    val format = DateTimeFormatter.ofPattern("EEE d", currentLocale())
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
        days.forEach { (date, minutes) ->
            val label = date.format(format)
            val value = DurationFormat.hoursMinutes(minutes.toInt())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm),
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$label, $value" },
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.width(56.dp))
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    val width = size.width * minutes / max
                    drawRoundRect(colors.sunken, cornerRadius = CornerRadius(3.dp.toPx()))
                    if (width > 0f) drawRoundRect(colors.textSecondary, size = Size(width, size.height), cornerRadius = CornerRadius(3.dp.toPx()))
                }
                Text(text = value, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
        }
    }
}

/** One hour: its label, the mix as a stacked bar, and the usual category named in text. */
@Composable
private fun HourRow(hour: HourUi) {
    val colors = PaintLifeTheme.colors
    val label = "%02d:00".format(Locale.ROOT, hour.hour)
    val text = if (hour.topName != null) {
        pluralStringResource(R.plurals.insights_hour_top, hour.days, hour.topName, hour.topDays, hour.days)
    } else {
        stringResource(R.string.insights_hour_none)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label, $text" },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.width(44.dp))
        Canvas(Modifier.width(72.dp).height(10.dp)) {
            drawRoundRect(colors.sunken, cornerRadius = CornerRadius(3.dp.toPx()))
            val capacity = maxOf(hour.capacityMinutes, hour.mix.sumOf { it.second }).coerceAtLeast(1L)
            var x = 0f
            hour.mix.forEach { (colorKey, minutes) ->
                val width = size.width * minutes / capacity
                drawRect(colors.category(colorKey), topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width
            }
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = colors.text, modifier = Modifier.weight(1f))
    }
}
