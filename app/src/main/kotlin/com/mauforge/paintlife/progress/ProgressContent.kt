package com.mauforge.paintlife.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.SectionHeader
import com.mauforge.paintlife.core.designsystem.component.SegmentedPicker
import com.mauforge.paintlife.core.designsystem.component.SwatchCalendar
import com.mauforge.paintlife.core.designsystem.component.SwatchDay
import com.mauforge.paintlife.core.designsystem.component.SwatchSegment
import com.mauforge.paintlife.core.designsystem.component.SwatchWeek
import com.mauforge.paintlife.core.designsystem.component.paintedOrEmpty
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Progress: reports for a chosen period. Counts and totals only; no productivity score, and a
 * day without records is simply unpainted. Stateless; wired in Iteration 7.
 */
@Composable
fun ProgressContent(
    state: ProgressUiState,
    onPeriodChange: (ProgressPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("progress_content")) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(text = stringResource(R.string.title_progress), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                SegmentedPicker(
                    options = ProgressPeriod.entries,
                    selected = state.period,
                    onSelect = onPeriodChange,
                    label = { periodLabel(it) },
                )
                Text(text = rangeLabel(state, currentLocale()), style = MaterialTheme.typography.bodyMedium, color = PaintLifeTheme.colors.textSecondary)
            }
        }
        item(key = "exercise") { ExerciseSection(state.exercise) }
        item(key = "quiet") { QuietSection(state) }
        item(key = "time") { TimeSection(state.time) }
    }
}

@Composable
private fun periodLabel(period: ProgressPeriod): String = stringResource(
    when (period) {
        ProgressPeriod.Today -> R.string.progress_period_today
        ProgressPeriod.Last7Days -> R.string.progress_period_7_days
        ProgressPeriod.ThisMonth -> R.string.progress_period_this_month
        ProgressPeriod.LastMonth -> R.string.progress_period_last_month
    },
)

private fun rangeLabel(state: ProgressUiState, locale: Locale): String {
    val day = DateTimeFormatter.ofPattern("d MMMM", locale)
    return if (state.start == state.end) state.start.format(day) else "${state.start.format(day)} – ${state.end.format(day)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSection(report: ExerciseReport) {
    val spacing = PaintLifeTheme.spacing
    val numberFormat = NumberFormat.getIntegerInstance()
    PaintLifeCard(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.progress_exercise))
        if (report.sessions == 0) {
            EmptyState(
                title = stringResource(R.string.progress_exercise_empty_title),
                body = stringResource(R.string.progress_exercise_empty_body),
            )
        } else {
            BigNumber(
                value = numberFormat.format(report.totalReps),
                unit = pluralStringResource(R.plurals.step_ups_unit, report.totalReps),
                label = pluralStringResource(R.plurals.progress_in_sessions, report.sessions, report.sessions),
                style = PaintLifeTheme.numbers.large,
                modifier = Modifier.padding(top = spacing.md),
            )
            Row(Modifier.fillMaxWidth().padding(top = spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                BigNumber(
                    value = report.averageSessionMillis?.let(DurationFormat::clock) ?: "–",
                    label = stringResource(R.string.progress_average_session),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
                BigNumber(
                    value = report.bestTimedMillis?.let(DurationFormat::clock) ?: "–",
                    label = report.bestTimedAgainst?.let { stringResource(R.string.progress_best_against, stringResource(it.nameRes)) }
                        ?: stringResource(R.string.progress_no_timed),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.progress_league_beaten, report.levelsBeaten),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = spacing.lg, bottom = spacing.sm),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            report.unlockedCats.forEachIndexed { index, cat ->
                CatAvatar(
                    cat = cat,
                    state = if (index < report.levelsBeaten) CatAvatarState.Beaten else CatAvatarState.Current,
                    size = CatAvatarSize.Small,
                )
            }
        }
    }
}

@Composable
private fun QuietSection(state: ProgressUiState) {
    val report = state.quiet
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val done = stringResource(R.string.quiet_day_done)
    val notDone = stringResource(R.string.quiet_day_not_done)
    val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale())
    PaintLifeCard(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.progress_quiet))
        Row(Modifier.fillMaxWidth().padding(vertical = spacing.md), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
            BigNumber(
                value = report.completedSessions.toString(),
                unit = pluralStringResource(R.plurals.sessions_unit, report.completedSessions),
                label = stringResource(R.string.progress_quiet_completed),
                style = PaintLifeTheme.numbers.medium,
                modifier = Modifier.weight(1f),
            )
            BigNumber(
                value = report.totalMinutes.toString(),
                unit = pluralStringResource(R.plurals.minutes_unit, report.totalMinutes),
                label = stringResource(R.string.progress_quiet_minutes),
                style = PaintLifeTheme.numbers.medium,
                modifier = Modifier.weight(1f),
            )
        }
        val month = state.calendarMonth
        if (month != null) {
            SwatchCalendar(
                month = month,
                fills = report.days.filter { it.second }.associate { it.first to listOf(SwatchSegment(colors.swatchQuiet, 1f)) },
                describe = { date, painted -> "${date.format(dayFormat)}, ${if (painted) done else notDone}" },
            )
        } else if (report.days.size > 1) {
            SwatchWeek(
                days = report.days.map { SwatchDay(it.first, paintedOrEmpty(it.second, colors.swatchQuiet)) },
                describe = { day -> "${day.date.format(dayFormat)}, ${if (day.segments.isEmpty()) notDone else done}" },
            )
        }
        val daysWith = report.days.count { it.second }
        Text(
            text = pluralStringResource(R.plurals.progress_days_with_quiet, daysWith, daysWith, report.days.size),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.md),
        )
    }
}

@Composable
private fun TimeSection(report: TimeReport) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.progress_time))
        if (report.isEmpty) {
            EmptyState(title = stringResource(R.string.progress_time_empty_title), body = stringResource(R.string.progress_time_empty_body))
            return@PaintLifeCard
        }
        SubHeading(stringResource(R.string.progress_hours_per_category))
        CategoryBars(report.categories, report.unclassifiedMinutes)
        if (report.hasPlans) {
            SubHeading(stringResource(R.string.progress_planned_vs_actual))
            PlannedVsActualBars(report.categories)
        }
        if (report.days.size > 1) {
            SubHeading(stringResource(R.string.progress_daily_distribution))
            DailyDistribution(report.days)
        }
        Text(
            text = stringResource(R.string.progress_time_note),
            style = MaterialTheme.typography.bodySmall,
            color = PaintLifeTheme.colors.textSecondary,
            modifier = Modifier.padding(top = spacing.lg),
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = PaintLifeTheme.colors.textSecondary,
        modifier = Modifier.padding(top = PaintLifeTheme.spacing.lg, bottom = PaintLifeTheme.spacing.sm).semantics { heading() },
    )
}
