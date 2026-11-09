package com.mauforge.paintlife.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.SectionHeader
import com.mauforge.paintlife.core.designsystem.component.Tray
import com.mauforge.paintlife.core.designsystem.component.TrayDivider
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Finished sessions, newest day first. Each day shows its total; each row opens the edit sheet.
 * A session sits on the day it started, even when it ran past midnight.
 */
@Composable
fun ExerciseHistoryContent(
    state: ExerciseHistoryUiState,
    onOpenSession: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaintLifeScreen(modifier = modifier.testTag("exercise_history")) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (state.days.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                )
            }
        }
        state.days.forEach { day ->
            item(key = day.date.toString()) {
                Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
                    SectionHeader(
                        title = dayLabel(day.date, state.today),
                        trailing = {
                            Text(
                                text = pluralStringResource(R.plurals.reps, day.reps, day.reps),
                                style = PaintLifeTheme.numbers.small,
                                color = PaintLifeTheme.colors.textSecondary,
                            )
                        },
                    )
                    Tray(Modifier.fillMaxWidth()) {
                        day.rows.forEachIndexed { index, row ->
                            if (index > 0) TrayDivider()
                            HistorySessionRow(row, onClick = { onOpenSession(row.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySessionRow(row: HistoryRow, onClick: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val time = timeLabel(row.startTime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.history_row_open, time), onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .testTag("history_row_${row.id}"),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = time, style = PaintLifeTheme.numbers.small, color = colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.history_row_summary,
                    pluralStringResource(R.plurals.reps, row.reps, row.reps),
                    DurationFormat.clock(row.activeMillis),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            row.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.history_today)
    today.minusDays(1) -> stringResource(R.string.history_yesterday)
    else -> date.format(
        DateTimeFormatter.ofPattern(if (date.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", currentLocale()),
    )
}

@Composable
internal fun timeLabel(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("HH:mm", currentLocale()))
