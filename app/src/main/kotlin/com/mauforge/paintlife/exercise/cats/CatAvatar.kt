package com.mauforge.paintlife.exercise.cats

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

/** Fixed avatar sizes, so a cat looks the same wherever it appears. */
enum class CatAvatarSize(val diameter: Dp) {
    /** Inline mentions, unlocked-cat rows in Progress. */
    Small(40.dp),

    /** League ladder rows. */
    Medium(56.dp),

    /** Cat detail, session header. */
    Large(96.dp),

    /** Current opponent, victory. */
    Hero(160.dp),
}

enum class CatAvatarState {
    /** The cat to beat next: a soft glow around the picture. */
    Current,

    /** Already beaten: an amber check badge. */
    Beaten,

    /** Not reachable yet: greyscale at 55% with a lock badge. */
    Locked,

    /** No league status shown (e.g. a plain mention). */
    Plain,
}

/**
 * A cat picture in a circular crop. The pictures carry their own gold ring, so the app draws
 * none: state is a glow or a badge, and is always spoken in words, never colour alone.
 */
@Composable
fun CatAvatar(
    cat: CatProfile,
    state: CatAvatarState,
    modifier: Modifier = Modifier,
    size: CatAvatarSize = CatAvatarSize.Medium,
) {
    val colors = PaintLifeTheme.colors
    val name = stringResource(cat.nameRes)
    val description = when (state) {
        CatAvatarState.Current -> stringResource(R.string.cat_state_current, name)
        CatAvatarState.Beaten -> stringResource(R.string.cat_state_beaten, name)
        CatAvatarState.Locked -> stringResource(R.string.cat_state_locked, name)
        CatAvatarState.Plain -> name
    }
    val diameter = size.diameter
    val glowColor = colors.highlight
    val greyscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = description }
            .then(if (state == CatAvatarState.Current) Modifier.softGlow(glowColor) else Modifier),
    ) {
        Image(
            painter = painterResource(cat.avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = cat.photoAlignment,
            colorFilter = if (state == CatAvatarState.Locked) greyscale else null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.raised)
                .alpha(if (state == CatAvatarState.Locked) 0.55f else 1f),
        )
        val badgeSize = (diameter * 0.3f).coerceIn(18.dp, 40.dp)
        when (state) {
            CatAvatarState.Beaten -> Badge(R.drawable.ic_check, colors.highlight, colors.onHighlight, badgeSize, Modifier.align(Alignment.BottomEnd))
            CatAvatarState.Locked -> Badge(R.drawable.ic_lock, colors.raised, colors.textSecondary, badgeSize, Modifier.align(Alignment.BottomEnd))
            CatAvatarState.Current, CatAvatarState.Plain -> Unit
        }
    }
}

/** A halo that fades out just past the edge of the picture. Drawn outside the bounds; takes no space. */
private fun Modifier.softGlow(color: Color): Modifier = drawBehind {
    val pictureRadius = size.minDimension / 2f
    val glowRadius = pictureRadius * 1.16f
    val edge = pictureRadius / glowRadius
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.5f),
            edge to color.copy(alpha = 0.5f),
            1f to Color.Transparent,
            center = center,
            radius = glowRadius,
        ),
        radius = glowRadius,
    )
}

@Composable
private fun Badge(@DrawableRes icon: Int, background: Color, tint: Color, size: Dp, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, PaintLifeTheme.colors.canvas, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
