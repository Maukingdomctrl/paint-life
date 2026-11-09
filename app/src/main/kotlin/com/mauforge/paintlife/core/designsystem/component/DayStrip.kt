package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * A span of one day in minutes from local midnight. [color] null means time that has passed
 * with nothing recorded; it is drawn hatched. Minutes not covered by any block (the rest of
 * today) stay plain.
 */
@Immutable
data class DayStripBlock(val startMinute: Int, val endMinute: Int, val color: Color?)

/** One day as a horizontal strip, e.g. "Today so far". [dayLengthMinutes] is 1380/1440/1500 on DST days. */
@Composable
fun DayStrip(
    blocks: List<DayStripBlock>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    dayLengthMinutes: Int = 1440,
    height: Dp = 18.dp,
) {
    val colors = PaintLifeTheme.colors
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
    ) {
        val radius = CornerRadius(6.dp.toPx())
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
        val gap = 1.dp.toPx()
        clipPath(clip) {
            drawRect(colors.sunken)
            blocks.forEach { block ->
                val left = size.width * block.startMinute / dayLengthMinutes
                val right = size.width * block.endMinute / dayLengthMinutes
                if (right <= left) return@forEach
                val width = (right - left - gap).coerceAtLeast(1f)
                if (block.color != null) {
                    drawRect(block.color, topLeft = Offset(left, 0f), size = Size(width, size.height))
                } else {
                    drawHatch(Offset(left, 0f), Size(width, size.height), colors.hatchBase, colors.hatchLine)
                }
            }
        }
        drawRoundRect(colors.border, cornerRadius = radius, style = Stroke(1.dp.toPx()))
    }
}

/** Diagonal hatching marks unrecorded time, so it never depends on colour alone. */
fun Modifier.hatched(base: Color, line: Color): Modifier = drawBehind {
    drawHatch(Offset.Zero, size, base, line)
}

internal fun DrawScope.drawHatch(topLeft: Offset, area: Size, base: Color, line: Color) {
    drawRect(base, topLeft = topLeft, size = area)
    val step = 6.dp.toPx()
    val stroke = 1.5.dp.toPx()
    clipRect(topLeft.x, topLeft.y, topLeft.x + area.width, topLeft.y + area.height) {
        var x = topLeft.x - area.height
        while (x < topLeft.x + area.width) {
            drawLine(line, Offset(x, topLeft.y + area.height), Offset(x + area.height, topLeft.y), strokeWidth = stroke)
            x += step
        }
    }
}
