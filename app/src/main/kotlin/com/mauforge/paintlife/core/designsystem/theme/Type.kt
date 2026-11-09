package com.mauforge.paintlife.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mauforge.paintlife.R

// Both families are bundled variable fonts (SIL OFL 1.1, licences in docs/licenses/), never
// downloaded at runtime.
//
// Bricolage Grotesque: headings, the greeting and cat names. Characterful, made for display sizes.
// Atkinson Hyperlegible Next: everything read and every number. Designed by the Braille Institute
// so look-alike characters (1 l I, 0 O, 5 S) stay distinct; it has tabular figures (tnum), so
// running timers don't shift sideways.

private fun bricolage(weight: Int, opticalSize: Float) = Font(
    resId = R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private fun atkinson(weight: Int) = Font(
    resId = R.font.atkinson_hyperlegible_next,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Bricolage tuned for large sizes (display). */
internal val DisplayFamily = FontFamily(bricolage(600, 48f), bricolage(700, 48f))

/** Bricolage tuned for headline sizes. */
internal val HeadlineFamily = FontFamily(bricolage(600, 24f), bricolage(700, 24f))

internal val TextFamily = FontFamily(atkinson(400), atkinson(500), atkinson(600), atkinson(800))

private const val TABULAR = "tnum"

internal val PaintLifeTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.8).sp),
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontFamily = HeadlineFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = HeadlineFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = HeadlineFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

/** Large numerical displays: ExtraBold Atkinson with tabular figures. Read via [PaintLifeTheme.numbers]. */
@Immutable
data class NumberStyles(
    val hero: TextStyle = number(64, 68, -1.2f),
    val large: TextStyle = number(44, 48, -0.6f),
    val medium: TextStyle = number(28, 32, -0.2f),
    val small: TextStyle = number(18, 22, 0f),
)

private fun number(size: Int, lineHeight: Int, tracking: Float) = TextStyle(
    fontFamily = TextFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR,
)
