package com.mauforge.paintlife.mytime

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * The manifest receiver with Hilt's test graph (real clock, in-memory database): the system
 * events after which alarms must be set again really reach the engine, and so does Snooze.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ReminderReceiverTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var engine: ReminderEngine

    @Inject lateinit var db: PaintLifeDatabase

    @Inject lateinit var time: TimeProvider

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        hiltRule.inject()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun alarms(): List<ShadowAlarmManager.ScheduledAlarm> = shadowOf(alarmManager).scheduledAlarms.toList()

    private fun await(what: String, until: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!until() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        assertTrue(what, until())
    }

    /** A one-off two hours from now (real time), so there is something to set an alarm for. */
    private fun addOneOff(): Instant {
        val added = blocking { reminders.addOneOff("Call the bank", time.now().plus(Duration.ofHours(2)), StartAction.NONE) }
        return (added as AddReminderResult.Added).reminder.fireAt
    }

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action).setPackage(context.packageName))
    }

    @Test
    fun theReceiverIsDeclaredForEveryEventThatLosesOrMovesAlarmsAndIsNotExported() {
        val receiver = context.packageManager.getReceiverInfo(
            android.content.ComponentName(context, ReminderReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(receiver.exported)

        SYSTEM_EVENTS.forEach { action ->
            val found = context.packageManager.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            assertEquals(action, listOf(ReminderReceiver::class.java.name), found.map { it.activityInfo.name })
        }
    }

    @Test
    fun thePermissionsAreOnlyTheOnesAgreed() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            .filter { it.startsWith("android.permission.") }

        assertEquals(
            listOf(
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.SCHEDULE_EXACT_ALARM,
                Manifest.permission.WAKE_LOCK,
            ),
            requested.sorted(),
        )
    }

    @Test
    fun aRestartATimeZoneChangeAClockChangeAndAnUpdateEachSetTheAlarmAgain() {
        val fireAt = addOneOff()
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarms())

        SYSTEM_EVENTS.forEach { action ->
            // The alarm is gone (as after a restart), or stale.
            alarms().forEach { alarmManager.cancel(it.operation!!) }
            assertEquals(action, 0, alarms().size)

            broadcast(action)

            await("alarm set again after $action") { alarms().size == 1 }
            assertEquals(action, fireAt, Instant.ofEpochMilli(alarms().single().triggerAtMs))
        }
    }

    @Test
    fun whenExactAlarmAccessIsTakenAwayTheNextAlarmIsInexact() {
        val fireAt = addOneOff()
        blocking { engine.sync() }
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarms().single().windowLengthMs)

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        broadcast(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)

        await("alarm made inexact") { alarms().singleOrNull()?.windowLengthMs?.let { it != ShadowAlarmManager.WINDOW_EXACT } == true }
        assertEquals(fireAt, Instant.ofEpochMilli(alarms().single().triggerAtMs))
    }

    @Test
    fun theAlarmItselfReachesTheEngine() {
        addOneOff()
        blocking { engine.sync() }
        val operation = alarms().single().operation!!
        val intent = shadowOf(operation).savedIntent
        assertEquals(ReminderReceiver::class.java.name, intent.component?.className)
        assertEquals(ReminderReceiver.ACTION_FIRE, intent.action)
        assertTrue(shadowOf(operation).isBroadcastIntent)

        // Fired early by hand: nothing is due, and the alarm is simply set again.
        alarms().forEach { alarmManager.cancel(it.operation!!) }
        operation.send()

        await("alarm set again") { alarms().size == 1 }
        assertEquals(emptyList<Notification>(), shadowOf(notificationManager).allNotifications)
    }

    @Test
    fun tappingSnoozeOnTheNotificationStoresTheSnoozeAndSetsItsAlarm() {
        // A reminder that comes due a moment after the engine last ran (inserted directly: the
        // repository only takes whole minutes ahead).
        blocking { engine.sync() }
        val now = time.now()
        blocking {
            db.reminderDao().insert(
                ReminderEntity(
                    id = "due",
                    kind = ReminderKind.ONE_OFF,
                    title = "Call the bank",
                    categoryId = null,
                    note = null,
                    fireAt = now.plusMillis(1),
                    zoneId = time.zone(),
                    activityStartAt = null,
                    activityEndAt = null,
                    action = StartAction.NONE,
                    createdAt = now,
                    createdZoneId = time.zone(),
                ),
            )
        }
        Thread.sleep(50)
        blocking { engine.sync() }
        val notification = shadowOf(notificationManager).allNotifications.single()
        val snooze = notification.actions.single { it.title.toString() == context.getString(R.string.action_snooze) }

        val tappedAt = time.now()
        snooze.actionIntent.send()

        await("snooze stored") { blocking { db.reminderDao().all() }.any { it.kind == ReminderKind.SNOOZE } }
        await("notification removed") { shadowOf(notificationManager).allNotifications.isEmpty() }
        val stored = blocking { db.reminderDao().all() }.single()
        assertEquals("Call the bank", stored.title)
        val wait = Duration.between(tappedAt, stored.fireAt)
        assertTrue("snoozed for $wait", wait >= ReminderRules.SNOOZE && wait < ReminderRules.SNOOZE.plusSeconds(30))
        await("snooze alarm set") { alarms().singleOrNull()?.triggerAtMs == stored.fireAt.toEpochMilli() }
        assertNull(blocking { db.reminderDao().reminder("due") })
    }

    private companion object {
        val SYSTEM_EVENTS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
