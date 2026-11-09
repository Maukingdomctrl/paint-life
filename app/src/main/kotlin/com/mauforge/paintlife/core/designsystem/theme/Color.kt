package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Raw palette (dark only, GitHub Dark / VS Code Dark inspired). Screens use
 * [MaterialTheme.colorScheme] roles or [PaintLifeTheme.colors], not these values directly.
 * Every text/background pairing is checked in ContrastTest.
 */
object PaintLifePalette {
    /** App background. */
    val Canvas = Color(0xFF0D1117)

    /** Cards and sheets. */
    val Surface = Color(0xFF161B22)

    /** Raised elements on a card: selected rows, active session bar, menus. */
    val Raised = Color(0xFF1C2128)

    /** Recessed trays, unpainted swatches, Quiet Time ground. */
    val Sunken = Color(0xFF010409)

    val Border = Color(0xFF30363D)

    val Text = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B949E)

    /** Filled buttons only; white text on it is 4.6:1, the light text colour is not enough. */
    val Green = Color(0xFF238636)

    /** Green for accents, rings and text. */
    val GreenBright = Color(0xFF3FB950)
    val GreenMuted = Color(0xFF12261E)

    val Amber = Color(0xFFD29922)
    val AmberSoft = Color(0xFFBB8009)
    val AmberMuted = Color(0xFF2E2310)

    val Blue = Color(0xFF58A6FF)

    /** Errors and destructive actions only. Never used for a missed day. */
    val Red = Color(0xFFF85149)
    val RedMuted = Color(0xFF3A1A1C)

    val White = Color(0xFFFFFFFF)
}

/**
 * My Time category colours, keyed by the `colorKey` stored on each category. Muted, spread in
 * lightness, and each at least 3:1 against [PaintLifePalette.Surface] (checked in ContrastTest).
 */
object CategoryPalette {
    val colors: Map<String, Color> = mapOf(
        "night" to Color(0xFF5B65BA),
        "clay" to Color(0xFFA0694C),
        "stone" to Color(0xFF7E7A73),
        "slate" to Color(0xFF4F82BC),
        "plum" to Color(0xFF9A7FD6),
        "teal" to Color(0xFF3E9E9A),
        "amber" to Color(0xFFE37B40),
        "sage" to Color(0xFF68AC80),
        "forest" to Color(0xFF3FB950),
        "rose" to Color(0xFFE9A3B6),
        "moss" to Color(0xFFC3D27F),
    )

    /** Fallback for an unknown key, so a bad record never crashes a screen. */
    val fallback: Color = colors.getValue("stone")

    fun forKey(colorKey: String?): Color = colors[colorKey] ?: fallback
}

internal val PaintLifeDarkColors: ColorScheme = darkColorScheme(
    primary = PaintLifePalette.Green,
    onPrimary = PaintLifePalette.White,
    primaryContainer = PaintLifePalette.GreenMuted,
    onPrimaryContainer = PaintLifePalette.GreenBright,
    inversePrimary = PaintLifePalette.Green,
    secondary = PaintLifePalette.Amber,
    onSecondary = PaintLifePalette.Canvas,
    secondaryContainer = PaintLifePalette.AmberMuted,
    onSecondaryContainer = PaintLifePalette.Amber,
    tertiary = PaintLifePalette.Blue,
    onTertiary = PaintLifePalette.Canvas,
    tertiaryContainer = PaintLifePalette.Raised,
    onTertiaryContainer = PaintLifePalette.Blue,
    background = PaintLifePalette.Canvas,
    onBackground = PaintLifePalette.Text,
    surface = PaintLifePalette.Surface,
    onSurface = PaintLifePalette.Text,
    surfaceVariant = PaintLifePalette.Raised,
    onSurfaceVariant = PaintLifePalette.TextSecondary,
    surfaceTint = PaintLifePalette.Surface,
    inverseSurface = PaintLifePalette.Text,
    inverseOnSurface = PaintLifePalette.Canvas,
    surfaceDim = PaintLifePalette.Canvas,
    surfaceBright = PaintLifePalette.Raised,
    surfaceContainerLowest = PaintLifePalette.Sunken,
    surfaceContainerLow = PaintLifePalette.Canvas,
    surfaceContainer = PaintLifePalette.Surface,
    surfaceContainerHigh = PaintLifePalette.Raised,
    surfaceContainerHighest = PaintLifePalette.Raised,
    outline = PaintLifePalette.TextSecondary,
    outlineVariant = PaintLifePalette.Border,
    error = PaintLifePalette.Red,
    onError = PaintLifePalette.Canvas,
    errorContainer = PaintLifePalette.RedMuted,
    onErrorContainer = PaintLifePalette.Red,
    scrim = PaintLifePalette.Sunken,
)
