package com.mauforge.paintlife.debug

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.today.TodayContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen layout in every sample state, rendered at 200% text on a small 320dp phone, plus
 * targeted checks of the rules the layouts must keep (no dead buttons, honest labels).
 */
@RunWith(AndroidJUnit4::class)
class ScreenLayoutsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun showLarge(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.FontScale(2f) then DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp)),
            ) {
                PaintLifeTheme { content() }
            }
        }
    }

    /** Scrolls item by item to the end of the screen's list, so every item is composed and laid out. */
    private fun scrollToEnd(): Int {
        var index = 0
        while (index < 200) {
            val moved = runCatching { composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(index) }.isSuccess
            if (!moved) break
            index++
        }
        return index
    }

    @Test
    fun everyGallerySampleRendersAtDoubleTextOnASmallPhone() {
        var current by mutableStateOf(galleryEntries.first())
        showLarge { current.content() }
        galleryEntries.forEach { entry ->
            current = entry
            composeRule.waitForIdle()
            val items = scrollToEnd()
            assertTrue("${entry.id} should show a scrolling screen", items > 0)
        }
    }

    @Test
    fun todayPriorityToggleAndEditAreRealActions() {
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val edited = mutableListOf<String>()
        composeRule.setContent {
            PaintLifeTheme {
                TodayContent(TodaySamples.partlyPainted, { id, done -> toggled += id to done }, { edited += it }, {}, {}, {}, {})
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Walk to the market"))
        composeRule.onNodeWithText("Walk to the market").performClick()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Edit priority: Read one chapter"))
        composeRule.onNode(hasContentDescription("Edit priority: Read one chapter")).performClick()
        assertEquals(listOf("p2" to true), toggled)
        assertEquals(listOf("p3"), edited)
    }

    @Test
    fun todayWithThreePrioritiesOffersNoAddButton() {
        showLarge { galleryEntries.first { it.id == "today_partial" }.content() }
        scrollToEnd()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        assertEquals(0, composeRule.onAllNodes(hasText("Add priority")).fetchSemanticsNodes().size)
    }

    @Test
    fun timedChallengeWaitsForAnOpenSessionToFinish() {
        showLarge { galleryEntries.first { it.id == "exercise_restored" }.content() }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Finish your open session first."))
        composeRule.onNodeWithText("Start timed challenge").assertIsNotEnabled()
        composeRule.onNodeWithText("Finish your open session first.").assertIsDisplayed()
    }

    @Test
    fun lockedCatOffersNothingToTap() {
        showLarge { galleryEntries.first { it.id == "cat_locked" }.content() }
        scrollToEnd()
        assertEquals(0, composeRule.onAllNodes(hasText("Start timed challenge")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodes(hasText("Replay level")).fetchSemanticsNodes().size)
    }

    @Test
    fun pausedTimedChallengeSaysTheClockKeepsRunning() {
        showLarge { galleryEntries.first { it.id == "session_timed_paused" }.content() }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Paused. The challenge clock keeps running."))
        composeRule.onNodeWithText("Paused. The challenge clock keeps running.").assertIsDisplayed()
    }

    @Test
    fun overnightSleepOnTheDstDayIsReadWithItsRealTimes() {
        composeRule.setContent { PaintLifeTheme { galleryEntries.first { it.id == "my_time_dst" }.content() } }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Sleep, 23:00 to 07:30"))
        composeRule.onNode(hasContentDescription("Sleep, 23:00 to 07:30")).assertExists()
    }

    @Test
    fun galleryOpensASampleWithASampleBadgeAndCloses() {
        composeRule.setContent { PaintLifeTheme { DesignGallery(onClose = {}) } }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Partly painted"))
        composeRule.onNodeWithText("Partly painted").performClick()
        composeRule.onNodeWithTag("sample_today_partial").assertIsDisplayed()
        composeRule.onNodeWithText("Sample").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Close sample")).performClick()
        composeRule.onNodeWithTag("design_gallery").assertIsDisplayed()
    }
}
