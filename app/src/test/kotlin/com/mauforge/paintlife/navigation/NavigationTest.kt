package com.mauforge.paintlife.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
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

    /** Iteration that delivers each destination still showing its placeholder. */
    private val deliveredIn = mapOf(
        TopLevelDestination.EXERCISE to 4,
        TopLevelDestination.QUIET to 5,
        TopLevelDestination.MY_TIME to 6,
        TopLevelDestination.PROGRESS to 7,
    )

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
    fun eachUnbuiltTabOpensItsScreenAndLabelsItAsNotBuiltYet() {
        deliveredIn.forEach { (destination, iteration) ->
            composeRule.onNodeWithTag(navItemTestTag(destination)).performClick()
            composeRule.onNodeWithTag(navItemTestTag(destination)).assertIsSelected()
            composeRule.onNodeWithText(comingIn(iteration)).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.not_built_badge)).assertIsDisplayed()
        }
    }

    @Test
    fun backFromAnotherTabReturnsToToday() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).performClick()
        composeRule.onNodeWithText(comingIn(7)).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        waitForToday()
    }

    private fun comingIn(iteration: Int) = context.getString(R.string.coming_in_iteration, iteration)
}
