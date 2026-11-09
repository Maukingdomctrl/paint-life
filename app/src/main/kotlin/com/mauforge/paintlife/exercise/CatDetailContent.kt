package com.mauforge.paintlife.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import com.mauforge.paintlife.exercise.cats.CatProfile

/**
 * One cat: portrait, personality, benchmark and your best. Actions depend on status: the current
 * cat offers a timed challenge, a beaten cat can be replayed, a locked cat offers nothing to tap.
 */
@Composable
fun CatDetailContent(
    entry: LeagueEntry,
    /** The cat to beat first when [entry] is locked. */
    previous: CatProfile?,
    onStartTimedChallenge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val name = stringResource(entry.cat.nameRes)
    PaintLifeScreen(modifier = modifier.testTag("cat_detail"), verticalSpacing = spacing.lg) {
        item(key = "portrait") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CatAvatar(entry.cat, entry.avatarState, size = CatAvatarSize.Hero)
                Text(
                    text = stringResource(R.string.cat_level, entry.cat.level),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = spacing.lg),
                )
                Text(text = name, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                Text(text = stringResource(entry.cat.breedRes), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
            }
        }
        item(key = "personality") {
            Text(
                text = stringResource(entry.cat.personalityRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "numbers") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                BigNumber(
                    value = DurationFormat.clock(entry.benchmarkMillis),
                    label = stringResource(R.string.league_benchmark, name),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
                BigNumber(
                    value = entry.personalBestMillis?.let(DurationFormat::clock) ?: stringResource(R.string.league_no_time_yet),
                    label = stringResource(R.string.league_your_best),
                    style = PaintLifeTheme.numbers.medium,
                    color = if (entry.personalBestMillis != null) colors.text else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "status") {
            Text(
                text = when (entry.status) {
                    LevelStatus.Locked -> stringResource(R.string.cat_locked_hint, previous?.let { stringResource(it.nameRes) } ?: "")
                    LevelStatus.Current -> stringResource(R.string.cat_current_hint, name)
                    LevelStatus.Beaten -> stringResource(R.string.cat_beaten_hint, name)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        when (entry.status) {
            LevelStatus.Current -> item(key = "start") {
                PrimaryButton(stringResource(R.string.action_start_timed), onStartTimedChallenge, Modifier.fillMaxWidth())
            }
            LevelStatus.Beaten -> item(key = "replay") {
                SecondaryButton(stringResource(R.string.action_replay_level), onStartTimedChallenge, Modifier.fillMaxWidth())
            }
            LevelStatus.Locked -> Unit
        }
        item(key = "disclaimer") {
            Text(text = stringResource(R.string.league_disclaimer), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/**
 * The single celebration moment: an amber ring sweeps once around the beaten cat. With system
 * animations turned off the ring simply appears complete.
 */
@Composable
fun VictoryContent(
    state: VictoryUiState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val name = stringResource(state.beaten.cat.nameRes)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing)) }
    val margin = state.beaten.benchmarkMillis - state.yourMillis
    PaintLifeScreen(modifier = modifier.testTag("victory"), verticalSpacing = spacing.lg) {
        item(key = "ring") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp).clearAndSetSemantics { }) {
                    drawArc(
                        color = colors.highlight,
                        startAngle = -90f,
                        sweepAngle = 360f * sweep.value,
                        useCenter = false,
                        style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                CatAvatar(state.beaten.cat, CatAvatarState.Beaten, size = CatAvatarSize.Hero)
            }
        }
        item(key = "headline") {
            Text(
                text = stringResource(R.string.victory_title, name),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        item(key = "time") {
            BigNumber(
                value = DurationFormat.clock(state.yourMillis),
                label = stringResource(R.string.victory_margin, DurationFormat.clock(margin), DurationFormat.clock(state.beaten.benchmarkMillis)),
                style = PaintLifeTheme.numbers.large,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "unlocked") {
            Text(
                text = state.unlocked?.let { stringResource(R.string.victory_unlocked, stringResource(it.nameRes)) }
                    ?: stringResource(R.string.victory_champion),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "continue") {
            PrimaryButton(stringResource(R.string.action_continue), onContinue, Modifier.fillMaxWidth())
        }
    }
}
