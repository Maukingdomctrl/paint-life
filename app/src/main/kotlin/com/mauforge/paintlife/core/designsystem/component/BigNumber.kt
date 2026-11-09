package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * A large number with an optional unit beside it and a label below, read as one phrase
 * ("640 reps, lifetime step-ups"). The number shrinks to fit instead of wrapping or clipping,
 * which keeps it whole at large font scales.
 */
@Composable
fun BigNumber(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    style: TextStyle = PaintLifeTheme.numbers.large,
    color: Color = PaintLifeTheme.colors.text,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val colors = PaintLifeTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { },
        horizontalAlignment = horizontalAlignment,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.sm)) {
            BasicText(
                text = value,
                style = style.copy(color = color),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize),
                modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}
