package com.mauforge.paintlife.today

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** The Today screen end to end: real ViewModel, real (in-memory) database, hand-driven clock. */
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(9, 0), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TodayRepository
    private var exerciseOpened = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).build()
        repository = TodayRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun show() {
        val viewModel = TodayViewModel(repository, ExerciseRepository(db, time.provider), time.dayClock)
        composeRule.setContent { PaintLifeTheme { TodayScreen(onStartExercise = { exerciseOpened++ }, viewModel = viewModel) } }
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNo(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
    }

    private fun priorityRow(title: String): SemanticsNodeInteraction {
        scrollTo(hasText(title))
        return composeRule.onNode(hasText(title) and isToggleable())
    }

    private fun stored(): List<PriorityEntity> = runBlocking { repository.observePriorities(day).first() }

    private fun addThroughTheSheet(title: String) {
        scrollTo(hasText(string(R.string.today_add_priority)))
        composeRule.onNodeWithText(string(R.string.today_add_priority)).performClick()
        waitFor(hasTestTag("priority_title_field"))
        composeRule.onNodeWithTag("priority_title_field").performTextInput(title)
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()
        waitForNo(hasTestTag("priority_editor"))
        waitFor(hasText(title))
    }

    @Test
    fun addAPriorityFromTheEmptyDay() {
        show()
        waitFor(hasTestTag("today_content"))
        scrollTo(hasText(string(R.string.today_priorities_empty_title)))

        addThroughTheSheet("Call the bank")

        assertEquals(listOf("Call the bank"), stored().map { it.title })
    }

    @Test
    fun saveIsDisabledUntilThereIsATitle() {
        show()
        waitFor(hasTestTag("today_content"))
        scrollTo(hasText(string(R.string.today_add_priority)))
        composeRule.onNodeWithText(string(R.string.today_add_priority)).performClick()
        waitFor(hasTestTag("priority_title_field"))
        composeRule.onNodeWithTag("priority_title_field").performTextInput("   ")
        composeRule.onNodeWithText(string(R.string.action_save)).assertIsNotEnabled()
    }

    @Test
    fun completeAndUncompleteAPriority() {
        runBlocking { repository.add(day, "Walk to the market") }
        show()
        waitFor(hasText("Walk to the market"))

        priorityRow("Walk to the market").assertIsOff().performClick()
        composeRule.waitUntil(5_000) { stored().single().isCompleted }
        priorityRow("Walk to the market").assertIsOn()

        priorityRow("Walk to the market").performClick()
        composeRule.waitUntil(5_000) { !stored().single().isCompleted }
        priorityRow("Walk to the market").assertIsOff()
    }

    @Test
    fun editAPriorityTitle() {
        runBlocking { repository.add(day, "Read") }
        show()
        waitFor(hasText("Read"))

        val edit = hasContentDescription(string(R.string.today_edit_priority, "Read"))
        scrollTo(edit)
        composeRule.onNode(edit).performClick()
        waitFor(hasTestTag("priority_title_field"))
        composeRule.onNodeWithTag("priority_title_field").performTextReplacement("Read one chapter")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        waitFor(hasText("Read one chapter"))
        assertEquals(listOf("Read one chapter"), stored().map { it.title })
    }

    @Test
    fun moveAPriorityDownFromTheEditSheet() {
        runBlocking {
            repository.add(day, "First")
            repository.add(day, "Second")
        }
        show()
        waitFor(hasText("Second"))

        val edit = hasContentDescription(string(R.string.today_edit_priority, "First"))
        scrollTo(edit)
        composeRule.onNode(edit).performClick()
        waitFor(hasText(string(R.string.today_move_down)))
        composeRule.onNodeWithText(string(R.string.today_move_up)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.today_move_down)).performClick()

        composeRule.waitUntil(5_000) { stored().map { it.title } == listOf("Second", "First") }
    }

    @Test
    fun deleteRemovesAtOnceAndUndoBringsItBack() {
        runBlocking { repository.add(day, "Water the plants") }
        show()
        waitFor(hasText("Water the plants"))

        val edit = hasContentDescription(string(R.string.today_edit_priority, "Water the plants"))
        scrollTo(edit)
        composeRule.onNode(edit).performClick()
        waitFor(hasText(string(R.string.today_delete_priority)))
        composeRule.onNodeWithText(string(R.string.today_delete_priority)).performClick()

        // No confirmation dialog: it is gone, with an Undo offer.
        waitFor(hasText(string(R.string.today_deleted, "Water the plants")))
        composeRule.waitUntil(5_000) { stored().isEmpty() }

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()
        composeRule.waitUntil(5_000) { stored().map { it.title } == listOf("Water the plants") }
        waitFor(hasText("Water the plants"))
        priorityRow("Water the plants").assertIsOff()
    }

    @Test
    fun aFullDayOffersNoAddButton() {
        runBlocking { repeat(3) { repository.add(day, "P$it") } }
        show()
        waitFor(hasText("P2"))
        scrollTo(hasText("P2"))
        assertEquals(0, composeRule.onAllNodes(hasText(string(R.string.today_add_priority))).fetchSemanticsNodes().size)
    }

    @Test
    fun unbuiltAreasSayNotTrackedYetAndOnlyTheExerciseActionIsOffered() {
        show()
        waitFor(hasTestTag("today_content"))

        listOf("swatch_exercise", "swatch_quiet", "swatch_time").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        // Exercise is tracked now; quiet time and time are not yet.
        assertEquals(2, composeRule.onAllNodes(hasText(string(R.string.today_not_tracked)), useUnmergedTree = true).fetchSemanticsNodes().size)
        // Scroll to the end so every section that exists has been composed.
        val exerciseAction = hasText(string(R.string.action_start_exercise)) and hasClickAction()
        scrollTo(exerciseAction)
        listOf(R.string.today_so_far, R.string.action_log_time).forEach {
            assertEquals(string(it), 0, composeRule.onAllNodes(hasText(string(it)), useUnmergedTree = true).fetchSemanticsNodes().size)
        }

        composeRule.onNode(exerciseAction).performClick()
        composeRule.waitForIdle()
        assertEquals(1, exerciseOpened)
    }

    @Test
    fun bringAChosenPriorityFromYesterday() {
        runBlocking {
            repository.add(day.minusDays(1), "Walk")
            repository.add(day.minusDays(1), "Read")
        }
        show()
        waitFor(hasText(string(R.string.today_carry_title_yesterday)))

        scrollTo(hasText(string(R.string.today_carry_choose)))
        composeRule.onNodeWithText(string(R.string.today_carry_choose)).performClick()
        waitFor(hasTestTag("carry_over_sheet"))
        composeRule.onNode(hasText("Read") and isToggleable()).performClick()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.today_carry_confirm, 1, 1)).performClick()

        composeRule.waitUntil(5_000) { stored().map { it.title } == listOf("Read") }
        waitForNo(hasTestTag("carry_over_sheet"))
        assertEquals(listOf("Walk", "Read"), runBlocking { repository.observePriorities(day.minusDays(1)).first() }.map { it.title })
    }

    @Test
    fun notNowHidesTheOffer() {
        runBlocking { repository.add(day.minusDays(1), "Walk") }
        show()
        waitFor(hasText(string(R.string.today_carry_title_yesterday)))
        scrollTo(hasText(string(R.string.action_not_now)))
        composeRule.onNodeWithText(string(R.string.action_not_now)).performClick()
        waitForNo(hasTestTag("carry_over_offer"))
    }
}
