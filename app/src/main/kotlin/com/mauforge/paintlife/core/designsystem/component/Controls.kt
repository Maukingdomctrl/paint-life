package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/** A small set of mutually exclusive options (report period, timeline layer). */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PaintLifeTheme.shapes.pill)
            .background(colors.sunken)
            .border(1.dp, colors.border, PaintLifeTheme.shapes.pill)
            .padding(spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = spacing.minTouchTarget)
                    .clip(PaintLifeTheme.shapes.pill)
                    .background(if (isSelected) colors.raised else Color.Transparent)
                    .then(if (isSelected) Modifier.border(1.dp, colors.border, PaintLifeTheme.shapes.pill) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.text else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A My Time category: colour chip, name, and a "Distraction" tag when the category carries the
 * distraction flag, so the flag never depends on colour. Selectable when [onClick] is given.
 */
@Composable
fun CategoryChip(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    isDistraction: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.border)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(start = spacing.sm, end = spacing.md, top = spacing.sm, bottom = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(20.dp).clip(PaintLifeTheme.shapes.bar).background(color))
            Text(text = name, style = MaterialTheme.typography.labelLarge, color = colors.text)
            if (isDistraction) DistractionTag()
        }
    }
    if (onClick == null) {
        Surface(modifier = modifier, shape = PaintLifeTheme.shapes.chip, color = colors.surface, border = border, content = content)
    } else {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = modifier.heightIn(min = spacing.minTouchTarget),
            shape = PaintLifeTheme.shapes.chip,
            color = if (selected) colors.raised else colors.surface,
            border = border,
            content = content,
        )
    }
}

@Composable
fun DistractionTag(modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    Text(
        text = stringResource(R.string.category_distraction),
        style = MaterialTheme.typography.labelSmall,
        color = colors.highlight,
        modifier = modifier
            .clip(PaintLifeTheme.shapes.pill)
            .background(colors.highlightMuted)
            .padding(horizontal = PaintLifeTheme.spacing.sm, vertical = PaintLifeTheme.spacing.xxs),
    )
}
