package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

// Elevation on a dark canvas is tonal, not shadowed: sunken < canvas < surface < raised, each
// separated by a 1dp hairline. Three containers cover every screen:
//   Tray      sunken, for groups of rows (priorities, the league ladder)
//   Card      surface, the default content block
//   HeroPanel raised with a larger radius, at most one per screen

/** Default content block. Pass [onClick] only when the whole card is one action. */
@Composable
fun PaintLifeCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(PaintLifeTheme.spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaintLifeTheme.colors
    val shape = PaintLifeTheme.shapes.card
    val border = BorderStroke(1.dp, colors.border)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = colors.surface, contentColor = colors.text, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = colors.surface, contentColor = colors.text, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** The single most important block on a screen. */
@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(PaintLifeTheme.spacing.xl),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaintLifeTheme.colors
    Surface(
        modifier = modifier,
        shape = PaintLifeTheme.shapes.hero,
        color = colors.raised,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Recessed group of rows. Separate rows with [TrayDivider]. */
@Composable
fun Tray(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PaintLifeTheme.colors
    Surface(
        modifier = modifier,
        shape = PaintLifeTheme.shapes.card,
        color = colors.sunken,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(Modifier.padding(vertical = PaintLifeTheme.spacing.xs), content = content)
    }
}

@Composable
fun TrayDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PaintLifeTheme.spacing.lg)
            .height(1.dp)
            .background(PaintLifeTheme.colors.border),
    )
}
