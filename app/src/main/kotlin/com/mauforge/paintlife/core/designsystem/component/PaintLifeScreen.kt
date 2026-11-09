package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * Scrolling screen body used by every destination. It applies the top and side system insets as
 * list content padding (so content scrolls behind the status bar), adds the screen gutter, and
 * caps the content width on large windows. The bottom is left to the navigation bar, which
 * handles its own inset.
 */
@Composable
fun PaintLifeScreen(
    modifier: Modifier = Modifier,
    background: Color = PaintLifeTheme.colors.canvas,
    verticalSpacing: Dp = PaintLifeTheme.spacing.xl,
    content: LazyListScope.() -> Unit,
) {
    val spacing = PaintLifeTheme.spacing
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val gutter = if (maxWidth >= 600.dp) spacing.screenGutterWide else spacing.screenGutter
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = spacing.contentMaxWidth + gutter * 2 + insets.horizontal(direction)),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(direction) + gutter,
                end = insets.calculateEndPadding(direction) + gutter,
                top = insets.calculateTopPadding() + spacing.xl,
                bottom = spacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

private fun PaddingValues.horizontal(direction: LayoutDirection): Dp =
    calculateStartPadding(direction) + calculateEndPadding(direction)

/** Section title with an optional trailing element (a count, a quiet action). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PaintLifeTheme.colors.text,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) Box { trailing() }
    }
}
