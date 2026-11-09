package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** One band of paint in a swatch: [fraction] of the swatch height, painted bottom up. */
@Immutable
data class SwatchSegment(val color: Color, val fraction: Float)

/**
 * A paint swatch. Segments stack from the bottom in order; a full swatch has fractions summing
 * to 1. An empty list is an unpainted swatch: the sunken surface with a hairline, never red.
 */
@Composable
fun Swatch(
    segments: List<SwatchSegment>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val semanticsModifier = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Canvas(modifier.then(semanticsModifier)) {
        val radius = CornerRadius(10.dp.toPx())
        val clip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
        }
        clipPath(clip) {
            drawRect(colors.swatchEmpty)
            var bottom = size.height
            var painted = 0f
            segments.forEach { segment ->
                val fraction = segment.fraction.coerceIn(0f, 1f - painted)
                if (fraction > 0f) {
                    val height = size.height * fraction
                    drawRect(segment.color, topLeft = Offset(0f, bottom - height), size = Size(size.width, height))
                    bottom -= height
                    painted += fraction
                }
            }
            if (painted > 0f) {
                // A darker lip along the bottom, like paint settling in a chip.
                drawRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(size.width, 4.dp.toPx()))
            }
        }
        drawRoundRect(colors.swatchOutline, cornerRadius = radius, style = Stroke(1.dp.toPx()))
    }
}

/** A full swatch in one colour, or an unpainted one. */
fun paintedOrEmpty(painted: Boolean, color: Color): List<SwatchSegment> =
    if (painted) listOf(SwatchSegment(color, 1f)) else emptyList()

/** One day in a [SwatchWeek] or [SwatchCalendar]. */
@Immutable
data class SwatchDay(val date: LocalDate, val segments: List<SwatchSegment>)

/**
 * Seven days in a row with narrow weekday labels underneath.
 * [describe] gives each day's spoken description, e.g. "Monday 9 November, quiet time done".
 */
@Composable
fun SwatchWeek(
    days: List<SwatchDay>,
    describe: (SwatchDay) -> String,
    modifier: Modifier = Modifier,
    locale: Locale = currentLocale(),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = describe(day) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Swatch(day.segments, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(0.8f))
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = PaintLifeTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = PaintLifeTheme.spacing.xs).clearAndSetSemantics { },
                )
            }
        }
    }
}

/**
 * A month of day swatches, weeks starting on the locale's first day. Days without an entry in
 * [fills] are unpainted.
 */
@Composable
fun SwatchCalendar(
    month: YearMonth,
    fills: Map<LocalDate, List<SwatchSegment>>,
    describe: (LocalDate, Boolean) -> String,
    modifier: Modifier = Modifier,
    locale: Locale = currentLocale(),
) {
    val firstDay: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weeks = cells.chunked(7)
    val spacing = PaintLifeTheme.spacing
    val colors = PaintLifeTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            (0 until 7).forEach { offset ->
                Text(
                    text = firstDay.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                (0 until 7).forEach { index ->
                    val date = week.getOrNull(index)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (date != null) {
                            val segments = fills[date].orEmpty()
                            Swatch(segments, contentDescription = describe(date, segments.isNotEmpty()), modifier = Modifier.fillMaxSize())
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (segments.isNotEmpty()) colors.onHighlight else colors.textSecondary,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = spacing.xs, bottom = spacing.xxs)
                                    .clearAndSetSemantics { },
                            )
                        }
                    }
                }
            }
        }
    }
}
