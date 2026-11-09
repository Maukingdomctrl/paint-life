package com.mauforge.paintlife.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.DurationFormat
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize

/**
 * 1,000 reps finished but not faster than the cat. A plain record of the run, no celebration and
 * no pressure: your time, the cat's time, your best, and what a tie means.
 */
@Composable
fun ChallengeCompleteContent(
    state: ChallengeCompleteUiState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val name = stringResource(state.opponent.cat.nameRes)
    val benchmark = DurationFormat.clock(state.opponent.benchmarkMillis)
    PaintLifeScreen(modifier = modifier.testTag("challenge_complete"), verticalSpacing = spacing.lg) {
        item(key = "cat") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CatAvatar(state.opponent.cat, state.opponent.avatarState, size = CatAvatarSize.Hero)
            }
        }
        item(key = "headline") {
            Text(
                text = stringResource(R.string.result_complete_title),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        }
        item(key = "numbers") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                BigNumber(
                    value = DurationFormat.clockTenths(state.yourMillis),
                    label = stringResource(R.string.result_your_time),
                    style = PaintLifeTheme.numbers.medium,
                    modifier = Modifier.weight(1f),
                )
                BigNumber(
                    value = benchmark,
                    label = stringResource(R.string.league_benchmark, name),
                    style = PaintLifeTheme.numbers.medium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "note") {
            Text(
                text = if (state.isTie) {
                    stringResource(R.string.result_tie, name, benchmark)
                } else {
                    stringResource(R.string.result_slower, DurationFormat.clockTenths(state.yourMillis - state.opponent.benchmarkMillis), name)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(key = "best") {
            Text(
                text = stringResource(R.string.result_best, DurationFormat.clockTenths(state.bestMillis)),
                style = MaterialTheme.typography.bodyMedium,
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
