package com.mauforge.paintlife.exercise

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** The counter route end to end: real ViewModel, controller and (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class ExerciseSessionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = TestTime(LocalDate.of(2026, 11, 9).atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val monotonic = FakeSessionClock(now = 50_000, boot = 2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var controller: ExerciseSessionController
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var viewModel: ExerciseSessionViewModel
    private var closed = 0
    private val results = mutableListOf<String>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = ExerciseRepository(db, time.provider)
        controller = ExerciseSessionController(repository, monotonic.clock, scope)
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "session_prefs_${UUID.randomUUID()}.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        // Let the controller's last save finish before the database closes.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun show(safetyRead: Boolean = true, level: Int = 0) {
        if (safetyRead) runBlocking { preferences.markExerciseSafetyRead() }
        viewModel = ExerciseSessionViewModel(SavedStateHandle(mapOf("level" to level)), controller, repository, preferences)
        composeRule.setContent {
            PaintLifeTheme { ExerciseSessionScreen(onClose = { closed++ }, onShowResult = { results += it }, viewModel = viewModel) }
        }
    }

    private fun waitForCounter() {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("exercise_session")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Querying the tree lets Main run (ViewModel coroutines, recomposition) while waiting. */
    private fun waitForReps(reps: Int) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("exercise_session")).fetchSemanticsNodes()
            controller.live.value?.counter?.reps == reps
        }
        composeRule.waitForIdle()
    }

    /**
     * The screen closes itself after Finish or Discard. Querying the tree lets Compose run its
     * pending frames while waiting (the test thread is the main thread under Robolectric).
     */
    private fun awaitClosed() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("exercise_session")).fetchSemanticsNodes().isEmpty() && closed == 1
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
    }

    private fun tapAdd(amount: Int) {
        composeRule.onNodeWithContentDescription(string(R.string.session_add_reps, amount)).performClick()
    }

    @Test
    fun theFirstSessionShowsTheSafetyGuidanceFirst() {
        show(safetyRead = false)
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("safety_guidance")).fetchSemanticsNodes().isNotEmpty() }
        assertNull(controller.live.value)

        scrollTo(string(R.string.safety_done))
        composeRule.onNodeWithText(string(R.string.safety_done)).performClick()

        waitForCounter()
        assertTrue(runBlocking { preferences.hasReadExerciseSafety.first() })
        assertEquals(SessionStatus.RUNNING, controller.live.value?.session?.status)
    }

    @Test
    fun tapsCountAndUndoTakesBackTheLastOne() {
        show()
        waitForCounter()
        composeRule.onNodeWithContentDescription(string(R.string.session_undo)).assertIsNotEnabled()

        repeat(3) { tapAdd(1) }
        tapAdd(10)
        waitForReps(13)
        composeRule.onNodeWithText("13").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.session_undo)).performClick()
        waitForReps(3)
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun pausingStopsCountingAndLetsTheScreenSleep() {
        show()
        waitForCounter()
        assertTrue(viewModel.state.value.keepScreenOn)

        composeRule.onNodeWithText(string(R.string.action_pause)).performClick()

        composeRule.waitUntil(5_000) { controller.live.value?.isPaused == true }
        composeRule.onNodeWithContentDescription(string(R.string.session_add_reps, 1)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.session_paused_volume)).assertIsDisplayed()
        composeRule.waitUntil(5_000) { !viewModel.state.value.keepScreenOn }

        scrollTo(string(R.string.action_resume))
        composeRule.onNodeWithText(string(R.string.action_resume)).performClick()
        composeRule.waitUntil(5_000) { controller.live.value?.isPaused == false }
        composeRule.onNodeWithContentDescription(string(R.string.session_add_reps, 1)).assertIsEnabled()
    }

    @Test
    fun resetAsksFirstAndCancelKeepsTheCount() {
        show()
        waitForCounter()
        tapAdd(5)
        waitForReps(5)

        scrollTo(string(R.string.session_reset))
        composeRule.onNodeWithText(string(R.string.session_reset)).performClick()
        composeRule.onNodeWithText(string(R.string.session_reset_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        composeRule.waitForIdle()
        assertEquals(5, controller.live.value?.counter?.reps)

        composeRule.onNodeWithText(string(R.string.session_reset)).performClick()
        composeRule.onNodeWithText(string(R.string.session_reset_confirm)).performClick()
        waitForReps(0)
        assertFalse(controller.live.value!!.counter.canUndo)
    }

    @Test
    fun finishingWithNothingCountedOffersToDiscard() {
        show()
        waitForCounter()

        composeRule.onNodeWithText(string(R.string.action_finish)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(string(R.string.session_discard_title))).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.session_keep_going)).performClick()
        composeRule.waitForIdle()
        assertEquals(0, closed)

        composeRule.onNodeWithText(string(R.string.action_finish)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(string(R.string.session_discard_confirm))).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.session_discard_confirm)).performClick()

        awaitClosed()
        assertNull(controller.live.value)
        assertEquals(0, runBlocking { db.exerciseDao().openSessions() }.size)
    }

    @Test
    fun finishSavesTheSessionAndCloses() {
        show()
        waitForCounter()
        tapAdd(10)
        tapAdd(5)
        waitForReps(15)

        composeRule.onNodeWithText(string(R.string.action_finish)).performClick()

        awaitClosed()
        val saved = runBlocking { withTimeout(5_000) { repository.observeFinishedSessions().first() } }.single()
        assertEquals(15, saved.repetitions)
        assertEquals(SessionStatus.COMPLETED, saved.status)
    }

    @Test
    fun correctCountSetsTheNumberAndUndoPutsTheOldOneBack() {
        show()
        waitForCounter()
        tapAdd(10)
        waitForReps(10)

        scrollTo(string(R.string.session_correct_count))
        composeRule.onNodeWithText(string(R.string.session_correct_count)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("reps_field")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasTestTag("reps_field")).performTextReplacement("12")
        composeRule.onNodeWithText(string(R.string.action_save)).performClick()

        waitForReps(12)
        composeRule.onNodeWithContentDescription(string(R.string.session_undo)).performClick()
        waitForReps(10)
    }

    private fun confirmInDialog(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag("confirm_dialog"))).performClick()
    }

    @Test
    fun aLockedCatClosesTheCounterWithoutStartingAnything() {
        show(level = 3)
        awaitClosed()
        assertNull(controller.live.value)
    }

    @Test
    fun stoppingATimedChallengeAsksFirstAndKeepsTheReps() {
        show(level = 1)
        waitForCounter()
        composeRule.onNodeWithText(string(R.string.session_title_timed, "Aero")).assertIsDisplayed()
        tapAdd(10)
        waitForReps(10)
        assertEquals(0, composeRule.onAllNodes(hasText(string(R.string.action_finish))).fetchSemanticsNodes().size)

        scrollTo(string(R.string.session_stop_challenge))
        composeRule.onNodeWithText(string(R.string.session_stop_challenge)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("confirm_dialog")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.session_stop_title)).assertIsDisplayed()
        confirmInDialog(string(R.string.session_stop_challenge))

        awaitClosed()
        val stopped = runBlocking { withTimeout(5_000) { repository.observeFinishedSessions().first() } }.single()
        assertEquals(SessionStatus.CANCELLED, stopped.status)
        assertEquals(10, stopped.repetitions)
    }

    @Test
    fun reachingOneThousandOffersUndoThenTheCounterComesBack() {
        controller.undoWindowMillis = 60_000
        show(level = 1)
        waitForCounter()
        assertTrue(controller.correct(999))
        waitForReps(999)

        tapAdd(1)

        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("finishing")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription(string(R.string.session_add_reps, 1)).assertIsNotEnabled()
        composeRule.onNode(hasText(string(R.string.action_undo)) and hasAnyAncestor(hasTestTag("finishing"))).performClick()

        waitForReps(999)
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("finishing")).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithContentDescription(string(R.string.session_add_reps, 1)).assertIsEnabled()
    }

    @Test
    fun afterTheUndoWindowTheResultTakesOver() {
        controller.undoWindowMillis = 200
        show(level = 1)
        waitForCounter()
        val id = controller.live.value!!.session.id
        assertTrue(controller.correct(995))
        tapAdd(5)

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("exercise_session")).fetchSemanticsNodes().isEmpty() && results == listOf(id)
        }
        assertEquals(0, closed)
    }
}
