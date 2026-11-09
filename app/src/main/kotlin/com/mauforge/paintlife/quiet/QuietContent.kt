package com.mauforge.paintlife.quiet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.ProgressRing
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SwatchDay
import com.mauforge.paintlife.core.designsystem.component.SwatchWeek
import com.mauforge.paintlife.core.designsystem.component.SwatchSegment
import com.mauforge.paintlife.core.designsystem.component.rememberReducedMotion
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import java.time.format.DateTimeFormatter

/**
 * Quiet Time: its own calm full-screen look on the deepest surface, one amber ring and the
 * countdown, and nothing else competing. Stateless.
 *
 * Reaching 5:00 closes the ring and lets one soft amber bloom spread from it; with reduced
 * motion the finished state simply appears.
 */
@Composable
fun QuietContent(
    state: QuietUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    notificationsOff: Boolean = false,
    onOpenNotificationSettings: () -> Unit = {},
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("quiet_content"), background = colors.sunken, verticalSpacing = spacing.xl) {
        item(key = "title") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(R.string.title_quiet), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    text = stringResource(
                        when (state.phase) {
                            QuietPhase.Ready -> R.string.quiet_subtitle_ready
                            QuietPhase.Running -> R.string.quiet_subtitle_running
                            QuietPhase.Paused -> R.string.quiet_subtitle_paused
                            QuietPhase.Completed -> R.string.quiet_subtitle_done
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.phase == QuietPhase.Paused) colors.highlight else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    // Announces each change of phase (not every second of the countdown).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        item(key = "ring") {
            val bloom = rememberCompletionBloom(state.phase == QuietPhase.Completed)
            Box(Modifier.fillMaxWidth().completionBloom(bloom, colors.swatchQuiet), contentAlignment = Alignment.Center) {
                val remaining = DurationFormat.countdown(state.remainingMillis)
                ProgressRing(
                    progress = state.progress,
                    contentDescription = if (state.phase == QuietPhase.Completed) {
                        stringResource(R.string.quiet_ring_done)
                    } else {
                        stringResource(R.string.quiet_ring_description, remaining)
                    },
                    size = 260.dp,
                    strokeWidth = 16.dp,
                    color = colors.swatchQuiet,
                    trackColor = colors.surface,
                    // Redrawn every tick while running; eased only for jumps (pause, completion).
                    animated = state.phase != QuietPhase.Running && !rememberReducedMotion(),
                ) {
                    BigNumber(
                        value = if (state.phase == QuietPhase.Completed) DurationFormat.clock(state.totalMillis) else remaining,
                        label = stringResource(if (state.phase == QuietPhase.Completed) R.string.quiet_label_done else R.string.quiet_label_left),
                        style = PaintLifeTheme.numbers.large,
                        color = if (state.phase == QuietPhase.Paused) colors.textSecondary else colors.text,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = spacing.xxl),
                    )
                }
            }
        }
        item(key = "controls") { Controls(state.phase, onStart, onPause, onResume, onFinish, onCancel, onDone) }
        if (notificationsOff && state.phase != QuietPhase.Completed) {
            item(key = "notifications_off") { NotificationsOffNote(onOpenNotificationSettings) }
        }
        item(key = "week") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(text = stringResource(R.string.quiet_this_week), style = MaterialTheme.typography.titleMedium)
                val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale())
                val done = stringResource(R.string.quiet_day_done)
                val notDone = stringResource(R.string.quiet_day_not_done)
                val partial = state.week.associate { it.date to stringResource(R.string.quiet_day_minutes, it.minutes) }
                val byDate = state.week.associateBy { it.date }
                SwatchWeek(
                    days = state.week.map { SwatchDay(it.date, quietSegments(it, colors.swatchQuiet)) },
                    describe = { swatch ->
                        val day = byDate[swatch.date]
                        val status = when {
                            day == null || day.fill <= 0f -> notDone
                            day.completed -> done
                            else -> partial.getValue(day.date)
                        }
                        "${swatch.date.format(dayFormat)}, $status"
                    },
                    modifier = Modifier.testTag("quiet_week"),
                )
            }
        }
        item(key = "month") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                BigNumber(
                    value = state.monthMinutes.toString(),
                    unit = pluralStringResource(R.plurals.minutes_unit, state.monthMinutes),
                    label = stringResource(R.string.quiet_month_minutes),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
                BigNumber(
                    value = state.monthCompletedSessions.toString(),
                    unit = pluralStringResource(R.plurals.sessions_unit, state.monthCompletedSessions),
                    label = stringResource(R.string.quiet_month_sessions),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Controls(
    phase: QuietPhase,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = PaintLifeTheme.spacing
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        when (phase) {
            QuietPhase.Ready -> PrimaryButton(stringResource(R.string.action_start), onStart, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_play))
            QuietPhase.Running, QuietPhase.Paused -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    if (phase == QuietPhase.Running) {
                        SecondaryButton(stringResource(R.string.action_pause), onPause, Modifier.weight(1f), icon = painterResource(R.drawable.ic_pause))
                    } else {
                        PrimaryButton(stringResource(R.string.action_resume), onResume, Modifier.weight(1f), icon = painterResource(R.drawable.ic_play))
                    }
                    SecondaryButton(stringResource(R.string.action_finish), onFinish, Modifier.weight(1f), icon = painterResource(R.drawable.ic_flag))
                }
                Text(
                    text = stringResource(R.string.quiet_finish_early_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = PaintLifeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                QuietButton(stringResource(R.string.action_cancel_session), onCancel, icon = painterResource(R.drawable.ic_close))
            }
            QuietPhase.Completed -> PrimaryButton(stringResource(R.string.action_done), onDone, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_check))
        }
    }
}

/** A day's quiet swatch: full amber for a completed session, otherwise filled in proportion. */
fun quietSegments(day: QuietDay, color: Color): List<SwatchSegment> =
    if (day.fill > 0f) listOf(SwatchSegment(color, day.fill)) else emptyList()

@Composable
private fun NotificationsOffNote(onOpenSettings: () -> Unit) {
    val spacing = PaintLifeTheme.spacing
    Column(
        Modifier.fillMaxWidth().testTag("quiet_notifications_off"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.quiet_notifications_off),
            style = MaterialTheme.typography.bodySmall,
            color = PaintLifeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        QuietButton(stringResource(R.string.action_turn_on_notifications), onOpenSettings)
    }
}

/**
 * Drives one soft amber ring that spreads out and fades behind the timer when [completed]
 * becomes true. Stays at 0 (nothing drawn) with reduced motion.
 */
@Composable
private fun rememberCompletionBloom(completed: Boolean): Animatable<Float, AnimationVector1D> {
    val reducedMotion = rememberReducedMotion()
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(completed, reducedMotion) {
        bloom.snapTo(0f)
        if (completed && !reducedMotion) bloom.animateTo(1f, tween(durationMillis = 1_400, easing = FastOutSlowInEasing))
    }
    return bloom
}

/** Draws [bloom] in the draw phase, so the animation never recomposes the screen. */
private fun Modifier.completionBloom(bloom: Animatable<Float, AnimationVector1D>, color: Color): Modifier = drawBehind {
    val progress = bloom.value
    if (progress > 0f && progress < 1f) {
        drawCircle(
            color = color.copy(alpha = 0.35f * (1f - progress)),
            radius = 130.dp.toPx() * (1f + 0.3f * progress),
            style = Stroke(width = 16.dp.toPx() * (1f - progress) + 2.dp.toPx()),
        )
    }
}
