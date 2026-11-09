package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalNumberStyles = staticCompositionLocalOf { NumberStyles() }
private val LocalShapeTokens = staticCompositionLocalOf { ShapeTokens() }

/**
 * Paint Life theme. Dark only in v1 (GitHub Dark / VS Code Dark inspired); there is no light
 * scheme and the system dark-mode setting is not consulted.
 */
@Composable
fun PaintLifeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalPaintLifeColors provides PaintLifeColors(),
        LocalNumberStyles provides NumberStyles(),
        LocalShapeTokens provides ShapeTokens(),
    ) {
        MaterialTheme(
            colorScheme = PaintLifeDarkColors,
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

    val colors: PaintLifeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPaintLifeColors.current

    val numbers: NumberStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalNumberStyles.current

    val shapes: ShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalShapeTokens.current
}
