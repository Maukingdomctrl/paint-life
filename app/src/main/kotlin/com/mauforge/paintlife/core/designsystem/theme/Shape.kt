package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Corner radius follows hierarchy instead of one radius everywhere.
internal val PaintLifeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Paint Life shape roles. Read via [PaintLifeTheme.shapes]. */
@Immutable
data class ShapeTokens(
    /** Hero panels: the most important block on a screen. */
    val hero: Shape = RoundedCornerShape(32.dp),
    val card: Shape = RoundedCornerShape(24.dp),
    val tile: Shape = RoundedCornerShape(16.dp),
    val chip: Shape = RoundedCornerShape(12.dp),
    val swatch: Shape = RoundedCornerShape(10.dp),
    val bar: Shape = RoundedCornerShape(6.dp),
    val pill: Shape = CircleShape,
)
