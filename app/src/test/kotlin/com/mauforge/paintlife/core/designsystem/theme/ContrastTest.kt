package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.x contrast for the dark palette. Normal text needs 4.5:1; large text, icons, rings and
 * chart marks need 3:1. A failing pair means the colour must change, not this test.
 */
class ContrastTest {

    private val p = PaintLifePalette
    private val backgrounds = listOf("canvas" to p.Canvas, "surface" to p.Surface, "raised" to p.Raised, "sunken" to p.Sunken)

    @Test
    fun `primary and secondary text pass 4_5 on every background`() {
        backgrounds.forEach { (name, bg) ->
            assertAtLeast(4.5, p.Text, bg, "text on $name")
            assertAtLeast(4.5, p.TextSecondary, bg, "secondary text on $name")
        }
    }

    @Test
    fun `accent, highlight, info and danger text pass 4_5 on every background`() {
        backgrounds.forEach { (name, bg) ->
            assertAtLeast(4.5, p.GreenBright, bg, "green accent on $name")
            assertAtLeast(4.5, p.Amber, bg, "amber on $name")
            assertAtLeast(4.5, p.Blue, bg, "blue on $name")
            assertAtLeast(4.5, p.Red, bg, "red on $name")
        }
    }

    @Test
    fun `filled button labels pass 4_5`() {
        assertAtLeast(4.5, p.White, p.Green, "white on green button")
        assertAtLeast(4.5, p.Canvas, p.Amber, "dark text on amber")
        assertAtLeast(4.5, p.Canvas, p.AmberSoft, "dark text on soft amber")
        assertAtLeast(4.5, p.Canvas, p.GreenBright, "dark check on bright green")
        assertAtLeast(4.5, p.Canvas, p.Red, "dark text on red")
    }

    @Test
    fun `muted containers keep their tinted text readable`() {
        assertAtLeast(4.5, p.GreenBright, p.GreenMuted, "green on green-muted (nav indicator)")
        assertAtLeast(4.5, p.Amber, p.AmberMuted, "amber on amber-muted (distraction tag)")
        assertAtLeast(4.5, p.Red, p.RedMuted, "red on red-muted (destructive button)")
    }

    @Test
    fun `every material colour scheme text role passes 4_5 on its container`() {
        val s = PaintLifeDarkColors
        listOf(
            Triple(s.onPrimary, s.primary, "onPrimary"),
            Triple(s.onPrimaryContainer, s.primaryContainer, "onPrimaryContainer"),
            Triple(s.onSecondary, s.secondary, "onSecondary"),
            Triple(s.onSecondaryContainer, s.secondaryContainer, "onSecondaryContainer"),
            Triple(s.onTertiary, s.tertiary, "onTertiary"),
            Triple(s.onBackground, s.background, "onBackground"),
            Triple(s.onSurface, s.surface, "onSurface"),
            Triple(s.onSurfaceVariant, s.surfaceVariant, "onSurfaceVariant"),
            Triple(s.onError, s.error, "onError"),
            Triple(s.onErrorContainer, s.errorContainer, "onErrorContainer"),
        ).forEach { (fg, bg, name) -> assertAtLeast(4.5, fg, bg, name) }
    }

    @Test
    fun `swatch paints and state rings pass 3 to 1 against cards and the sunken surface`() {
        listOf(p.GreenBright, p.Amber, p.TextSecondary).forEach { mark ->
            assertAtLeast(3.0, mark, p.Surface, "mark ${hex(mark)} on surface")
            assertAtLeast(3.0, mark, p.Sunken, "mark ${hex(mark)} on sunken")
        }
    }

    @Test
    fun `every category colour is at least 3 to 1 against cards`() {
        CategoryPalette.colors.forEach { (key, color) ->
            assertAtLeast(3.0, color, p.Surface, "category $key")
            assertAtLeast(3.0, color, p.Canvas, "category $key on canvas")
        }
    }

    @Test
    fun `category colours keep a spread of lightness`() {
        val lightness = CategoryPalette.colors.values.map(::luminance).sorted()
        assertTrue("lightest and darkest should differ clearly", lightness.last() - lightness.first() >= 0.3)
    }

    @Test
    fun `there is one category colour per seeded colour key`() {
        val seeded = com.mauforge.paintlife.core.database.SeedData.categories.map { it.colorKey }.toSet()
        assertTrue("missing: ${seeded - CategoryPalette.colors.keys}", CategoryPalette.colors.keys.containsAll(seeded))
    }

    private fun assertAtLeast(minimum: Double, fg: Color, bg: Color, label: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$label: ${hex(fg)} on ${hex(bg)} is %.2f:1, needs %.1f:1".format(ratio, minimum), ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
}
