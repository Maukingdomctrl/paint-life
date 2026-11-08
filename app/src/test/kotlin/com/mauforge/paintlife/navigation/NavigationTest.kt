package com.mauforge.paintlife.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Iteration that delivers each destination, as shown on its placeholder. */
    private val deliveredIn = mapOf(
        TopLevelDestination.TODAY to 3,
        TopLevelDestination.EXERCISE to 4,
        TopLevelDestination.QUIET to 5,
        TopLevelDestination.MY_TIME to 6,
        TopLevelDestination.PROGRESS to 7,
    )

    @Before
    fun setUp() {
        composeRule.setContent { PaintLifeTheme { PaintLifeAppShell() } }
    }

    @Test
    fun startsOnTodayWithItsComingSoonNotice() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        composeRule.onNodeWithText(comingIn(3)).assertIsDisplayed()
    }

    @Test
    fun eachTabOpensItsScreenAndLabelsItAsNotBuiltYet() {
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag(navItemTestTag(destination)).performClick()
            composeRule.onNodeWithTag(navItemTestTag(destination)).assertIsSelected()
            composeRule.onNodeWithText(comingIn(deliveredIn.getValue(destination))).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.not_built_badge)).assertIsDisplayed()
        }
    }

    @Test
    fun backFromAnotherTabReturnsToToday() {
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).performClick()
        composeRule.onNodeWithText(comingIn(7)).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        composeRule.onNodeWithText(comingIn(3)).assertIsDisplayed()
    }

    private fun comingIn(iteration: Int) = context.getString(R.string.coming_in_iteration, iteration)
}
