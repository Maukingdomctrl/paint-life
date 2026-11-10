package com.mauforge.paintlife.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import com.mauforge.paintlife.exercise.cats.CatRoster
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun buttonsMeetTheMinimumTouchTarget() {
        composeRule.setContent {
            PaintLifeTheme {
                Column {
                    PrimaryButton("Primary", {})
                    SecondaryButton("Secondary", {})
                    QuietButton("Quiet", {})
                    DestructiveButton("Delete", {})
                }
            }
        }
        listOf("Primary", "Secondary", "Quiet", "Delete").forEach {
            composeRule.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun disabledButtonIsAnnouncedAsDisabledAndDoesNothing() {
        var clicks = 0
        composeRule.setContent { PaintLifeTheme { PrimaryButton("Save", { clicks++ }, enabled = false) } }
        composeRule.onNodeWithText("Save").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun progressRingExposesItsValueAndDescription() {
        composeRule.setContent { PaintLifeTheme { ProgressRing(0.6f, "Quiet time, 2:00 left") } }
        composeRule.onNodeWithContentDescription("Quiet time, 2:00 left")
            .assertIsDisplayed()
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.6f, 0f..1f))
    }

    @Test
    fun meterBarClampsOverflowingProgress() {
        composeRule.setContent { PaintLifeTheme { MeterBar(1.8f, "1,800 of 1,000 step-ups") } }
        composeRule.onNodeWithContentDescription("1,800 of 1,000 step-ups").assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
    }

    @Test
    fun bigNumberIsReadAsOnePhrase() {
        composeRule.setContent { PaintLifeTheme { BigNumber("640", unit = "step-ups", label = "Lifetime step-ups") } }
        // Merged: one node carries the number, unit and label together.
        composeRule.onNodeWithText("640").assertIsDisplayed()
        composeRule.onNodeWithText("step-ups").assertIsDisplayed()
        composeRule.onNodeWithText("Lifetime step-ups").assertIsDisplayed()
    }

    @Test
    fun emptyStateActionAndErrorRetryAreRealActions() {
        var added = 0
        var retried = 0
        composeRule.setContent {
            PaintLifeTheme {
                Column {
                    EmptyState("Nothing yet", "Add one.", action = { SecondaryButton("Add", { added++ }) })
                    ErrorState("Couldn't read the file", "Choose another file.", onRetry = { retried++ })
                }
            }
        }
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, added)
        assertEquals(1, retried)
    }

    @Test
    fun catAvatarStatesAreSpokenNotJustColoured() {
        val ana = CatRoster.cats[0]
        val mia = CatRoster.cats[1]
        val zoe = CatRoster.cats[2]
        composeRule.setContent {
            PaintLifeTheme {
                Column {
                    CatAvatar(ana, CatAvatarState.Beaten)
                    CatAvatar(mia, CatAvatarState.Current)
                    CatAvatar(zoe, CatAvatarState.Locked)
                }
            }
        }
        composeRule.onNodeWithContentDescription("Ana, beaten").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mia, next opponent").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Zoe, locked").assertIsDisplayed()
    }

    @Test
    fun segmentedPickerSelectsAnOption() {
        var selected = "Today"
        composeRule.setContent {
            PaintLifeTheme { SegmentedPicker(listOf("Today", "7 days"), selected, { selected = it }, label = { it }) }
        }
        // Each option is a full-size touch target of its own.
        composeRule.onNodeWithText("Today").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("7 days").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals("7 days", selected)
    }
}
