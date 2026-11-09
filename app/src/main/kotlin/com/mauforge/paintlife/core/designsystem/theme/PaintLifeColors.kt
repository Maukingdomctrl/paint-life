package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paint Life roles that Material 3 has no slot for. Read through [PaintLifeTheme.colors].
 *
 * Swatch rule, used everywhere: Exercise paints green, Quiet Time paints amber, My Time paints
 * in its category colours, a partly painted swatch is filled in proportion, and an unpainted
 * swatch is the sunken surface. Nothing is ever painted red for a missed day.
 */
@Immutable
data class PaintLifeColors(
    val canvas: Color = PaintLifePalette.Canvas,
    val surface: Color = PaintLifePalette.Surface,
    val raised: Color = PaintLifePalette.Raised,
    val sunken: Color = PaintLifePalette.Sunken,
    val border: Color = PaintLifePalette.Border,
    val text: Color = PaintLifePalette.Text,
    val textSecondary: Color = PaintLifePalette.TextSecondary,
    /** Green accent for text, rings and the "current" state. */
    val accent: Color = PaintLifePalette.GreenBright,
    val accentMuted: Color = PaintLifePalette.GreenMuted,
    /** Progress fills and highlights. */
    val highlight: Color = PaintLifePalette.Amber,
    val highlightSoft: Color = PaintLifePalette.AmberSoft,
    val highlightMuted: Color = PaintLifePalette.AmberMuted,
    /** Text or icons drawn on [highlight]. */
    val onHighlight: Color = PaintLifePalette.Canvas,
    val info: Color = PaintLifePalette.Blue,
    val danger: Color = PaintLifePalette.Red,
    val dangerMuted: Color = PaintLifePalette.RedMuted,
    val swatchExercise: Color = PaintLifePalette.GreenBright,
    val swatchQuiet: Color = PaintLifePalette.Amber,
    val swatchEmpty: Color = PaintLifePalette.Sunken,
    val swatchOutline: Color = PaintLifePalette.Border,
    /** Unrecorded time is hatched with these two, so it never depends on colour alone. */
    val hatchBase: Color = PaintLifePalette.Sunken,
    val hatchLine: Color = PaintLifePalette.Border,
) {
    fun category(colorKey: String?): Color = CategoryPalette.forKey(colorKey)
}

internal val LocalPaintLifeColors = staticCompositionLocalOf { PaintLifeColors() }
