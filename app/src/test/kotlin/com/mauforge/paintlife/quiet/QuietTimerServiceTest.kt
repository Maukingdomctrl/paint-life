package com.mauforge.paintlife.quiet

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager
import com.mauforge.paintlife.testing.TestMonotonic
import javax.inject.Inject

/** The foreground service with Hilt's test graph: notification, wake lock, alert and stopping. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class QuietTimerServiceTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var controller: QuietSessionController

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        TestMonotonic.offsetMillis = 0L
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { withTimeout(5_000) { controller.loaded.first { it } } }
    }

    @After
    fun tearDown() {
        TestMonotonic.offsetMillis = 0L
    }

    private fun settle(until: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!until() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue(until())
    }

    private fun wakeLock(): PowerManager.WakeLock? = ShadowPowerManager.getLatestWakeLock()

    @Test
    fun runsInTheForegroundWithAWakeLockOnlyWhileTheTimerRuns() {
        runBlocking { controller.start() }
        val service = Robolectric.buildService(QuietTimerService::class.java).create().startCommand(0, 1).get()
        settle { wakeLock()?.isHeld == true }

        val shadow = shadowOf(service)
        assertFalse(shadow.isForegroundStopped)
        val ongoing = shadow.lastForegroundNotification
        assertNotNull(ongoing)
        assertEquals(QuietNotifications.CHANNEL_RUNNING, ongoing.channelId)
        assertTrue(ongoing.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, NotificationCompat.getActionCount(ongoing))

        controller.pause()
        settle { wakeLock()?.isHeld == false }

        controller.resume()
        settle { wakeLock()?.isHeld == true }

        runBlocking { controller.cancel() }
        settle { shadow.isStoppedBySelf }
        assertFalse(wakeLock()!!.isHeld)
    }

    @Test
    fun pauseFromTheNotificationActionPausesTheSession() {
        runBlocking { controller.start() }
        val serviceController = Robolectric.buildService(QuietTimerService::class.java).create().startCommand(0, 1)

        serviceController.withIntent(
            Intent(context, QuietTimerService::class.java).setAction(QuietTimerService.ACTION_PAUSE),
        ).startCommand(0, 2)

        assertTrue(controller.live.value!!.isPaused)
    }

    @Test
    fun completionAtFiveMinutesPostsTheAlertAndStopsTheService() {
        runBlocking { controller.start() }
        val service = Robolectric.buildService(QuietTimerService::class.java).create().startCommand(0, 1).get()
        settle { wakeLock()?.isHeld == true }

        TestMonotonic.offsetMillis += QuietRules.TARGET_MILLIS
        runBlocking { withTimeout(5_000) { controller.checkDeadlines() } }

        settle { shadowOf(service).isStoppedBySelf }
        val alert = shadowOf(notifications).getNotification(QuietNotifications.FINISHED_ID)
        assertNotNull(alert)
        assertEquals(QuietNotifications.CHANNEL_FINISHED, alert.channelId)
        assertFalse(wakeLock()!!.isHeld)
    }

    @Test
    fun theAlertChannelUsesSoundAndVibrationAndTheRunningChannelIsSilent() {
        QuietNotifications.ensureChannels(context)
        val running = notifications.getNotificationChannel(QuietNotifications.CHANNEL_RUNNING)
        val finished = notifications.getNotificationChannel(QuietNotifications.CHANNEL_FINISHED)
        assertEquals(null, running.sound)
        assertFalse(running.shouldVibrate())
        assertNotNull(finished.sound)
        assertTrue(finished.shouldVibrate())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, finished.importance)
    }
}
