package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifePalette
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

private val LargeButtonHeight = 56.dp

/** The one main action in a context. Green fill, white label (4.6:1). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    PaintLifeButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        container = PaintLifePalette.Green,
        content = PaintLifePalette.White,
        border = null,
        minHeight = LargeButtonHeight,
    )
}

/** A real alternative to the primary action. Raised fill, hairline border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = PaintLifeTheme.colors
    PaintLifeButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        container = colors.raised,
        content = colors.text,
        border = BorderStroke(1.dp, colors.border),
        minHeight = LargeButtonHeight,
    )
}

/** Low-emphasis action ("Not now", "Correct count"). Text only, green label. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    PaintLifeButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        container = Color.Transparent,
        content = PaintLifeTheme.colors.accent,
        border = null,
        minHeight = PaintLifeTheme.spacing.minTouchTarget,
    )
}

/**
 * Destructive action. Small, undoable deletions (one priority) act at once and offer Undo;
 * bigger ones (resetting counts, deleting history) open a confirmation whose confirming
 * button is this one too.
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val colors = PaintLifeTheme.colors
    PaintLifeButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        container = colors.dangerMuted,
        content = colors.danger,
        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
        minHeight = LargeButtonHeight,
    )
}

@Composable
private fun PaintLifeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: Painter?,
    container: Color,
    content: Color,
    border: BorderStroke?,
    minHeight: Dp,
) {
    val colors = PaintLifeTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = PaintLifeTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (container == Color.Transparent) Color.Transparent else colors.raised,
            disabledContentColor = colors.textSecondary,
        ),
        border = if (enabled) border else BorderStroke(1.dp, colors.border),
        contentPadding = PaddingValues(horizontal = PaintLifeTheme.spacing.xl, vertical = PaintLifeTheme.spacing.md),
    ) {
        if (icon != null) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(PaintLifeTheme.spacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
