package com.mauforge.paintlife.progress

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mauforge.paintlife.R
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
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Progress: reports for a chosen period. Counts and totals only; no productivity score, no
 * percentages, and a day without records is simply unpainted. Stateless.
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
                Text(
                    text = rangeLabel(state, currentLocale()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaintLifeTheme.colors.textSecondary,
                    modifier = Modifier.testTag("progress_range"),
                )
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

private fun shortRange(start: LocalDate, end: LocalDate, locale: Locale): String {
    val day = DateTimeFormatter.ofPattern("d MMM", locale)
    return if (start == end) start.format(day) else "${start.format(day)} – ${end.format(day)}"
}

/** A small pill naming an all-time record. Text, so it never rests on colour. */
@Composable
private fun RecordTag(modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    Text(
        text = stringResource(R.string.progress_personal_best),
        style = MaterialTheme.typography.labelSmall,
        color = colors.accent,
        modifier = modifier
            .clip(PaintLifeTheme.shapes.pill)
            .background(colors.accentMuted)
            .padding(horizontal = PaintLifeTheme.spacing.sm, vertical = PaintLifeTheme.spacing.xxs),
    )
}

@Composable
private fun Stat(value: String, label: String, isRecord: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
        BigNumber(value = value, label = label, style = PaintLifeTheme.numbers.medium)
        if (isRecord) RecordTag()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseSection(report: ExerciseReport) {
    val spacing = PaintLifeTheme.spacing
    val numberFormat = NumberFormat.getIntegerInstance(currentLocale())
    PaintLifeCard(Modifier.fillMaxWidth().testTag("progress_exercise")) {
        SectionHeader(title = stringResource(R.string.progress_exercise))
        if (report.isEmpty) {
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
                Stat(
                    value = report.averageSessionMillis?.let(DurationFormat::clock) ?: "–",
                    label = stringResource(R.string.progress_average_session),
                    isRecord = false,
                    modifier = Modifier.weight(1f),
                )
                Stat(
                    value = report.mostReps?.let(numberFormat::format) ?: "–",
                    label = stringResource(R.string.progress_most_in_session),
                    isRecord = report.mostRepsIsRecord,
                    modifier = Modifier.weight(1f),
                )
            }
            Stat(
                value = report.bestTimedMillis?.let(DurationFormat::clock) ?: "–",
                label = report.bestTimedAgainst?.let { stringResource(R.string.progress_best_against, stringResource(it.nameRes)) }
                    ?: stringResource(R.string.progress_no_timed),
                isRecord = report.bestTimedIsRecord,
                modifier = Modifier.padding(top = spacing.lg),
            )
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
        if (report.beatenInPeriod.isNotEmpty()) {
            val names = report.beatenInPeriod.map { stringResource(it.nameRes) }.joinToString(", ")
            Text(
                text = stringResource(R.string.progress_beaten_in_period, names),
                style = MaterialTheme.typography.bodyMedium,
                color = PaintLifeTheme.colors.textSecondary,
                modifier = Modifier.padding(top = spacing.sm),
            )
        }
    }
}

@Composable
private fun QuietSection(state: ProgressUiState) {
    val report = state.quiet
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val locale = currentLocale()
    PaintLifeCard(Modifier.fillMaxWidth().testTag("progress_quiet")) {
        SectionHeader(title = stringResource(R.string.progress_quiet))
        if (report.isEmpty) {
            EmptyState(title = stringResource(R.string.progress_quiet_empty_title), body = stringResource(R.string.progress_quiet_empty_body))
            return@PaintLifeCard
        }
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
        val done = stringResource(R.string.quiet_day_done)
        val notDone = stringResource(R.string.quiet_day_not_done)
        val partFormat = stringResource(R.string.progress_quiet_day_minutes)
        val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
        val byDate = report.days.associateBy { it.date }
        fun describe(date: LocalDate): String {
            val day = byDate[date]
            val status = when {
                day == null -> notDone
                day.completed -> done
                day.minutes > 0 -> partFormat.format(day.minutes)
                else -> notDone
            }
            return "${date.format(dayFormat)}, $status"
        }
        fun segments(day: QuietDayFill): List<SwatchSegment> =
            if (day.fill > 0f) listOf(SwatchSegment(colors.swatchQuiet, day.fill)) else emptyList()

        val month = state.calendarMonth
        if (month != null) {
            SwatchCalendar(
                month = month,
                fills = report.days.filter { it.fill > 0f }.associate { it.date to segments(it) },
                describe = { date, _ -> describe(date) },
            )
        } else if (report.days.size > 1) {
            SwatchWeek(
                days = report.days.map { SwatchDay(it.date, segments(it)) },
                describe = { day -> describe(day.date) },
            )
        }
        if (state.period != ProgressPeriod.Today && report.countedDays > 0) {
            Text(
                text = pluralStringResource(R.plurals.progress_days_with_quiet, report.countedDays, report.daysWithFull, report.countedDays),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.md).testTag("progress_quiet_consistency"),
            )
        }
        if (report.weeks.isNotEmpty()) {
            Column(Modifier.padding(top = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                report.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = shortRange(week.start, week.end, locale),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = pluralStringResource(R.plurals.progress_week_days, week.countedDays, week.daysWithFull, week.countedDays),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSection(section: TimeSection) {
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(Modifier.fillMaxWidth().testTag("progress_time")) {
        SectionHeader(title = stringResource(R.string.progress_time))
        val report = when (section) {
            is TimeSection.Locked -> {
                EmptyState(
                    title = stringResource(R.string.my_time_observation_title, section.recordedDays),
                    body = stringResource(R.string.progress_time_locked_body),
                    modifier = Modifier.testTag("progress_time_locked"),
                )
                return@PaintLifeCard
            }
            is TimeSection.Ready -> section.report
        }
        if (report.isEmpty) {
            EmptyState(title = stringResource(R.string.progress_time_empty_title), body = stringResource(R.string.progress_time_empty_body))
            return@PaintLifeCard
        }
        SubHeading(stringResource(R.string.progress_hours_per_category))
        CategoryBars(report.categories, report.unrecordedMinutes)
        if (report.hasPlans) {
            SubHeading(stringResource(R.string.progress_planned_vs_actual))
            PlannedVsActualBars(report.planned)
            Text(
                text = pluralStringResource(R.plurals.progress_planned_days, report.plannedDays, report.plannedDays),
                style = MaterialTheme.typography.bodySmall,
                color = PaintLifeTheme.colors.textSecondary,
                modifier = Modifier.padding(top = spacing.sm),
            )
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
