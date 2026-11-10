package com.mauforge.paintlife.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
import com.mauforge.paintlife.quiet.QuietNotifications
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The real app shell in the real activity, with Hilt's test graph (see TestAppModule). */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun waitForToday() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("today_content")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun startsOnTheRealTodayScreen() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        waitForToday()
        composeRule.onNodeWithText(context.getString(R.string.today_canvas)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.not_built_badge))).fetchSemanticsNodes().size)
    }

    @Test
    fun progressTabShowsTheRealReports() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).performClick()
        waitForTag("progress_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).assertIsSelected()
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.not_built_badge))).fetchSemanticsNodes().size)
    }

    @Test
    fun quietTabShowsTheRealTimer() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.QUIET)).performClick()
        waitForTag("quiet_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.QUIET)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.quiet_subtitle_ready)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.not_built_badge))).fetchSemanticsNodes().size)
    }

    @Test
    fun myTimeTabShowsTheRealTimeline() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.MY_TIME)).performClick()
        waitForTag("my_time_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.MY_TIME)).assertIsSelected()
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.not_built_badge))).fetchSemanticsNodes().size)
    }

    @Test
    fun aQuietNotificationTapOpensTheQuietTab() {
        waitForToday()
        composeRule.runOnUiThread {
            val intent = Intent(context, MainActivity::class.java).putExtra(QuietNotifications.EXTRA_OPEN_QUIET, true)
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(composeRule.activity, intent)
        }
        waitForTag("quiet_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.QUIET)).assertIsSelected()
    }

    @Test
    fun exerciseTabShowsTheLeagueAndALadderRowOpensTheCat() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).performClick()
        waitForTag("exercise_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.volume_lifetime)).assertIsDisplayed()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.league_next_opponent)))
        composeRule.onNodeWithText(context.getString(R.string.league_next_opponent)).assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.not_built_badge))).fetchSemanticsNodes().size)

        val mia = hasText(context.getString(R.string.cat_mia_name)) and hasClickAction()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(mia)
        composeRule.onNode(mia).performClick()
        waitForTag("cat_detail")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).assertIsSelected()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.cat_locked_hint, "Ana")))
        assertEquals(0, composeRule.onAllNodes(hasText(context.getString(R.string.action_start_timed))).fetchSemanticsNodes().size)
    }

    @Test
    fun theCounterKeepsTheExerciseTabSelectedAndBackReturnsToTheTab() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).performClick()
        waitForTag("exercise_content")
        composeRule.onNodeWithText(context.getString(R.string.action_start_session)).performClick()
        // First session ever: the safety guidance comes first.
        waitForTag("safety_guidance")
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(context.getString(R.string.safety_done)))
        composeRule.onNodeWithText(context.getString(R.string.safety_done)).performClick()
        waitForTag("exercise_session")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).assertIsSelected()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        waitForTag("exercise_content")
        composeRule.onNodeWithText(context.getString(R.string.session_open_running)).assertIsDisplayed()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun backFromAnotherTabReturnsToToday() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).performClick()
        waitForTag("progress_content")

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        waitForToday()
    }
}
