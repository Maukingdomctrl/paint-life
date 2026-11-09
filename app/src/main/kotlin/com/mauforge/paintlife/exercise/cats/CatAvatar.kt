package com.mauforge.paintlife.exercise.cats

import android.content.res.Resources
import android.util.TypedValue
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.HeadlineFamily
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
    /** The cat to beat next: green ring. */
    Current,

    /** Already beaten: amber ring and a check badge. */
    Beaten,

    /** Not reachable yet: greyscale at 55% with a lock badge. */
    Locked,

    /** No league status shown (e.g. a plain mention). */
    Plain,
}

/**
 * A cat's photo in a circular crop with a state ring and badge. Every state is shown by a ring
 * or badge and spoken in words, never by colour alone.
 */
@Composable
fun CatAvatar(
    cat: CatProfile,
    state: CatAvatarState,
    modifier: Modifier = Modifier,
    size: CatAvatarSize = CatAvatarSize.Medium,
) {
    val colors = PaintLifeTheme.colors
    val resources = LocalResources.current
    val isPlaceholder = remember(cat.avatarRes, resources) { CatArt.isPlaceholder(resources, cat.avatarRes) }
    val name = stringResource(cat.nameRes)
    val description = when (state) {
        CatAvatarState.Current -> stringResource(R.string.cat_state_current, name)
        CatAvatarState.Beaten -> stringResource(R.string.cat_state_beaten, name)
        CatAvatarState.Locked -> stringResource(R.string.cat_state_locked, name)
        CatAvatarState.Plain -> name
    }
    val diameter = size.diameter
    val ringWidth = (diameter * 0.04f).coerceAtLeast(2.dp)
    val ringGap = (diameter * 0.03f).coerceAtLeast(2.dp)
    val ringColor = when (state) {
        CatAvatarState.Current -> colors.accent
        CatAvatarState.Beaten -> colors.highlight
        CatAvatarState.Locked -> colors.border
        CatAvatarState.Plain -> Color.Transparent
    }
    val greyscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(ringWidth, ringColor, CircleShape)
                .padding(ringWidth + ringGap)
                .clip(CircleShape)
                .background(colors.raised)
                .alpha(if (state == CatAvatarState.Locked) 0.55f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(cat.avatarRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = cat.photoAlignment,
                colorFilter = if (state == CatAvatarState.Locked) greyscale else null,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPlaceholder) {
                Text(
                    text = name.take(1),
                    style = TextStyle(
                        fontFamily = HeadlineFamily,
                        fontSize = (diameter.value * 0.26f).sp,
                        color = colors.textSecondary,
                    ),
                    modifier = Modifier.padding(top = diameter * 0.1f),
                )
            }
        }
        val badgeSize = (diameter * 0.3f).coerceIn(18.dp, 40.dp)
        when (state) {
            CatAvatarState.Beaten -> Badge(R.drawable.ic_check, colors.highlight, colors.onHighlight, badgeSize, Modifier.align(Alignment.BottomEnd))
            CatAvatarState.Locked -> Badge(R.drawable.ic_lock, colors.raised, colors.textSecondary, badgeSize, Modifier.align(Alignment.BottomEnd))
            CatAvatarState.Current, CatAvatarState.Plain -> Unit
        }
    }
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

object CatArt {
    /**
     * True while a cat's drawable still resolves to the vector placeholder (an .xml file) rather
     * than an imported photo (.webp).
     */
    fun isPlaceholder(resources: Resources, @DrawableRes resId: Int): Boolean {
        val value = TypedValue()
        resources.getValue(resId, value, true)
        return value.string?.toString()?.endsWith(".xml") == true
    }
}
