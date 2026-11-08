package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Paint Life theme. Light only for now: whether a dark theme can match the
 * primary design is decided in Iteration 2.
 */
@Composable
fun PaintLifeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = PaintLifeLightColors,
            typography = PaintLifeTypography,
            shapes = PaintLifeShapes,
            content = content,
        )
    }
}

object PaintLifeTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}
