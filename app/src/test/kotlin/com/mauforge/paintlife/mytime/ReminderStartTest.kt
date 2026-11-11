package com.mauforge.paintlife.mytime

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.quiet.QuietSessionController
import com.mauforge.paintlife.quiet.QuietTimerService
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A reminder's "Start now": each action goes through the same path as the button on its screen,
 * on a real (in-memory) database. (The tap itself, through MainActivity, is in ReminderTapTest.)
 */
@RunWith(AndroidJUnit4::class)
class ReminderStartTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(14, 0), zone)

    private lateinit var db: PaintLifeDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var entries: TimeRepository
    private lateinit var launch: MyTimeLaunch
    private lateinit var quiet: QuietSessionController
    private lateinit var start: ReminderStart

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        entries = TimeRepository(db, time.provider)
        launch = MyTimeLaunch()
        quiet = QuietSessionController(QuietRepository(db, time.provider), FakeSessionClock(now = 1_000_000, boot = 3).clock, scope)
        start = ReminderStart(context, entries, launch, quiet)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(60_000) { block() } }

    private fun at(h: Int, m: Int = 0): Instant = day.atTime(h, m).atZone(zone).toInstant()

    private fun startNow(action: StartAction, category: String? = null, note: String? = null): StartDestination =
        blocking { start.start(action, category, note) }

    private fun running(): CurrentActivityEntity? = blocking { entries.observeCurrent().first() }

    private fun saved(): List<TimeEntryEntity> = blocking { db.backupDao().timeEntries() }

    private fun openSessions(): Pair<Int, Int> = blocking { db.reminderDao().openExerciseSessions() to db.reminderDao().openQuietSessions() }

    @Test
    fun aReminderThatStartsNothingJustOpensMyTime() {
        assertEquals(StartDestination.MyTime, startNow(StartAction.NONE))

        assertNull(running())
        assertEquals(0 to 0, openSessions())
        assertNull(launch.request.value)
    }

    @Test
    fun exerciseOpensTheCounterAndStartsNothingByItself() {
        assertEquals(StartDestination.ExerciseSession, startNow(StartAction.EXERCISE, "exercise"))

        // The counter screen starts a normal session (after the safety guidance if unread): no
        // session is made behind the user's back, timed or otherwise, and nothing is logged.
        assertEquals(0 to 0, openSessions())
        assertNull(running())
        assertEquals(emptyList<TimeEntryEntity>(), saved())
    }

    @Test
    fun quietTimeStartsTheFiveMinutesAndItsService() {
        assertEquals(StartDestination.Quiet, startNow(StartAction.QUIET))

        val live = quiet.live.value
        assertNotNull(live)
        assertEquals(0 to 1, openSessions())
        assertEquals(QuietTimerService::class.java.name, shadowOf(context as Application).nextStartedService.component?.className)

        // A second reminder while it runs simply shows the open session.
        assertEquals(StartDestination.Quiet, startNow(StartAction.QUIET))
        assertEquals(live?.session?.id, quiet.live.value?.session?.id)
        assertEquals(0 to 1, openSessions())
    }

    @Test
    fun sleepIsTheSleepButton() {
        assertEquals(StartDestination.Today, startNow(StartAction.SLEEP, "sleep"))

        assertEquals("sleep", running()?.categoryId)
        assertEquals(at(14), running()?.startAt)

        // Already sleeping: nothing changes.
        time.clock.instant = at(14, 30)
        assertEquals(StartDestination.Today, startNow(StartAction.SLEEP, "sleep"))
        assertEquals(at(14), running()?.startAt)
        assertEquals(emptyList<TimeEntryEntity>(), saved())
    }

    @Test
    fun sleepEndsAndSavesTheRunningActivityFirst() {
        blocking { entries.startCurrent("work", "report") }
        time.clock.instant = at(15, 10)

        assertEquals(StartDestination.Today, startNow(StartAction.SLEEP, "sleep"))

        assertEquals("sleep", running()?.categoryId)
        val work = saved().single()
        assertEquals(TimeEntryKind.ACTUAL, work.kind)
        assertEquals(Triple("work", at(14), at(15, 10)), Triple(work.categoryId, work.startAt, work.endAt))
    }

    @Test
    fun aPlannedActivityStartsAsNowDoingWithItsNote() {
        assertEquals(StartDestination.MyTime, startNow(StartAction.NOW_DOING, "learning", "chapter 4"))

        val current = running()
        assertEquals(Triple("learning", "chapter 4", at(14)), Triple(current?.categoryId, current?.note, current?.startAt))
        assertEquals(emptyList<TimeEntryEntity>(), saved())
        assertNull(launch.request.value)
    }

    @Test
    fun startingAPlannedActivityEndsAndSavesTheOneThatWasRunning() {
        blocking { entries.startCurrent("family", null) }
        time.clock.instant = at(14, 45)

        assertEquals(StartDestination.MyTime, startNow(StartAction.NOW_DOING, "work", null))

        assertEquals("work" to at(14, 45), running()?.categoryId to running()?.startAt)
        val family = saved().single()
        assertEquals(Triple("family", at(14), at(14, 45)), Triple(family.categoryId, family.startAt, family.endAt))
    }

    @Test
    fun startingWhatIsAlreadyRunningChangesNothing() {
        blocking { entries.startCurrent("work", "report") }
        time.clock.instant = at(15, 0)

        assertEquals(StartDestination.MyTime, startNow(StartAction.NOW_DOING, "work", "report"))

        assertEquals(at(14), running()?.startAt)
        assertEquals(emptyList<TimeEntryEntity>(), saved())
    }

    @Test
    fun nowDoingWithoutACategoryStartsNothing() {
        assertEquals(StartDestination.MyTime, startNow(StartAction.NOW_DOING, null, null))

        assertNull(running())
    }

    @Test
    fun anActivityRunningOverSixteenHoursIsConfirmedInMyTimeBeforeTheNewOneStarts() {
        blocking { entries.startCurrent("family", null) }
        time.clock.instant = at(14).plus(Duration.ofHours(17))

        assertEquals(StartDestination.MyTime, startNow(StartAction.NOW_DOING, "work", "report"))

        // Nothing changed yet: My Time asks for the end time first, then starts Work.
        assertEquals("family", running()?.categoryId)
        assertEquals(emptyList<TimeEntryEntity>(), saved())
        val request = launch.request.value as LogRequest.Draft
        assertTrue(request.confirmingRunning)
        assertEquals("family", request.draft.categoryId)
        assertEquals("work" to "report", request.then?.categoryId to request.then?.note)
    }

    @Test
    fun sleepOverALongRunningActivityAlsoAsksFirst() {
        blocking { entries.startCurrent("family", null) }
        time.clock.instant = at(14).plus(Duration.ofHours(17))

        assertEquals(StartDestination.MyTime, startNow(StartAction.SLEEP, "sleep"))

        assertEquals("family", running()?.categoryId)
        val request = launch.request.value as LogRequest.Draft
        assertTrue(request.confirmingRunning)
        assertEquals("sleep", request.then?.categoryId)
    }
}
