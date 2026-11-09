package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * Circular progress with a track and rounded ends. Only displays [progress]; the value comes
 * from a timestamp-based source, never from this composable's own timing.
 *
 * [contentDescription] is read instead of the centre content (e.g. "3 of 5 minutes").
 * [animated] eases discrete jumps; turn it off when [progress] is redrawn many times a second (a
 * running countdown), where an eased change would never settle.
 */
@Composable
fun ProgressRing(
    progress: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 14.dp,
    color: Color = PaintLifeTheme.colors.highlight,
    trackColor: Color = PaintLifeTheme.colors.raised,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val clamped = progress.coerceIn(0f, 1f)
    val shown by animateFloatAsState(clamped, animationSpec = if (animated) tween(durationMillis = 300) else snap(), label = "ring")
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (shown > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * shown,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Linear meter with an optional goal marker (a fraction of the track). */
@Composable
fun MeterBar(
    progress: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    color: Color = PaintLifeTheme.colors.accent,
    trackColor: Color = PaintLifeTheme.colors.sunken,
    height: Dp = 12.dp,
    goalMarker: Float? = null,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val markerColor = PaintLifeTheme.colors.text
    val borderColor = PaintLifeTheme.colors.border
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(trackColor, cornerRadius = radius)
        drawRoundRect(borderColor, cornerRadius = radius, style = Stroke(1.dp.toPx()))
        if (clamped > 0f) {
            val width = (size.width * clamped).coerceAtLeast(size.height)
            drawRoundRect(color, size = Size(width, size.height), cornerRadius = radius)
        }
        goalMarker?.let { goal ->
            val x = size.width * goal.coerceIn(0f, 1f)
            drawLine(markerColor, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }
}
