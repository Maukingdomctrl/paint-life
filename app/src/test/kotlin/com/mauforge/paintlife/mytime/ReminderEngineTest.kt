package com.mauforge.paintlife.mytime

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.MainActivity
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The reminder engine on a real (in-memory) database with a hand-driven clock and Robolectric's
 * alarm and notification managers. "The alarm fires", "the phone restarted", "the time zone
 * changed" and "the clock was set" all reach the engine as [ReminderEngine.sync] (see
 * [ReminderReceiver], tested in ReminderReceiverTest), so that is what these tests call.
 */
@RunWith(AndroidJUnit4::class)
class ReminderEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    /** A Monday. */
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(8, 0), london)

    private lateinit var db: PaintLifeDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var scope: CoroutineScope
    private lateinit var engine: ReminderEngine
    private lateinit var entries: TimeRepository
    private lateinit var schedules: ScheduleRepository
    private lateinit var reminders: ReminderRepository

    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        preferences = testPreferences(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        engine = ReminderEngine(context, db, preferences, time.provider, scope)
        entries = TimeRepository(db, time.provider)
        schedules = ScheduleRepository(db, time.provider)
        reminders = ReminderRepository(db, preferences, time.provider)
        // As in the app, where the engine runs once when the process starts: from here on, reminders count.
        sync()
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    // Helpers

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun at(h: Int, m: Int = 0, date: LocalDate = day): Instant = date.atTime(h, m).atZone(london).toInstant()

    private fun sync() = blocking { engine.sync() }

    /** Moves the clock to [h]:[m] London time and lets the alarm "fire". */
    private fun fireAt(h: Int, m: Int = 0, date: LocalDate = day) {
        time.clock.instant = at(h, m, date)
        sync()
    }

    private fun plan(category: String, start: Instant, lead: Int?, note: String? = null, id: String? = null, minutes: Long = 60): TimeEntryEntity {
        val draft = EntryDraft(id, TimeEntryKind.PLANNED, category, note, start, start.plus(Duration.ofMinutes(minutes)), zone = london, reminderMinutes = lead)
        return (blocking { entries.save(draft, Resolution.None) } as SaveResult.Saved).entry
    }

    /** Approves a schedule with one Work block, 09:00-17:00 every day, and returns the block's id. */
    private fun approveWork(): String {
        val proposal = ApprovedProposal("Week", listOf(ProposalBlock("x", "work", 9 * 60, 17 * 60, ScheduleRules.ALL_DAYS)), null, null)
        assertTrue(blocking { schedules.approve(proposal) } is ApproveResult.Approved)
        return blocking { db.backupDao().scheduleBlocks() }.single().id
    }

    private fun scheduled(): List<ShadowAlarmManager.ScheduledAlarm> = shadowOf(alarmManager).scheduledAlarms.toList()

    /** When the one alarm is set for; null when there is none. There is never more than one. */
    private fun alarmAt(): Instant? {
        val all = scheduled()
        assertTrue("more than one alarm: ${all.size}", all.size <= 1)
        return all.singleOrNull()?.let { Instant.ofEpochMilli(it.triggerAtMs) }
    }

    private fun awaitAlarm(expected: Instant?) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val all = scheduled()
            if (all.size <= 1 && all.singleOrNull()?.let { Instant.ofEpochMilli(it.triggerAtMs) } == expected) return
            Thread.sleep(10)
        }
        assertEquals(expected, alarmAt())
    }

    private fun shown(): List<Notification> = shadowOf(notificationManager).allNotifications

    private fun titles(): List<String> = shown().map { it.extras.getCharSequence(Notification.EXTRA_TITLE).toString() }.sorted()

    private fun actions(notification: Notification): List<String> = notification.actions.orEmpty().map { it.title.toString() }

    private fun actionIntent(notification: Notification, title: Int): Intent =
        shadowOf(notification.actions.first { it.title.toString() == context.getString(title) }.actionIntent).savedIntent

    private fun inMinutes(name: String, minutes: Int): String = context.resources.getQuantityString(R.plurals.reminder_in_minutes, minutes, name, minutes)

    /** The phone restarts: alarms and notifications are gone, and a new process starts on the same data. */
    private fun restart() {
        scheduled().forEach { alarmManager.cancel(it.operation!!) }
        notificationManager.cancelAll()
        assertNull(alarmAt())
        engine = ReminderEngine(context, db, preferences, time.provider, scope)
    }

    // Setting the alarm

    @Test
    fun nothingSetMeansNoAlarmAndNoNotificationEver() {
        plan("work", at(15), lead = null)
        approveWork()

        sync()
        assertNull(alarmAt())

        // A week passes with the engine waking for every system event: still nothing.
        (0L..7L).forEach { fireAt(23, 0, day.plusDays(it)) }
        assertNull(alarmAt())
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun oneExactAlarmIsSetForTheEarliestReminder() {
        plan("family", at(19), lead = 30)
        plan("work", at(15), lead = 10)

        sync()

        val alarm = scheduled().single()
        assertEquals(at(14, 50), Instant.ofEpochMilli(alarm.triggerAtMs))
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun withoutExactAlarmAccessTheAlarmIsInexactAndTheReminderStillComes() {
        assertTrue("needs Android 12+ to refuse exact alarms", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(ReminderNotifications.canScheduleExact(context))
        plan("work", at(15), lead = 10)
        plan("family", at(19), lead = 30)

        sync()

        // Same moment, but Android may deliver it late.
        val alarm = scheduled().single()
        assertEquals(at(14, 50), Instant.ofEpochMilli(alarm.triggerAtMs))
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue(alarm.isAllowWhileIdle)

        // It arrives three minutes late: the reminder is shown with the time that is left.
        fireAt(14, 53)
        assertEquals(listOf(inMinutes("Work", 7)), titles())
        val next = scheduled().single()
        assertEquals(at(18, 30), Instant.ofEpochMilli(next.triggerAtMs))
        assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, next.windowLengthMs)

        // The user allows exact timing in Android's settings: the next alarm becomes exact.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        sync()
        val exact = scheduled().single()
        assertEquals(at(18, 30), Instant.ofEpochMilli(exact.triggerAtMs))
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, exact.windowLengthMs)
    }

    // Showing a reminder

    @Test
    fun aPlannedReminderIsShownOnceWithStartNowAndSnooze() {
        plan("work", at(9), lead = 10, note = "deep work")
        sync()
        assertEquals(at(8, 50), alarmAt())

        fireAt(8, 50)

        val notification = shown().single()
        assertEquals(inMinutes("Work", 10), titles().single())
        assertEquals(ReminderNotifications.CHANNEL, notification.channelId)
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        // The lock-screen version has the time but not the note.
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("deep work"))
        assertEquals("09:00–10:00", notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertNull(notification.fullScreenIntent)
        assertEquals(listOf(context.getString(R.string.action_start_now), context.getString(R.string.action_snooze)), actions(notification))
        assertNull(alarmAt())

        // However often the engine wakes afterwards, it is not shown again.
        notificationManager.cancelAll()
        sync()
        fireAt(8, 55)
        fireAt(9, 30)
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun theReminderChannelIsGentleAndNeverBypassesDoNotDisturb() {
        plan("work", at(9), lead = 10)
        fireAt(8, 50)

        val channel = notificationManager.getNotificationChannel(ReminderNotifications.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertFalse(channel.canBypassDnd())
    }

    @Test
    fun startNowCarriesWhatToStartIntoTheApp() {
        plan("work", at(9), lead = 10, note = "deep work")
        plan("exercise", at(11), lead = 5)
        plan("sleep", at(23), lead = 30)
        blocking { reminders.addOneOff("Sit quietly", at(13), StartAction.QUIET) }

        fun startOf(notification: Notification) = actionIntent(notification, R.string.action_start_now)
        fun only(): Notification = shown().single().also { notificationManager.cancelAll() }

        fireAt(8, 50)
        val work = startOf(only())
        assertEquals(MainActivity::class.java.name, work.component?.className)
        assertEquals(StartAction.NOW_DOING.name, work.getStringExtra(ReminderNotifications.EXTRA_START))
        assertEquals("work", work.getStringExtra(ReminderNotifications.EXTRA_CATEGORY))
        assertEquals("deep work", work.getStringExtra(ReminderNotifications.EXTRA_NOTE))
        assertTrue(work.hasExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID))

        fireAt(10, 55)
        assertEquals(StartAction.EXERCISE.name, startOf(only()).getStringExtra(ReminderNotifications.EXTRA_START))

        fireAt(13, 0)
        assertEquals(StartAction.QUIET.name, startOf(only()).getStringExtra(ReminderNotifications.EXTRA_START))

        fireAt(22, 30)
        val sleep = startOf(only())
        assertEquals(StartAction.SLEEP.name, sleep.getStringExtra(ReminderNotifications.EXTRA_START))
        assertEquals("sleep", sleep.getStringExtra(ReminderNotifications.EXTRA_CATEGORY))
    }

    @Test
    fun aReminderIsNotShownWhenItsActivityIsAlreadyRunning() {
        plan("work", at(9), lead = 10)
        plan("family", at(12), lead = 10)
        sync()
        time.clock.instant = at(8, 30)
        assertTrue(blocking { entries.startCurrent("work", null) } is RunResult.Done)

        fireAt(8, 50)
        assertEquals(emptyList<String>(), titles())

        // Something else is planned later: that one is shown.
        fireAt(11, 50)
        assertEquals(listOf(inMinutes("Family", 10)), titles())
    }

    @Test
    fun withNotificationsRefusedNothingIsShownAndTheNextAlarmIsStillSet() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(ReminderNotifications.canNotify(context))
        plan("work", at(9), lead = 10)
        plan("family", at(19), lead = 30)

        fireAt(8, 50)

        assertEquals(emptyList<String>(), titles())
        assertEquals(at(18, 30), alarmAt())
    }

    // One-off reminders

    @Test
    fun aOneOffReminderFiresOnceAtItsTimeAndIsThenRemoved() {
        val added = blocking { reminders.addOneOff("  Call the bank ", at(11, 30).plusSeconds(40), StartAction.NONE) }
        assertEquals("Call the bank", (added as AddReminderResult.Added).reminder.title)
        sync()
        // Stored to the minute.
        assertEquals(at(11, 30), alarmAt())

        fireAt(11, 30)

        val notification = shown().single()
        assertEquals("Call the bank", titles().single())
        // Nothing to start: Snooze only.
        assertEquals(listOf(context.getString(R.string.action_snooze)), actions(notification))
        assertEquals(emptyList<ReminderEntity>(), blocking { db.reminderDao().all() })
        assertNull(alarmAt())

        notificationManager.cancelAll()
        fireAt(11, 31)
        fireAt(11, 30, day.plusDays(1))
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun aOneOffNeedsATitleAndATimeStillAhead() {
        assertEquals(AddReminderResult.NoTitle, blocking { reminders.addOneOff("   ", at(11), StartAction.NONE) })
        assertEquals(AddReminderResult.NotAhead, blocking { reminders.addOneOff("Late", at(8), StartAction.NONE) })
        assertEquals(AddReminderResult.NotAhead, blocking { reminders.addOneOff("Late", at(7, 59), StartAction.NONE) })
        assertEquals(emptyList<ReminderEntity>(), blocking { db.reminderDao().all() })

        // "Now doing" needs a category, which a one-off does not have.
        val added = blocking { reminders.addOneOff("x".repeat(80), at(8, 1), StartAction.NOW_DOING) } as AddReminderResult.Added
        assertEquals(StartAction.NONE, added.reminder.action)
        assertEquals(ReminderRules.TITLE_MAX, added.reminder.title?.length)
        assertEquals(london, added.reminder.zoneId)
    }

    // Snooze

    @Test
    fun snoozeBringsTheReminderBackFiveMinutesLater() {
        plan("work", at(9), lead = 10, note = "deep work")
        fireAt(8, 50)
        val snooze = actionIntent(shown().single(), R.string.action_snooze)
        assertEquals(ReminderReceiver::class.java.name, snooze.component?.className)
        assertEquals(ReminderNotifications.ACTION_SNOOZE, snooze.action)

        // Tapped a minute later.
        time.clock.instant = at(8, 51)
        blocking { engine.snooze(snooze) }

        assertEquals(emptyList<String>(), titles())
        val stored = blocking { db.reminderDao().all() }.single()
        assertEquals(ReminderKind.SNOOZE, stored.kind)
        assertEquals(at(8, 56), stored.fireAt)
        assertEquals(at(9) to at(10), stored.activityStartAt to stored.activityEndAt)
        assertEquals(at(8, 56), alarmAt())

        fireAt(8, 56)

        val again = shown().single()
        assertEquals(inMinutes("Work", 4), titles().single())
        assertEquals(StartAction.NOW_DOING.name, actionIntent(again, R.string.action_start_now).getStringExtra(ReminderNotifications.EXTRA_START))
        assertEquals("deep work", actionIntent(again, R.string.action_start_now).getStringExtra(ReminderNotifications.EXTRA_NOTE))
        assertEquals(emptyList<ReminderEntity>(), blocking { db.reminderDao().all() })
        assertNull(alarmAt())
    }

    @Test
    fun aSnoozedReminderCanBeSnoozedAgainAndAOneOffKeepsItsTitle() {
        blocking { reminders.addOneOff("Call the bank", at(11), StartAction.NONE) }
        fireAt(11, 0)

        blocking { engine.snooze(actionIntent(shown().single(), R.string.action_snooze)) }
        fireAt(11, 5)
        assertEquals(listOf("Call the bank"), titles())

        blocking { engine.snooze(actionIntent(shown().single(), R.string.action_snooze)) }
        assertEquals(emptyList<String>(), titles())
        assertEquals(at(11, 10), alarmAt())
        fireAt(11, 10)
        assertEquals(listOf("Call the bank"), titles())
    }

    @Test
    fun aSnoozeSurvivesARestart() {
        plan("work", at(9), lead = 30)
        fireAt(8, 30)
        blocking { engine.snooze(actionIntent(shown().single(), R.string.action_snooze)) }
        assertEquals(at(8, 35), alarmAt())

        restart()
        sync()

        assertEquals(at(8, 35), alarmAt())
        fireAt(8, 35)
        assertEquals(listOf(inMinutes("Work", 25)), titles())
    }

    // Phone restart

    @Test
    fun afterARestartTheAlarmIsSetAgainFromWhatIsStored() {
        plan("work", at(15), lead = 10)
        blocking { reminders.addOneOff("Call the bank", at(11, 30), StartAction.NONE) }
        blocking { reminders.setBedtime(22 * 60 + 30) }
        sync()
        assertEquals(at(11, 30), alarmAt())

        restart()
        // The phone was off for an hour.
        fireAt(9, 0)

        assertEquals(at(11, 30), alarmAt())
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, scheduled().single().windowLengthMs)
        assertEquals(emptyList<String>(), titles())

        // And the day goes on as set.
        fireAt(11, 30)
        assertEquals(at(14, 50), alarmAt())
        fireAt(14, 50)
        assertEquals(at(22, 30), alarmAt())
        assertEquals(listOf("Call the bank", inMinutes("Work", 10)), titles())
    }

    @Test
    fun remindersThatCameDueWhileThePhoneWasOffAreShownOnlyIfStillUseful() {
        plan("work", at(9), lead = 30)
        plan("family", at(8, 20), lead = 10, minutes = 30)
        blocking { reminders.addOneOff("Too old", at(8, 10), StartAction.NONE) }
        blocking { reminders.addOneOff("Still fresh", at(8, 35), StartAction.NONE) }
        sync()
        assertEquals(at(8, 10), alarmAt())

        // Off at 08:05, on again at 08:45.
        restart()
        fireAt(8, 45)

        // Work has not started yet; Family started at 08:20; "Too old" is 35 minutes late.
        assertEquals(listOf("Still fresh", inMinutes("Work", 15)), titles())
        assertEquals(emptyList<ReminderEntity>(), blocking { db.reminderDao().all() })
        assertNull(alarmAt())

        // A second wake-up (the app is opened as well) shows nothing more.
        notificationManager.cancelAll()
        restart()
        sync()
        assertEquals(emptyList<String>(), titles())
    }

    // Time-zone change

    @Test
    fun aPlannedEntryKeepsItsMomentWhenTheTimeZoneChanges() {
        plan("work", at(15), lead = 10)
        sync()
        assertEquals(at(14, 50), alarmAt())

        time.zone = tokyo
        sync()

        assertEquals(at(14, 50), alarmAt())
        fireAt(14, 50)
        assertEquals(listOf(inMinutes("Work", 10)), titles())
    }

    @Test
    fun scheduleAndBedtimeRemindersMoveToTheNewZonesWallClock() {
        val block = approveWork()
        assertTrue(blocking { entries.setBlockReminder(block, 10) })
        sync()
        assertEquals(at(8, 50), alarmAt())

        // 08:00 in London is 17:00 in Tokyo: the next 08:50 on the clock is tomorrow morning there.
        time.zone = tokyo
        sync()
        assertEquals(LocalDateTime.of(2026, 11, 10, 8, 50).atZone(tokyo).toInstant(), alarmAt())

        blocking { reminders.setBedtime(22 * 60 + 30) }
        sync()
        assertEquals(LocalDateTime.of(2026, 11, 9, 22, 30).atZone(tokyo).toInstant(), alarmAt())

        // Back home before either came due: London's wall clock again.
        time.zone = london
        sync()
        assertEquals(at(8, 50), alarmAt())
        fireAt(8, 50)
        assertEquals(listOf(inMinutes("Work", 10)), titles())
        assertEquals(at(22, 30), alarmAt())
    }

    // Daylight saving time

    @Test
    fun bedtimeStaysAtItsWallClockTimeAcrossTheClocksGoingForward() {
        // Europe/London: 01:00 becomes 02:00 on 28 March 2027.
        val saturday = LocalDate.of(2027, 3, 27)
        time.setLocal(saturday.atTime(20, 0))
        blocking { reminders.setBedtime(22 * 60 + 30) }
        sync()
        assertEquals(Instant.parse("2027-03-27T22:30:00Z"), alarmAt())

        time.clock.instant = Instant.parse("2027-03-27T22:30:00Z")
        sync()

        val notification = shown().single()
        assertEquals(listOf(context.getString(R.string.reminder_bedtime_title)), titles())
        assertEquals(StartAction.SLEEP.name, actionIntent(notification, R.string.action_start_now).getStringExtra(ReminderNotifications.EXTRA_START))
        // 22:30 the next evening is 23 hours later.
        assertEquals(Instant.parse("2027-03-28T21:30:00Z"), alarmAt())

        notificationManager.cancelAll()
        time.clock.instant = Instant.parse("2027-03-28T21:30:00Z")
        sync()
        assertEquals(listOf(context.getString(R.string.reminder_bedtime_title)), titles())
        assertEquals(Instant.parse("2027-03-29T21:30:00Z"), alarmAt())
    }

    @Test
    fun aScheduleBlockStaysAtItsWallClockTimeAcrossTheClocksGoingBack() {
        // Europe/London: 02:00 becomes 01:00 on 31 October 2027.
        time.setLocal(LocalDate.of(2027, 10, 30).atTime(8, 0))
        assertTrue(blocking { entries.setBlockReminder(approveWork(), 30) })
        sync()
        assertEquals(Instant.parse("2027-10-30T07:30:00Z"), alarmAt())

        time.clock.instant = Instant.parse("2027-10-30T07:30:00Z")
        sync()

        assertEquals(listOf(inMinutes("Work", 30)), titles())
        // 08:30 on Sunday's clock is 25 hours later.
        assertEquals(Instant.parse("2027-10-31T08:30:00Z"), alarmAt())
    }

    // The clock is set by hand

    @Test
    fun whenTheClockIsSetBackAReminderWhoseTimeComesRoundAgainIsShownAgain() {
        plan("work", at(9), lead = 10)
        fireAt(8, 50)
        assertEquals(listOf(inMinutes("Work", 10)), titles())
        assertNull(alarmAt())
        notificationManager.cancelAll()

        // The clock is put back to 08:40.
        fireAt(8, 40)
        assertEquals(emptyList<String>(), titles())
        assertEquals(at(8, 50), alarmAt())

        fireAt(8, 50)
        assertEquals(listOf(inMinutes("Work", 10)), titles())
    }

    @Test
    fun whenTheClockIsSetForwardPastAPlannedActivityItsReminderIsDropped() {
        plan("work", at(9), lead = 10)
        plan("family", at(19), lead = 30)
        sync()

        fireAt(12, 0)

        assertEquals(emptyList<String>(), titles())
        assertEquals(at(18, 30), alarmAt())
    }

    // Deleting or editing what a reminder belongs to (the engine watches the data, as in the app)

    @Test
    fun deletingAPlannedEntryCancelsItsReminderAndUndoBringsItBack() {
        engine.start()
        val entry = plan("work", at(15), lead = 10)
        awaitAlarm(at(14, 50))

        val change = blocking { entries.delete(entry.id) }!!
        awaitAlarm(null)

        assertTrue(blocking { entries.undo(change) })
        awaitAlarm(at(14, 50))

        blocking { entries.delete(entry.id) }
        awaitAlarm(null)
        // Its time comes: nothing is shown.
        fireAt(14, 50)
        fireAt(15, 30)
        assertEquals(emptyList<String>(), titles())
        assertNull(alarmAt())
    }

    @Test
    fun editingAPlannedEntryMovesOrCancelsItsReminder() {
        engine.start()
        val entry = plan("work", at(15), lead = 10)
        awaitAlarm(at(14, 50))

        // A new time.
        plan("work", at(16), lead = 10, id = entry.id)
        awaitAlarm(at(15, 50))

        // A new lead time.
        plan("work", at(16), lead = 30, id = entry.id)
        awaitAlarm(at(15, 30))

        // Reminder switched off.
        plan("work", at(16), lead = null, id = entry.id)
        awaitAlarm(null)
        assertNull(blocking { entries.entry(entry.id) }?.reminderMinutes)

        // And on again.
        plan("work", at(16), lead = 5, id = entry.id)
        awaitAlarm(at(15, 55))

        // The old times never fire.
        fireAt(14, 50)
        fireAt(15, 30)
        assertEquals(emptyList<String>(), titles())
        fireAt(15, 55)
        assertEquals(listOf(inMinutes("Work", 5)), titles())
    }

    @Test
    fun anEntryMovedSoCloseThatItsLeadTimeHasPassedDoesNotRemind() {
        engine.start()
        val entry = plan("work", at(15), lead = 30)
        awaitAlarm(at(14, 30))

        // At 08:00 it is moved to 08:20: half an hour before that has gone.
        plan("work", at(8, 20), lead = 30, id = entry.id)
        awaitAlarm(null)

        fireAt(8, 10)
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun onlyAPlannedEntryCanRemindAndOnlyAtTheOfferedLeadTimes() {
        val planned = EntryDraft(null, TimeEntryKind.PLANNED, "work", null, at(15), at(16), zone = london, reminderMinutes = 7)
        assertEquals(SaveResult.Invalid, blocking { entries.save(planned, Resolution.None) })

        // An actual entry is in the past: a reminder asked for it is simply not kept.
        val actual = EntryDraft(null, TimeEntryKind.ACTUAL, "work", null, at(6), at(7), zone = london, reminderMinutes = 10)
        assertNull((blocking { entries.save(actual, Resolution.None) } as SaveResult.Saved).entry.reminderMinutes)

        sync()
        assertNull(alarmAt())
    }

    @Test
    fun switchingABlocksReminderOffCancelsItOnEveryDay() {
        engine.start()
        val block = approveWork()
        awaitAlarm(null)

        assertTrue(blocking { entries.setBlockReminder(block, 10) })
        awaitAlarm(at(8, 50))
        assertFalse(blocking { entries.setBlockReminder(block, 20) })
        assertFalse(blocking { entries.setBlockReminder("no-such-block", 10) })

        assertTrue(blocking { entries.setBlockReminder(block, null) })
        awaitAlarm(null)
        (0L..3L).forEach { fireAt(8, 50, day.plusDays(it)) }
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun changeForThisDayKeepsTheBlocksReminderAndDeletingThatEntryCancelsIt() {
        engine.start()
        assertTrue(blocking { entries.setBlockReminder(approveWork(), 10) })
        awaitAlarm(at(8, 50))

        val copied = blocking { entries.copyScheduleDay(day, london) }!!.after.single()
        assertEquals(10, copied.reminderMinutes)
        awaitAlarm(at(8, 50))

        // The day's own plan is moved an hour later: so is today's reminder.
        plan("work", at(10), lead = 10, id = copied.id, minutes = 420)
        awaitAlarm(at(9, 50))

        // Today's plan is replaced by one without a reminder: the next reminder is tomorrow's block.
        plan("work", at(10), lead = null, id = copied.id, minutes = 420)
        awaitAlarm(at(8, 50, day.plusDays(1)))
    }

    @Test
    fun deletingAOneOffCancelsItAndUndoBringsItBack() {
        engine.start()
        val added = blocking { reminders.addOneOff("Call the bank", at(11, 30), StartAction.NONE) } as AddReminderResult.Added
        awaitAlarm(at(11, 30))

        val removed = blocking { reminders.delete(added.reminder.id) }!!
        awaitAlarm(null)

        assertTrue(blocking { reminders.restore(removed) })
        awaitAlarm(at(11, 30))

        // Undo after its time has passed does not bring it back.
        blocking { reminders.delete(added.reminder.id) }
        awaitAlarm(null)
        time.clock.instant = at(11, 30)
        assertFalse(blocking { reminders.restore(removed) })
        sync()
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun switchingBedtimeOffCancelsItAndMovingItMovesTheAlarm() {
        engine.start()
        blocking { reminders.setBedtime(22 * 60 + 30) }
        awaitAlarm(at(22, 30))

        blocking { reminders.setBedtime(21 * 60) }
        awaitAlarm(at(21, 0))

        blocking { reminders.setBedtime(null) }
        awaitAlarm(null)
        fireAt(21, 0)
        fireAt(22, 30)
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun aBedtimeSetAfterTonightsTimeStartsTomorrow() {
        time.clock.instant = at(23, 0)
        blocking { reminders.setBedtime(22 * 60 + 30) }

        sync()

        assertEquals(emptyList<String>(), titles())
        assertEquals(at(22, 30, day.plusDays(1)), alarmAt())
    }
}
