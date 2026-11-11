package com.mauforge.paintlife.navigation

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
import com.mauforge.paintlife.mytime.ReminderNotifications
import com.mauforge.paintlife.mytime.StartAction
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.quiet.QuietSessionController
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import javax.inject.Inject

/**
 * A tap on a reminder notification, through the real activity and app shell with Hilt's test
 * graph: "Start now" starts what the reminder names and lands on its screen; a tap on the
 * notification itself only opens the app.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReminderTapTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var time: TimeRepository

    @Inject lateinit var quiet: QuietSessionController

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        waitForTag("today_content")
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** For conditions outside the screen: the main looper is run between checks, so the app's own work goes on. */
    private fun waitUntil(condition: () -> Boolean) = composeRule.waitUntil(10_000) {
        ShadowLooper.idleMainLooper()
        condition()
    }

    private fun running(): String? = blocking { time.observeCurrent().first() }?.categoryId

    /** Shows a notification with [id], as a reminder would, so its removal can be seen. */
    private fun post(id: Int) {
        ReminderNotifications.ensureChannel(context)
        notificationManager.notify(id, NotificationCompat.Builder(context, ReminderNotifications.CHANNEL).setSmallIcon(R.drawable.ic_bell).setContentTitle("Reminder").build())
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    private fun reminderIntent(id: Int = 7): Intent = Intent(context, MainActivity::class.java).putExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, id)

    private fun startNow(action: StartAction, category: String? = null, note: String? = null, id: Int = 7): Intent =
        reminderIntent(id).setAction("start")
            .putExtra(ReminderNotifications.EXTRA_START, action.name)
            .putExtra(ReminderNotifications.EXTRA_CATEGORY, category)
            .putExtra(ReminderNotifications.EXTRA_NOTE, note)

    private fun tap(intent: Intent) {
        composeRule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(composeRule.activity, intent) }
    }

    @Test
    fun startNowOnAPlannedActivityStartsItAndOpensMyTime() {
        post(7)

        tap(startNow(StartAction.NOW_DOING, "work", "deep work"))

        waitForTag("my_time_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.MY_TIME)).assertIsSelected()
        waitForTag("now_doing")
        assertEquals("work", running())
        assertEquals("deep work", blocking { time.observeCurrent().first() }?.note)
        // The notification that was tapped is gone.
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun startNowOnSleepStartsSleepAndShowsToday() {
        // From another tab, so the way back to Today is seen.
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.PROGRESS)).performClick()
        waitForTag("progress_content")

        tap(startNow(StartAction.SLEEP, "sleep"))

        waitUntil { running() == "sleep" }
        waitForTag("today_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("today_sleeping"))
        composeRule.onNodeWithTag("today_sleeping").assertExists()
    }

    @Test
    fun startNowOnQuietTimeStartsTheFiveMinutes() {
        tap(startNow(StartAction.QUIET))

        waitForTag("quiet_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.QUIET)).assertIsSelected()
        waitUntil { quiet.live.value != null }
        assertNotNull(quiet.live.value)
        blocking { quiet.cancel() }
    }

    @Test
    fun startNowOnExerciseOpensTheCounterWithTheSafetyGuidanceFirst() {
        tap(startNow(StartAction.EXERCISE, "exercise"))

        // First session ever: the guidance comes before the counter, exactly as from the Exercise tab.
        waitForTag("safety_guidance")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.EXERCISE)).assertIsSelected()
        assertNull(running())
    }

    @Test
    fun tappingTheNotificationItselfOnlyOpensTheApp() {
        post(7)

        tap(reminderIntent().setAction("open").putExtra(ReminderNotifications.EXTRA_OPEN, ReminderNotifications.OPEN_MY_TIME))

        waitForTag("my_time_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.MY_TIME)).assertIsSelected()
        assertNull(running())
        assertNull(quiet.live.value)
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)

        // The bedtime reminder opens Today.
        tap(reminderIntent(8).setAction("open").putExtra(ReminderNotifications.EXTRA_OPEN, ReminderNotifications.OPEN_TODAY))
        waitForTag("today_content")
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
        assertNull(running())
    }

    @Test
    fun theSameTapDeliveredAgainStartsNothingASecondTime() {
        val intent = startNow(StartAction.NOW_DOING, "work")
        tap(intent)
        waitUntil { running() == "work" }
        assertTrue(blocking { time.discardCurrent() })

        tap(intent)
        composeRule.waitForIdle()

        assertNull(running())
    }

    @Test
    fun anAppOpenedWithoutAReminderStartsNothing() {
        tap(Intent(context, MainActivity::class.java).putExtra(ReminderNotifications.EXTRA_START, StartAction.SLEEP.name))
        composeRule.waitForIdle()

        assertNull(running())
        composeRule.onNodeWithTag(navItemTestTag(TopLevelDestination.TODAY)).assertIsSelected()
    }
}
