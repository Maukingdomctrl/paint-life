package com.mauforge.paintlife.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.HeroPanel
import com.mauforge.paintlife.core.designsystem.component.MeterBar
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SectionHeader
import com.mauforge.paintlife.core.designsystem.component.Tray
import com.mauforge.paintlife.core.designsystem.component.TrayDivider
import com.mauforge.paintlife.core.designsystem.component.currentLocale
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

/**
 * Exercise tab: the volume goal, an open session if there is one, safety guidance, and the
 * Paint Life Cat League. Timed challenges are always optional. Stateless.
 */
@Composable
fun ExerciseContent(
    state: ExerciseUiState,
    onStartSession: () -> Unit,
    onResumeSession: () -> Unit,
    onStartTimedChallenge: (level: Int) -> Unit,
    onOpenCat: (level: Int) -> Unit,
    onOpenSafety: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaintLifeScreen(modifier = modifier.testTag("exercise_content")) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.title_exercise),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        state.activeSession?.let { session ->
            item(key = "active") { ActiveSessionCard(session, onResumeSession) }
        }
        item(key = "volume") { VolumePanel(state, showStart = state.activeSession == null, onStartSession = onStartSession) }
        item(key = "safety") { SafetyCard(expanded = !state.hasReadSafety, onOpenSafety = onOpenSafety) }
        item(key = "history") {
            QuietButton(text = stringResource(R.string.history_open), onClick = onOpenHistory, icon = painterResource(R.drawable.ic_chevron_right))
        }
        item(key = "league_title") {
            SectionHeader(
                title = stringResource(R.string.league_title),
                trailing = {
                    Text(
                        text = pluralStringResource(R.plurals.league_beaten_count, state.levelsBeaten, state.levelsBeaten, state.league.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaintLifeTheme.colors.textSecondary,
                    )
                },
            )
        }
        val current = state.currentOpponent
        item(key = "opponent") {
            if (current != null) {
                CurrentOpponent(current, canStart = state.activeSession == null, onStart = { onStartTimedChallenge(current.cat.level) })
            } else {
                AllBeatenCard()
            }
        }
        item(key = "ladder") { Ladder(state.league, onOpenCat) }
        item(key = "disclaimer") {
            Text(
                text = stringResource(R.string.league_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = PaintLifeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ActiveSessionCard(session: ActiveSessionSummary, onResume: () -> Unit) {
    val colors = PaintLifeTheme.colors
    PaintLifeCard(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (session.isPaused) R.string.session_open_paused else R.string.session_open_running),
            style = MaterialTheme.typography.titleMedium,
            color = colors.accent,
        )
        Text(
            text = stringResource(
                R.string.session_open_summary,
                pluralStringResource(R.plurals.reps, session.reps, session.reps),
                DurationFormat.clock(session.activeMillis),
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = PaintLifeTheme.spacing.xs),
        )
        session.startedOn?.let { date ->
            Text(
                text = stringResource(R.string.session_open_started, date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", currentLocale()))),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = PaintLifeTheme.spacing.xxs),
            )
        }
        Spacer(Modifier.height(PaintLifeTheme.spacing.lg))
        PrimaryButton(text = stringResource(R.string.action_resume_session), onClick = onResume, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VolumePanel(state: ExerciseUiState, showStart: Boolean, onStartSession: () -> Unit) {
    val spacing = PaintLifeTheme.spacing
    val numberFormat = NumberFormat.getIntegerInstance()
    val reached = state.lifetimeReps >= state.volumeGoal
    HeroPanel(Modifier.fillMaxWidth()) {
        BigNumber(
            value = numberFormat.format(state.lifetimeReps),
            unit = pluralStringResource(R.plurals.step_ups_unit, state.lifetimeReps),
            label = stringResource(R.string.volume_lifetime),
            style = PaintLifeTheme.numbers.hero,
        )
        MeterBar(
            progress = state.lifetimeReps.toFloat() / state.volumeGoal,
            contentDescription = stringResource(R.string.volume_meter_description, numberFormat.format(state.lifetimeReps), numberFormat.format(state.volumeGoal)),
            modifier = Modifier.padding(top = spacing.lg),
        )
        Text(
            text = if (reached) {
                stringResource(R.string.volume_goal_reached, numberFormat.format(state.volumeGoal))
            } else {
                stringResource(R.string.volume_goal_progress, numberFormat.format(state.volumeGoal - state.lifetimeReps), numberFormat.format(state.volumeGoal))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PaintLifeTheme.colors.textSecondary,
            modifier = Modifier.padding(top = spacing.sm),
        )
        if (showStart) {
            PrimaryButton(
                text = stringResource(R.string.action_start_session),
                onClick = onStartSession,
                icon = painterResource(R.drawable.ic_play),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
            )
        }
    }
}

@Composable
private fun SafetyCard(expanded: Boolean, onOpenSafety: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    if (!expanded) {
        QuietButton(text = stringResource(R.string.safety_open), onClick = onOpenSafety, icon = painterResource(R.drawable.ic_info))
        return
    }
    PaintLifeCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.info, modifier = Modifier.size(24.dp))
            Text(text = stringResource(R.string.safety_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = stringResource(R.string.safety_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.sm, bottom = spacing.md),
        )
        SecondaryButton(text = stringResource(R.string.safety_read), onClick = onOpenSafety, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CurrentOpponent(entry: LeagueEntry, canStart: Boolean, onStart: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CatAvatar(entry.cat, entry.avatarState, size = CatAvatarSize.Hero)
            Text(
                text = stringResource(R.string.league_next_opponent),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.lg),
            )
            Text(
                text = stringResource(entry.cat.nameRes),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(entry.cat.breedRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            BigNumber(
                value = DurationFormat.clock(entry.benchmarkMillis),
                label = stringResource(R.string.league_benchmark, stringResource(entry.cat.nameRes)),
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
        SecondaryButton(
            text = stringResource(R.string.action_start_timed),
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(if (canStart) R.string.league_optional_note else R.string.league_finish_open_first),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

@Composable
private fun AllBeatenCard() {
    PaintLifeCard(Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.league_all_beaten_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.league_all_beaten_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PaintLifeTheme.colors.textSecondary,
            modifier = Modifier.padding(top = PaintLifeTheme.spacing.xs),
        )
    }
}

@Composable
private fun Ladder(league: List<LeagueEntry>, onOpenCat: (Int) -> Unit) {
    Tray(Modifier.fillMaxWidth()) {
        league.forEachIndexed { index, entry ->
            if (index > 0) TrayDivider()
            LadderRow(entry, onClick = { onOpenCat(entry.cat.level) })
        }
    }
}

@Composable
private fun LadderRow(entry: LeagueEntry, onClick: () -> Unit) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val status = when (entry.status) {
        LevelStatus.Locked -> stringResource(R.string.league_status_locked)
        LevelStatus.Current -> entry.personalBestMillis?.let { stringResource(R.string.league_status_best, DurationFormat.clock(it)) }
            ?: stringResource(R.string.league_status_current)
        LevelStatus.Beaten -> stringResource(R.string.league_status_beaten, DurationFormat.clock(entry.personalBestMillis ?: 0L))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.league_open_cat), onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.cat.level.toString(),
            style = PaintLifeTheme.numbers.small,
            color = colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp),
        )
        CatAvatar(entry.cat, entry.avatarState, size = CatAvatarSize.Medium)
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(entry.cat.nameRes), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(entry.cat.breedRes), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = DurationFormat.clock(entry.benchmarkMillis), style = PaintLifeTheme.numbers.small)
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = when (entry.status) {
                    LevelStatus.Beaten -> colors.highlight
                    LevelStatus.Current -> colors.accent
                    LevelStatus.Locked -> colors.textSecondary
                },
            )
        }
    }
}
