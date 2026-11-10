package com.mauforge.paintlife.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.DayStrip
import com.mauforge.paintlife.core.designsystem.component.DayStripBlock
import com.mauforge.paintlife.core.designsystem.component.DistractionTag
import com.mauforge.paintlife.core.designsystem.component.hatched
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.format.DateTimeFormatter

// Charts are drawn by hand: thin bars with rounded data ends anchored at the baseline, values and
// names in text colours (never the series colour), and every bar named in text so identity never
// rests on colour. Each row is one TalkBack item that reads its name and value.

/** Hours per category, longest first, plus unrecorded time as a hatched row. */
@Composable
fun CategoryBars(
    categories: List<CategoryTotal>,
    unrecordedMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val sorted = categories.filter { it.actualMinutes > 0 }.sortedByDescending { it.actualMinutes }
    val max = (sorted.maxOfOrNull { it.actualMinutes } ?: 0).coerceAtLeast(unrecordedMinutes).coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.md)) {
        sorted.forEach { category ->
            BarRow(
                name = category.name,
                value = DurationFormat.hoursMinutes(category.actualMinutes),
                fraction = category.actualMinutes.toFloat() / max,
                color = colors.category(category.colorKey),
                isDistraction = category.isDistraction,
            )
        }
        if (unrecordedMinutes > 0) {
            BarRow(
                name = stringResource(R.string.progress_unrecorded),
                value = DurationFormat.hoursMinutes(unrecordedMinutes),
                fraction = unrecordedMinutes.toFloat() / max,
                color = null,
                isDistraction = false,
            )
        }
    }
}

@Composable
private fun BarRow(name: String, value: String, fraction: Float, color: Color?, isDistraction: Boolean) {
    val colors = PaintLifeTheme.colors
    val distraction = stringResource(R.string.category_distraction)
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = if (isDistraction) "$name, $distraction, $value" else "$name, $value" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
            Text(text = name, style = MaterialTheme.typography.labelLarge, color = colors.text, modifier = Modifier.weight(1f, fill = false))
            if (isDistraction) DistractionTag()
            Text(text = value, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        val barModifier = Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(10.dp)
        if (color != null) {
            Canvas(barModifier) { drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx())) }
        } else {
            Box(barModifier.hatched(colors.hatchBase, colors.hatchLine))
        }
    }
}

/** Planned (outlined) against actual (filled) for each category that had a plan. */
@Composable
fun PlannedVsActualBars(categories: List<CategoryTotal>, modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    val planned = categories.filter { it.plannedMinutes != null }
    val max = planned.maxOfOrNull { maxOf(it.actualMinutes, it.plannedMinutes ?: 0) }?.coerceAtLeast(1) ?: 1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.md)) {
        planned.forEach { category ->
            val plan = category.plannedMinutes ?: 0
            val description = stringResource(
                R.string.progress_planned_vs_actual_row,
                category.name,
                DurationFormat.hoursMinutes(plan),
                DurationFormat.hoursMinutes(category.actualMinutes),
            )
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
                Row {
                    Text(text = category.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.progress_planned_actual_values, DurationFormat.hoursMinutes(plan), DurationFormat.hoursMinutes(category.actualMinutes)),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }
                val color = colors.category(category.colorKey)
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val radius = CornerRadius(4.dp.toPx())
                    val barHeight = 9.dp.toPx()
                    val plannedWidth = size.width * plan / max
                    val actualWidth = size.width * category.actualMinutes / max
                    if (plannedWidth > 0f) {
                        drawRoundRect(color, Offset(1f, 1f), Size(plannedWidth - 2f, barHeight - 2f), radius, style = Stroke(1.5.dp.toPx()))
                    }
                    if (actualWidth > 0f) {
                        drawRoundRect(color, Offset(0f, barHeight + 3.dp.toPx()), Size(actualWidth, barHeight), radius)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.lg), modifier = Modifier.clearAndSetSemantics { }) {
            LegendKey(outlined = true, label = stringResource(R.string.progress_legend_planned))
            LegendKey(outlined = false, label = stringResource(R.string.progress_legend_actual))
        }
    }
}

@Composable
private fun LegendKey(outlined: Boolean, label: String) {
    val colors = PaintLifeTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
        Canvas(Modifier.width(20.dp).height(9.dp)) {
            val radius = CornerRadius(3.dp.toPx())
            if (outlined) drawRoundRect(colors.textSecondary, cornerRadius = radius, style = Stroke(1.5.dp.toPx()))
            else drawRoundRect(colors.textSecondary, cornerRadius = radius)
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

/** One strip per day, labelled with the date: how each day was spread across 24 hours. */
@Composable
fun DailyDistribution(days: List<DayDistribution>, modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    val dayFormat = DateTimeFormatter.ofPattern("EEE d", currentLocale())
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
        days.forEach { day ->
            val recorded = day.blocks.filter { it.colorKey != null }.sumOf { it.endMinute - it.startMinute }
            val label = day.date.format(dayFormat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm),
                modifier = Modifier.semantics(mergeDescendants = true) { },
            ) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.width(56.dp))
                DayStrip(
                    blocks = day.blocks.map { DayStripBlock(it.startMinute, it.endMinute, it.colorKey?.let(colors::category)) },
                    contentDescription = stringResource(R.string.progress_day_recorded, label, DurationFormat.hoursMinutes(recorded)),
                    dayLengthMinutes = day.dayLengthMinutes,
                    height = 14.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
            Spacer(Modifier.width(56.dp + PaintLifeTheme.spacing.sm))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00", "06", "12", "18", "24").forEach { hour ->
                    Text(text = hour, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
            }
        }
    }
}
