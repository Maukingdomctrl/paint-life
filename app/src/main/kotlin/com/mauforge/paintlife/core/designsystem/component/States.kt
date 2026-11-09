package com.mauforge.paintlife.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/**
 * Nothing recorded yet. An unpainted swatch, one plain sentence and, when there is something to
 * do, one real action in [action]. Never shame, never a dead button.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = PaintLifeTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Swatch(segments = emptyList(), contentDescription = null, modifier = Modifier.size(width = 48.dp, height = 60.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = PaintLifeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) action()
    }
}

/** Calm loading indicator: a slowly breathing ring, no shimmer. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.state_loading),
) {
    val colors = PaintLifeTheme.colors
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PaintLifeTheme.spacing.xl)
            .clearAndSetSemantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.md),
    ) {
        Canvas(Modifier.size(40.dp)) {
            drawCircle(colors.accent.copy(alpha = alpha), style = Stroke(4.dp.toPx()))
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

/**
 * Something failed. Says what went wrong and what to do about it, in the interface's voice.
 * [onRetry] is offered only when retrying can actually help.
 */
@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val spacing = PaintLifeTheme.spacing
    val colors = PaintLifeTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.danger, modifier = Modifier.size(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = body, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            SecondaryButton(
                text = stringResource(R.string.action_try_again),
                onClick = onRetry,
                icon = painterResource(R.drawable.ic_refresh),
            )
        }
    }
}
