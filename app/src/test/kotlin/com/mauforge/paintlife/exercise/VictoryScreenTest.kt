package com.mauforge.paintlife.exercise

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.exercise.cats.CatRoster
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** The victory moment and the calm completion screen, as the user sees them. */
@RunWith(AndroidJUnit4::class)
class VictoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ana = LeagueEntry(CatRoster.forLevel(1)!!, TimeUnit.MINUTES.toMillis(30), LevelStatus.Beaten, TimeUnit.MINUTES.toMillis(29))
    private var continued = 0

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun aFirstWinNamesTheCatTheMarginAndTheUnlockAndContinues() {
        val state = VictoryUiState(beaten = ana, yourMillis = TimeUnit.MINUTES.toMillis(29) + 12_300, unlocked = CatRoster.forLevel(2))
        composeRule.setContent { PaintLifeTheme { VictoryContent(state, onContinue = { continued++ }, reducedMotion = true) } }

        composeRule.onNodeWithText(string(R.string.victory_title, "Ana")).assertIsDisplayed()
        composeRule.onNodeWithText("29:12.3").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.victory_margin, "0:47.7", "30:00")).assertIsDisplayed()
        scrollTo(string(R.string.victory_unlocked, "Mia"))
        composeRule.onNodeWithText(string(R.string.victory_unlocked, "Mia")).assertIsDisplayed()

        scrollTo(string(R.string.action_continue))
        composeRule.onNodeWithText(string(R.string.action_continue)).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun aReplayWinSaysTheCatStaysBeatenAndNeverClaimsTheWholeLeague() {
        val state = VictoryUiState(beaten = ana, yourMillis = TimeUnit.MINUTES.toMillis(28), unlocked = null, isReplay = true)
        composeRule.setContent { PaintLifeTheme { VictoryContent(state, onContinue = {}, reducedMotion = true) } }

        scrollTo(string(R.string.victory_replay, "Ana", "28:00.0"))
        composeRule.onNodeWithText(string(R.string.victory_replay, "Ana", "28:00.0")).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText(string(R.string.victory_champion))).fetchSemanticsNodes().size)
    }

    @Test
    fun theVictoryShowsWithAnimationsOnToo() {
        val state = VictoryUiState(beaten = ana, yourMillis = TimeUnit.MINUTES.toMillis(29), unlocked = CatRoster.forLevel(2))
        composeRule.setContent { PaintLifeTheme { VictoryContent(state, onContinue = {}, reducedMotion = false) } }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText(string(R.string.victory_title, "Ana")).assertIsDisplayed()
    }

    @Test
    fun aTieExplainsThatItDoesNotUnlock() {
        val state = ChallengeCompleteUiState(opponent = ana.copy(status = LevelStatus.Current), yourMillis = ana.benchmarkMillis, bestMillis = ana.benchmarkMillis)
        composeRule.setContent { PaintLifeTheme { ChallengeCompleteContent(state, onContinue = { continued++ }) } }

        composeRule.onNodeWithText(string(R.string.result_complete_title)).assertIsDisplayed()
        scrollTo(string(R.string.result_tie, "Ana", "30:00"))
        composeRule.onNodeWithText(string(R.string.result_tie, "Ana", "30:00")).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasTestTag("victory")).fetchSemanticsNodes().size)
    }

    @Test
    fun aSlowerRunShowsByHowMuchToTheTenth() {
        val state = ChallengeCompleteUiState(opponent = ana.copy(status = LevelStatus.Current), yourMillis = ana.benchmarkMillis + 400, bestMillis = ana.benchmarkMillis + 400)
        composeRule.setContent { PaintLifeTheme { ChallengeCompleteContent(state, onContinue = {}) } }

        composeRule.onNodeWithText("30:00.4").assertIsDisplayed()
        scrollTo(string(R.string.result_slower, "0:00.4", "Ana"))
        composeRule.onNodeWithText(string(R.string.result_slower, "0:00.4", "Ana")).assertIsDisplayed()
    }
}
