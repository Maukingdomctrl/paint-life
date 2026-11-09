package com.mauforge.paintlife.quiet

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Quiet Time end to end: real ViewModel and controller, in-memory database, hand-driven clocks. */
@RunWith(AndroidJUnit4::class)
class QuietScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val monotonic = FakeSessionClock(now = 1_000_000, boot = 3)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var db: PaintLifeDatabase
    private lateinit var controller: QuietSessionController
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).build()
        controller = QuietSessionController(QuietRepository(db, time.provider), monotonic.clock, scope)
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(produceFile = { File(context.cacheDir, "quiet_prefs_${UUID.randomUUID()}.preferences_pb") }),
        )
        // The permission question is covered by the system; here it is already answered.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun show() {
        val viewModel = QuietViewModel(context, controller, QuietRepository(db, time.provider), preferences, time.dayClock)
        composeRule.setContent { PaintLifeTheme { QuietScreen(viewModel = viewModel) } }
        waitFor(hasTestTag("quiet_content"))
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(textId: Int) {
        val button = hasText(string(textId)) and hasClickAction()
        // Buttons merge their label, so look in the merged tree.
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(button).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(button)
        composeRule.onNode(button).performClick()
    }

    /** Taps Start and waits until the session is really running, before any time passes. */
    private fun startSession() {
        click(R.string.action_start)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("quiet_content")).fetchSemanticsNodes()
            controller.live.value?.session?.status == SessionStatus.RUNNING
        }
    }

    /** Every stored session that is open or counts toward minutes. */
    private fun stored(): List<Any> = runBlocking {
        db.quietDao().openSessions() + db.quietDao().observeFinished(day.minusDays(1), day.plusDays(1)).first()
    }

    @Test
    fun startRunsTheTimerAndStartsTheForegroundService() {
        show()
        startSession()

        waitFor(hasText(string(R.string.action_pause)))
        assertEquals(SessionStatus.RUNNING, controller.live.value?.session?.status)
        val service = shadowOf(context as Application).nextStartedService
        assertEquals(QuietTimerService::class.java.name, service?.component?.className)
    }

    @Test
    fun pauseAndResumeFromTheScreen() {
        show()
        startSession()
        monotonic.advance(60_000)
        click(R.string.action_pause)
        waitFor(hasText(string(R.string.quiet_subtitle_paused)))
        waitFor(hasText("4:00"))

        monotonic.advance(120_000)
        click(R.string.action_resume)
        waitFor(hasText(string(R.string.action_pause)))
        assertEquals(60_000L, controller.activeMillis(controller.live.value!!))
    }

    @Test
    fun cancelDiscardsAtOnceAndUndoBringsTheSessionBackPaused() {
        show()
        startSession()
        monotonic.advance(90_000)
        click(R.string.action_cancel_session)

        waitFor(hasText(string(R.string.quiet_cancelled)))
        waitFor(hasText(string(R.string.action_start)))
        assertEquals(emptyList<Any>(), stored())

        composeRule.onNodeWithText(string(R.string.action_undo)).performClick()

        waitFor(hasText(string(R.string.action_resume)))
        assertEquals(SessionStatus.PAUSED, controller.live.value?.session?.status)
        assertEquals(90_000L, controller.activeMillis(controller.live.value!!))
    }

    @Test
    fun finishEarlySavesAShorterSessionAndPaintsTodayInProportion() {
        show()
        startSession()
        monotonic.advance(150_000)
        click(R.string.action_finish)

        waitFor(hasText(string(R.string.quiet_notice_ended_early, "2:30")))
        waitFor(hasText(string(R.string.action_start)))
        assertNull(controller.completed.value)
        // Today's swatch says how much, and that it was not a full session.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("quiet_week"))
        waitFor(hasContentDescription(string(R.string.quiet_day_minutes, 2), substring = true))
        val today = runBlocking { withTimeout(5_000) { QuietRepository(db, time.provider).observeDay(day).first() } }
        assertEquals(0, today.completedSessions)
        assertEquals(150_000L, today.quietMillis)
    }

    @Test
    fun reachingFiveMinutesShowsTheCompletionAndDoneReturnsToReady() {
        show()
        startSession()
        monotonic.advance(300_000)
        // Never block the main thread on the controller: its database work may need Main.
        scope.launch { controller.checkDeadlines() }

        waitFor(hasText(string(R.string.quiet_subtitle_done)))
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("quiet_week"))
        waitFor(hasContentDescription(string(R.string.quiet_day_done), substring = true))
        click(R.string.action_done)
        waitFor(hasText(string(R.string.action_start)))
    }
}

/** QuietContent on its own: the notification note and its action. */
@RunWith(AndroidJUnit4::class)
class QuietContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val state = QuietUiState(
        phase = QuietPhase.Ready,
        remainingMillis = QuietUiState.FIVE_MINUTES,
        week = (0L..6L).map { QuietDay(LocalDate.of(2026, 11, 9).plusDays(it), completed = false) },
        monthCompletedSessions = 0,
        monthMinutes = 0,
    )

    @Test
    fun withNotificationsOffANoteOffersToTurnThemOn() {
        var opened = 0
        composeRule.setContent {
            PaintLifeTheme {
                QuietContent(state, {}, {}, {}, {}, {}, {}, notificationsOff = true, onOpenNotificationSettings = { opened++ })
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("quiet_notifications_off"))
        composeRule.onNodeWithTag("quiet_notifications_off").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.action_turn_on_notifications)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun withNotificationsOnThereIsNoNote() {
        composeRule.setContent { PaintLifeTheme { QuietContent(state, {}, {}, {}, {}, {}, {}) } }
        assertEquals(0, composeRule.onAllNodes(hasTestTag("quiet_notifications_off")).fetchSemanticsNodes().size)
    }
}
