package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp-based spacing scale. Use these instead of literal dp values in screens. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal gutter for screen content on phones. */
    val screenGutter: Dp = 20.dp,
    /** Horizontal gutter from 600dp window width. */
    val screenGutterWide: Dp = 24.dp,
    /** Content never stretches wider than this, so lines stay readable on tablets. */
    val contentMaxWidth: Dp = 640.dp,
    /** Minimum size of anything tappable. */
    val minTouchTarget: Dp = 48.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }
