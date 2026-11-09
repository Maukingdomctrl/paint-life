package com.mauforge.paintlife.exercise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.MeterBar
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.theme.PaintLifePalette
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import java.text.NumberFormat

/**
 * The step-up counter. One very large +1 target, +5/+10/Undo below it, the clock above.
 * In a timed challenge the clock keeps running while paused, and the screen says so.
 */
@Composable
fun ExerciseSessionContent(
    state: ExerciseSessionUiState,
    onAdd: (Int) -> Unit,
    onUndo: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCorrectCount: () -> Unit,
    onReset: () -> Unit,
    onUndoFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val timed = state.type == ExerciseSessionType.TIMED_CHALLENGE && state.opponent != null
    val numberFormat = NumberFormat.getIntegerInstance()
    PaintLifeScreen(modifier = modifier.testTag("exercise_session"), verticalSpacing = spacing.lg) {
        item(key = "header") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                if (timed) CatAvatar(state.opponent.cat, state.opponent.avatarState, size = CatAvatarSize.Small)
                Text(
                    text = if (timed) {
                        stringResource(R.string.session_title_timed, stringResource(state.opponent.cat.nameRes))
                    } else {
                        stringResource(R.string.session_title_volume)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        if (state.finishing) {
            item(key = "finishing") { FinishingCard(state.elapsedMillis, onUndoFinish) }
        }
        item(key = "clock") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BigNumber(
                    value = if (state.finishing) DurationFormat.clockTenths(state.elapsedMillis) else DurationFormat.clock(state.elapsedMillis),
                    label = stringResource(if (timed) R.string.session_clock_timed else R.string.session_clock_active),
                    style = PaintLifeTheme.numbers.large,
                    color = if (state.isPaused && !timed) colors.textSecondary else colors.text,
                    horizontalAlignment = Alignment.CenterHorizontally,
                )
                if (state.isPaused) {
                    Text(
                        text = stringResource(if (timed) R.string.session_paused_timed else R.string.session_paused_volume),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.highlight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
            }
        }
        item(key = "reps") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BigNumber(
                    value = numberFormat.format(state.reps),
                    unit = pluralStringResource(R.plurals.reps_unit, state.reps),
                    style = PaintLifeTheme.numbers.hero,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (timed) {
                    MeterBar(
                        progress = state.reps.toFloat() / state.targetReps,
                        contentDescription = stringResource(R.string.session_timed_meter, numberFormat.format(state.reps), numberFormat.format(state.targetReps)),
                        modifier = Modifier.padding(top = spacing.md),
                        color = colors.highlight,
                    )
                    Text(
                        text = stringResource(
                            R.string.session_timed_remaining,
                            numberFormat.format(state.repsRemaining),
                            stringResource(state.opponent.cat.nameRes),
                            DurationFormat.clock(state.opponent.benchmarkMillis),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
            }
        }
        item(key = "counter") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
                val counting = !state.isPaused && !state.finishing
                PlusOneButton(onClick = { onAdd(1) }, enabled = counting)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    CountButton(label = "+5", description = stringResource(R.string.session_add_reps, 5), onClick = { onAdd(5) }, enabled = counting, modifier = Modifier.weight(1f))
                    CountButton(label = "+10", description = stringResource(R.string.session_add_reps, 10), onClick = { onAdd(10) }, enabled = counting, modifier = Modifier.weight(1f))
                    CountButton(
                        label = null,
                        description = stringResource(R.string.session_undo),
                        onClick = onUndo,
                        enabled = state.canUndo && !state.finishing,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (state.finishing) return@PaintLifeScreen
        item(key = "controls") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                if (state.isPaused) {
                    SecondaryButton(stringResource(R.string.action_resume), onResume, Modifier.weight(1f), icon = painterResource(R.drawable.ic_play))
                } else {
                    SecondaryButton(stringResource(R.string.action_pause), onPause, Modifier.weight(1f), icon = painterResource(R.drawable.ic_pause))
                }
                if (timed) {
                    SecondaryButton(stringResource(R.string.session_stop_challenge), onFinish, Modifier.weight(1f), icon = painterResource(R.drawable.ic_close))
                } else {
                    PrimaryButton(stringResource(R.string.action_finish), onFinish, Modifier.weight(1f), icon = painterResource(R.drawable.ic_flag))
                }
            }
        }
        item(key = "corrections") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuietButton(stringResource(R.string.session_correct_count), onCorrectCount, icon = painterResource(R.drawable.ic_edit))
                QuietButton(stringResource(R.string.session_reset), onReset, enabled = state.reps > 0)
            }
        }
    }
}

/** The 5-second window after a timed challenge reached 1,000: the time, and Undo for a mis-tap. */
@Composable
private fun FinishingCard(elapsedMillis: Long, onUndo: () -> Unit) {
    val colors = PaintLifeTheme.colors
    PaintLifeCard(Modifier.fillMaxWidth().testTag("finishing")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    text = stringResource(R.string.session_finish_window_title, DurationFormat.clockTenths(elapsedMillis)),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.highlight,
                )
                Text(
                    text = stringResource(R.string.session_finish_window_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            QuietButton(stringResource(R.string.action_undo), onUndo, icon = painterResource(R.drawable.ic_undo))
        }
    }
}

@Composable
private fun PlusOneButton(onClick: () -> Unit, enabled: Boolean) {
    val colors = PaintLifeTheme.colors
    val description = stringResource(R.string.session_add_reps, 1)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) PaintLifePalette.Green else colors.raised,
        contentColor = if (enabled) PaintLifePalette.White else colors.textSecondary,
        border = BorderStroke(1.dp, if (enabled) colors.accent else colors.border),
        modifier = Modifier
            .widthIn(min = 160.dp, max = 220.dp)
            .fillMaxWidth(0.55f)
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "+1", style = PaintLifeTheme.numbers.hero, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun CountButton(label: String?, description: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val colors = PaintLifeTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = PaintLifeTheme.shapes.tile,
        color = colors.raised,
        contentColor = if (enabled) colors.text else colors.textSecondary,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(text = label, style = PaintLifeTheme.numbers.medium, modifier = Modifier.clearAndSetSemantics { })
            } else {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Before-you-start guidance. Shown expanded until read once, then reachable from the Exercise tab. */
@Composable
fun SafetyGuidanceContent(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val tips = listOf(
        R.string.safety_tip_warm_up_title to R.string.safety_tip_warm_up,
        R.string.safety_tip_height_title to R.string.safety_tip_height,
        R.string.safety_tip_count_title to R.string.safety_tip_count,
        R.string.safety_tip_alternate_title to R.string.safety_tip_alternate,
        R.string.safety_tip_rest_title to R.string.safety_tip_rest,
    )
    PaintLifeScreen(modifier = modifier.testTag("safety_guidance"), verticalSpacing = spacing.lg) {
        item(key = "title") {
            Text(text = stringResource(R.string.safety_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        }
        tips.forEach { (title, body) ->
            item(key = title) {
                Column(Modifier.semantics(mergeDescendants = true) { }) {
                    Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                }
            }
        }
        item(key = "stop") {
            PaintLifeCard(Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.safety_stop_title), style = MaterialTheme.typography.titleLarge, color = colors.highlight)
                Text(
                    text = stringResource(R.string.safety_stop_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }
        item(key = "done") {
            PrimaryButton(text = stringResource(R.string.safety_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}
