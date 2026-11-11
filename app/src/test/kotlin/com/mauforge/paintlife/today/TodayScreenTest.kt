package com.mauforge.paintlife.today

import com.mauforge.paintlife.core.backup.DataReplaced
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
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
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.mytime.LogRequest
import com.mauforge.paintlife.mytime.MyTimeLaunch
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.testing.testPreferences
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimerCheckpoint
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
    // Afternoon by default, so the morning sleep card is out of the way; its own tests set the morning.
    private val time = TestTime(day.atTime(15, 0), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TodayRepository
    private var exerciseOpened = 0
    private var myTimeOpened = 0
    private val launch = MyTimeLaunch()
    private var quietOpened = 0

    @Before
    fun setUp() {
        // Seeded, so the built-in categories (Sleep for the morning card) exist.
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = TodayRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun show() {
        val viewModel = TodayViewModel(
            repository,
            ExerciseRepository(db, time.provider),
            QuietRepository(db, time.provider),
            TimeRepository(db, time.provider),
            launch,
            testPreferences(ApplicationProvider.getApplicationContext()),
            time.provider,
            time.dayClock,
            DataReplaced(),
        )
        composeRule.setContent { PaintLifeTheme { TodayScreen(onStartExercise = { exerciseOpened++ }, onOpenQuiet = { quietOpened++ }, onOpenMyTime = { myTimeOpened++ }, viewModel = viewModel) } }
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
    fun undoOnAFullDayExplainsWhyItCouldNotPutThePriorityBack() {
        runBlocking { listOf("Walk", "Read", "Call").forEach { repository.add(day, it) } }
        show()
        waitFor(hasText("Call"))

        val edit = hasContentDescription(string(R.string.today_edit_priority, "Call"))
        scrollTo(edit)
        composeRule.onNode(edit).performClick()
        waitFor(hasText(string(R.string.today_delete_priority)))
        composeRule.onNodeWithText(string(R.string.today_delete_priority)).performClick()
        waitFor(hasText(string(R.string.today_deleted, "Call")))
        composeRule.waitUntil(5_000) { stored().size == 2 }

        // The day fills up again before Undo is tapped.
        runBlocking { repository.add(day, "Cook") }
        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()

        // The notice must actually appear, not be cleared before it is shown.
        waitFor(hasText(string(R.string.today_notice_restore_failed)))
        assertEquals(listOf("Walk", "Read", "Cook"), stored().map { it.title })
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
    fun everyAreaIsTrackedAndEveryQuickActionWorks() {
        show()
        waitFor(hasTestTag("today_content"))

        listOf("swatch_exercise", "swatch_quiet", "swatch_time").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        assertEquals(0, composeRule.onAllNodes(hasText("Not tracked yet"), useUnmergedTree = true).fetchSemanticsNodes().size)
        val exerciseAction = hasText(string(R.string.action_start_exercise)) and hasClickAction()
        val quietAction = hasText(string(R.string.action_quiet_time)) and hasClickAction()
        val logAction = hasText(string(R.string.action_log_time)) and hasClickAction()
        scrollTo(hasText(string(R.string.today_so_far)))

        scrollTo(exerciseAction)
        composeRule.onNode(exerciseAction).performClick()
        composeRule.onNode(quietAction).performClick()
        composeRule.onNode(logAction).performClick()
        composeRule.waitUntil(5_000) { myTimeOpened == 1 }
        assertEquals(1, exerciseOpened)
        assertEquals(1, quietOpened)
        // My Time is asked to open its sheet from the last entry to now.
        assertEquals(LogRequest.SinceLastEntry, launch.request.value)
    }

    @Test
    fun theMorningSleepCardLogsLastNightInOneTap() {
        time.setLocal(day.atTime(8, 30))
        show()
        waitFor(hasTestTag("today_sleep_card"))
        composeRule.onNodeWithText(string(R.string.today_sleep_body, "23:00", "07:00")).assertExists()

        val logIt = hasText(string(R.string.action_log_it)) and hasClickAction()
        scrollTo(logIt)
        composeRule.onNode(logIt).performClick()

        // The confirmation is short-lived, so it is checked first.
        waitFor(hasText(string(R.string.today_sleep_logged)))
        waitForNo(hasTestTag("today_sleep_card"))
        val sleep = runBlocking { TimeRepository(db, time.provider).observeDayEntries(day).first() }.single().entry
        assertEquals("sleep", sleep.categoryId)
        assertEquals(day.minusDays(1).atTime(23, 0).atZone(time.zone).toInstant(), sleep.startAt)
        assertEquals(day.atTime(7, 0).atZone(time.zone).toInstant(), sleep.endAt)
    }

    @Test
    fun sleepShowsSleepingSinceAndUndoTakesItBack() {
        show()
        waitFor(hasTestTag("today_content"))
        val sleep = hasText(string(R.string.action_sleep)) and hasClickAction()
        scrollTo(sleep)
        composeRule.onNode(sleep).performClick()

        waitFor(hasText(string(R.string.today_sleep_started)))
        scrollTo(hasTestTag("today_sleeping"))
        composeRule.onNodeWithText(string(R.string.today_sleeping_since, "15:00")).assertExists()
        // Only Wake up is offered while asleep.
        assertEquals(0, composeRule.onAllNodes(sleep).fetchSemanticsNodes().size)
        assertEquals("sleep", runBlocking { TimeRepository(db, time.provider).observeCurrent().first() }?.categoryId)

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()

        composeRule.waitUntil(5_000) { runBlocking { TimeRepository(db, time.provider).observeCurrent().first() } == null }
        waitForNo(hasTestTag("today_sleeping"))
        scrollTo(sleep)
    }

    @Test
    fun wakeUpSavesTheSleepInOneTap() {
        val times = TimeRepository(db, time.provider)
        time.setLocal(day.atTime(13, 0))
        runBlocking { times.startSleep() }
        time.setLocal(day.atTime(15, 0))
        show()
        waitFor(hasTestTag("today_sleeping"))

        val wakeUp = hasText(string(R.string.action_wake_up)) and hasClickAction()
        scrollTo(wakeUp)
        composeRule.onNode(wakeUp).performClick()

        waitFor(hasText(string(R.string.today_sleep_saved)))
        waitForNo(hasTestTag("today_sleeping"))
        val saved = runBlocking { times.observeDayEntries(day).first() }.single().entry
        assertEquals("sleep", saved.categoryId)
        assertEquals(day.atTime(13, 0).atZone(time.zone).toInstant(), saved.startAt)
        assertEquals(day.atTime(15, 0).atZone(time.zone).toInstant(), saved.endAt)
    }

    @Test
    fun notNowHidesTheSleepCardForTheDay() {
        time.setLocal(day.atTime(8, 30))
        show()
        waitFor(hasTestTag("today_sleep_card"))
        val notNow = hasText(string(R.string.action_not_now)) and hasClickAction() and hasAnyAncestor(hasTestTag("today_sleep_card"))
        scrollTo(notNow)
        composeRule.onNode(notNow).performClick()
        waitForNo(hasTestTag("today_sleep_card"))
    }

    @Test
    fun theQuietSwatchShowsMinutesForAShortSessionAndDoneForAFullOne() {
        val quiet = QuietRepository(db, time.provider)
        runBlocking {
            val short = quiet.start(SessionTimer.started(0L, 1))
            quiet.endEarly(short.id, TimerCheckpoint(180_000L, 180_000L, null, 1))
        }
        show()
        waitFor(hasText(string(R.string.today_quiet_minutes, 3)))

        runBlocking {
            val full = quiet.start(SessionTimer.started(0L, 1))
            quiet.complete(full.id, TimerCheckpoint(300_000L, 300_000L, null, 1), overshootMillis = 0L)
        }
        waitForNo(hasText(string(R.string.today_quiet_minutes, 3)))
        assertEquals(1, composeRule.onAllNodes(hasText(string(R.string.today_done)), useUnmergedTree = true).fetchSemanticsNodes().size)
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
