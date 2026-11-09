package com.mauforge.paintlife.exercise

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.SessionTimer
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

/** Session history end to end: real ViewModel and (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class ExerciseHistoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 40), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var sessionId: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = ExerciseRepository(db, time.provider)
        sessionId = runBlocking {
            val started = repository.startVolumeSession(SessionTimer.started(0, 1))
            repository.finish(started.id, 120, SessionTimer.paused(started.timer, 660_000, 1))!!.id
        }
        val viewModel = ExerciseHistoryViewModel(repository, time.dayClock)
        composeRule.setContent { PaintLifeTheme { ExerciseHistoryScreen(viewModel = viewModel) } }
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("history_row_$sessionId")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun lifetime() = runBlocking { repository.observeLifetimeReps().first() }

    @Test
    fun showsTheSessionUnderTodayWithItsRepsAndActiveTime() {
        composeRule.onNodeWithText(string(R.string.history_today)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.history_row_summary, "120 reps", "11:00")).assertIsDisplayed()
    }

    @Test
    fun correctingTheRepsAndNoteUpdatesTotals() {
        composeRule.onNodeWithTag("history_row_$sessionId").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("history_editor")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("reps_field").performTextReplacement("100")
        composeRule.onNodeWithTag("note_field").performTextReplacement("Miscounted a set")
        composeRule.onNodeWithText(string(R.string.action_save)).performScrollTo().performClick()

        composeRule.waitUntil(5_000) { lifetime() == 100 }
        assertEquals("Miscounted a set", runBlocking { repository.observeFinishedSessions().first() }.single().notes)
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("history_editor")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun deletingAsksFirstAndThenRemovesTheSession() {
        composeRule.onNodeWithTag("history_row_$sessionId").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag("history_editor")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.history_delete)).performScrollTo().performClick()

        composeRule.onNodeWithText(string(R.string.history_delete_title)).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.action_cancel)) and hasAnyAncestor(hasTestTag("confirm_dialog"))).performClick()
        composeRule.waitForIdle()
        assertEquals(120, lifetime())

        composeRule.onNodeWithText(string(R.string.history_delete)).performScrollTo().performClick()
        composeRule.onNode(hasText(string(R.string.history_delete_confirm)) and hasAnyAncestor(hasTestTag("confirm_dialog"))).performClick()

        composeRule.waitUntil(5_000) { lifetime() == 0 }
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasText(string(R.string.history_empty_title))).fetchSemanticsNodes().isNotEmpty() }
    }
}
