package com.mauforge.paintlife.progress

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Switching periods on the real screen: the range, the numbers and the time section follow. */
@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.minusDays(2).atTime(8, 0), ZoneId.of("Europe/London"))
    private lateinit var db: PaintLifeDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        val exercise = ExerciseRepository(db, time.provider)
        // 120 step-ups two days ago, then the clock moves on to today.
        runBlocking {
            val started = exercise.startVolumeSession(SessionTimer.started(0, 1))
            exercise.finish(started.id, 120, SessionTimer.paused(started.timer, 480_000, 1))
        }
        time.setLocal(day.atTime(18, 0))
        val viewModel = ProgressViewModel(
            exercise, QuietRepository(db, time.provider), TimeRepository(db, time.provider), testPreferences(context), time.provider, time.dayClock,
        )
        composeRule.setContent { PaintLifeTheme { ProgressScreen(viewModel = viewModel) } }
        waitFor(hasTestTag("progress_content"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun choose(labelId: Int) {
        composeRule.onNode(hasText(string(labelId)) and hasClickAction()).performClick()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        waitFor(matcher)
    }

    @Test
    fun startsOnTheLastSevenDays() {
        composeRule.onNode(hasText(string(R.string.progress_period_7_days)) and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithTag("progress_range").assertTextEquals("3 November – 9 November")
        waitFor(hasText("120"))
    }

    @Test
    fun switchingPeriodsChangesTheRangeAndTheNumbers() {
        choose(R.string.progress_period_today)
        waitFor(hasText(string(R.string.progress_exercise_empty_title)))
        composeRule.onNodeWithTag("progress_range").assertTextEquals("9 November")

        choose(R.string.progress_period_this_month)
        waitFor(hasText("1 November – 9 November"))
        waitFor(hasText("120"))

        choose(R.string.progress_period_last_month)
        waitFor(hasText("1 October – 31 October"))
        waitFor(hasText(string(R.string.progress_exercise_empty_title)))
    }

    @Test
    fun timeOverSeveralDaysWaitsForTheObservationWeek() {
        scrollTo(hasTestTag("progress_time"))
        waitFor(hasText(string(R.string.my_time_observation_title, 0)))

        scrollTo(hasTestTag("progress_range"))
        choose(R.string.progress_period_today)
        scrollTo(hasTestTag("progress_time"))
        waitFor(hasText(string(R.string.progress_time_empty_title)))
    }
}
