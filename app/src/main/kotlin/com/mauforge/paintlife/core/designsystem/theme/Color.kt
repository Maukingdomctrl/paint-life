package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Raw palette. Screens use [MaterialTheme.colorScheme] roles, not these values directly. */
object PaintLifePalette {
    val Forest900 = Color(0xFF12301F)
    val Forest700 = Color(0xFF1F4D3A)
    val Forest500 = Color(0xFF3C7259)
    val Forest100 = Color(0xFFD7E8DD)

    val Amber700 = Color(0xFFB0741A)
    val Amber500 = Color(0xFFE3A23B)
    val Amber100 = Color(0xFFFBE9C8)

    val Cream = Color(0xFFFFF8EE)
    val Surface = Color(0xFFFFFCF6)
    val SurfaceSunken = Color(0xFFF4ECDD)
    val Neutral300 = Color(0xFFD9D0BF)
    val Neutral600 = Color(0xFF6B6559)
    val Ink = Color(0xFF23271F)

    val Clay = Color(0xFFB4483A)
    val Clay100 = Color(0xFFF6DAD4)
}

internal val PaintLifeLightColors: ColorScheme = lightColorScheme(
    primary = PaintLifePalette.Forest700,
    onPrimary = PaintLifePalette.Cream,
    primaryContainer = PaintLifePalette.Forest100,
    onPrimaryContainer = PaintLifePalette.Forest900,
    secondary = PaintLifePalette.Amber700,
    onSecondary = Color.White,
    secondaryContainer = PaintLifePalette.Amber100,
    onSecondaryContainer = PaintLifePalette.Forest900,
    tertiary = PaintLifePalette.Forest500,
    onTertiary = Color.White,
    background = PaintLifePalette.Cream,
    onBackground = PaintLifePalette.Ink,
    surface = PaintLifePalette.Surface,
    onSurface = PaintLifePalette.Ink,
    surfaceVariant = PaintLifePalette.SurfaceSunken,
    onSurfaceVariant = PaintLifePalette.Neutral600,
    surfaceContainerLowest = PaintLifePalette.Surface,
    surfaceContainerLow = PaintLifePalette.Surface,
    surfaceContainer = PaintLifePalette.SurfaceSunken,
    surfaceContainerHigh = PaintLifePalette.SurfaceSunken,
    surfaceContainerHighest = PaintLifePalette.Neutral300,
    outline = PaintLifePalette.Neutral300,
    outlineVariant = PaintLifePalette.Neutral300,
    error = PaintLifePalette.Clay,
    onError = Color.White,
    errorContainer = PaintLifePalette.Clay100,
    onErrorContainer = PaintLifePalette.Ink,
)
