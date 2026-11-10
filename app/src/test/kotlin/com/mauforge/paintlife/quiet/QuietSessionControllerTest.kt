package com.mauforge.paintlife.quiet

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/**
 * The quiet session controller on a real database file, with a hand-driven monotonic clock and
 * wall clock. "Android killed the app" closes the database and builds a new controller on the
 * same file, which is what a new process does; "the phone restarted" also changes the boot count
 * and resets the monotonic clock.
 */
@RunWith(AndroidJUnit4::class)
class QuietSessionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val monotonic = FakeSessionClock(now = 1_000_000, boot = 3)
    private lateinit var file: File

    private class Process(val db: PaintLifeDatabase, val scope: CoroutineScope, val repository: QuietRepository, val controller: QuietSessionController)

    private lateinit var app: Process

    @Before
    fun setUp() {
        file = File(context.cacheDir, "quiet-controller-test.db").also { context.deleteDatabase(it.absolutePath) }
        app = launch()
    }

    @After
    fun tearDown() {
        stop(app)
        context.deleteDatabase(file.absolutePath)
    }

    private fun launch(): Process {
        val db = Room.databaseBuilder(context, PaintLifeDatabase::class.java, file.absolutePath).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = QuietRepository(db, time.provider)
        val controller = QuietSessionController(repository, monotonic.clock, scope)
        blocking { controller.loaded.first { it } }
        return Process(db, scope, repository, controller)
    }

    private fun stop(process: Process) {
        runBlocking { process.scope.coroutineContext.job.cancelAndJoin() }
        process.db.close()
    }

    /** Android ended the process (same boot), and the app is opened again. */
    private fun killAndReopen() {
        stop(app)
        app = launch()
    }

    /** The phone restarted: new boot count, monotonic clock from zero. */
    private fun rebootAndReopen() {
        stop(app)
        monotonic.boot = (monotonic.boot ?: 0) + 1
        monotonic.now = 20_000
        time.clock.instant = time.clock.instant.plusSeconds(90)
        app = launch()
    }

    /**
     * Real time passes: the wall clock moves first, then the monotonic clock, so the controller
     * never sees the monotonic clock ahead of the wall clock.
     */
    private fun pass(millis: Long) {
        time.clock.instant = time.clock.instant.plusMillis(millis)
        monotonic.advance(millis)
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

    private val controller get() = app.controller
    private val live get() = controller.live.value

    private fun stored(id: String): QuietSessionEntity = blocking { app.db.quietDao().session(id) }!!

    private fun today(date: LocalDate = day): QuietDayTotals = blocking { app.repository.observeDay(date).first() }

    @Test
    fun completesAtExactlyFiveMinutes() {
        val session = blocking { controller.start() }.session
        val startedAt = session.startedAt

        pass(299_999)
        blocking { controller.checkDeadlines() }
        assertNotNull(live)
        assertEquals(1L, controller.remainingMillis(live!!))

        pass(1)
        blocking { controller.checkDeadlines() }

        assertNull(live)
        val done = stored(session.id)
        assertEquals(SessionStatus.COMPLETED, done.status)
        assertEquals(300_000L, done.timer.activeDurationMillis)
        assertEquals(startedAt.plusSeconds(300), done.endedAt)
        assertEquals(done.id, controller.completed.value?.id)
        assertEquals(QuietDayTotals(day, completedSessions = 1, quietMillis = 300_000), today())
        assertTrue(today().completed)
        assertEquals(1f, today().fill)
    }

    @Test
    fun pausedTimeIsNeverCounted() {
        val session = blocking { controller.start() }.session
        pass(120_000)
        controller.pause()
        pass(600_000)
        assertEquals(120_000L, controller.activeMillis(live!!))
        controller.resume()
        pass(60_000)
        controller.pause()
        pass(45_000)
        controller.resume()
        assertEquals(180_000L, controller.activeMillis(live!!))
        pass(120_000)
        blocking { controller.checkDeadlines() }

        val done = stored(session.id)
        assertEquals(SessionStatus.COMPLETED, done.status)
        assertEquals(300_000L, done.timer.activeDurationMillis)
        // 5 minutes of quiet and 10:45 of pauses.
        assertEquals(session.startedAt.plus(Duration.ofMinutes(5).plusMinutes(10).plusSeconds(45)), done.endedAt)
    }

    @Test
    fun screenOffOrBackgroundTimeCountsAndCompletionIsBackdatedToTheTarget() {
        val session = blocking { controller.start() }.session
        // The phone slept with no ticks at all and woke 7 minutes later.
        pass(420_000)
        blocking { controller.checkDeadlines() }

        val done = stored(session.id)
        assertEquals(SessionStatus.COMPLETED, done.status)
        assertEquals(300_000L, done.timer.activeDurationMillis)
        assertEquals(session.startedAt.plusSeconds(300), done.endedAt)
    }

    @Test
    fun finishEarlySavesTheMinutesButNeverCountsAsCompleted() {
        val session = blocking { controller.start() }.session
        pass(185_000)

        val result = blocking { controller.finish() }

        val ended = (result as QuietFinish.EndedEarly).session
        assertEquals(SessionStatus.ENDED_EARLY, ended.status)
        assertEquals(185_000L, stored(session.id).timer.activeDurationMillis)
        assertNull(live)
        assertNull(controller.completed.value)
        val totals = today()
        assertFalse(totals.completed)
        assertEquals(3, totals.minutes)
        assertEquals(185_000f / 300_000f, totals.fill, 0.0001f)
        // A repeated tap does nothing.
        assertEquals(QuietFinish.NothingOpen, blocking { controller.finish() })
    }

    @Test
    fun finishingWithLessThanASecondSavesNothing() {
        val session = blocking { controller.start() }.session
        pass(400)

        assertEquals(QuietFinish.NothingSaved, blocking { controller.finish() })

        assertNull(blocking { app.db.quietDao().session(session.id) })
        assertEquals(QuietDayTotals.empty(day), today())
    }

    @Test
    fun cancelRemovesTheSessionAndUndoBringsItBackPaused() {
        val session = blocking { controller.start() }.session
        pass(90_000)

        val cancelled = blocking { controller.cancel() }!!
        assertNull(live)
        assertNull(blocking { app.db.quietDao().session(session.id) })

        pass(5_000)
        assertTrue(blocking { controller.undoCancel(cancelled) })
        val restored = live!!
        assertTrue(restored.isPaused)
        assertEquals(session.id, restored.session.id)
        assertEquals(90_000L, controller.activeMillis(restored))
        assertEquals(SessionStatus.PAUSED, stored(session.id).status)

        controller.resume()
        pass(210_000)
        blocking { controller.checkDeadlines() }
        assertEquals(SessionStatus.COMPLETED, stored(session.id).status)
    }

    @Test
    fun undoOfCancelFailsOnceAnotherSessionHasStarted() {
        blocking { controller.start() }
        pass(30_000)
        val cancelled = blocking { controller.cancel() }!!
        val second = blocking { controller.start() }

        assertFalse(blocking { controller.undoCancel(cancelled) })
        assertEquals(second.session.id, live?.session?.id)
    }

    @Test
    fun aSessionPausedForThirtyMinutesEndsEarlyWithItsMinutes() {
        val session = blocking { controller.start() }.session
        pass(150_000)
        controller.pause()
        pass(QuietRules.PAUSE_LIMIT_MILLIS - 1)
        blocking { controller.checkDeadlines() }
        assertNotNull(live)

        pass(1)
        blocking { controller.checkDeadlines() }

        assertNull(live)
        val ended = stored(session.id)
        assertEquals(SessionStatus.ENDED_EARLY, ended.status)
        assertEquals(150_000L, ended.timer.activeDurationMillis)
        assertFalse(today().completed)
    }

    @Test
    fun aSessionPausedAtOnceAndLeftForThirtyMinutesLeavesNothingBehind() {
        val session = blocking { controller.start() }.session
        pass(400)
        controller.pause()
        pass(QuietRules.PAUSE_LIMIT_MILLIS)

        blocking { controller.checkDeadlines() }

        // Under a second of quiet: no zero-minute session in the history, as with Finish.
        assertNull(live)
        assertNull(blocking { app.db.quietDao().session(session.id) })
        assertEquals(QuietDayTotals.empty(day), today())
    }

    @Test
    fun resumingAfterThePauseLimitEndsTheSessionInstead() {
        val session = blocking { controller.start() }.session
        pass(60_000)
        controller.pause()
        pass(QuietRules.PAUSE_LIMIT_MILLIS + 60_000)

        controller.resume()

        blocking { withTimeout(5_000) { controller.live.first { it == null } } }
        assertEquals(SessionStatus.ENDED_EARLY, stored(session.id).status)
    }

    @Test
    fun androidKillingTheAppSavesARunningSessionAsIncomplete() {
        val session = blocking { controller.start() }.session
        pass(120_000)
        controller.checkpoint()
        blocking { controller.flush() }
        pass(10_000)

        killAndReopen()

        assertNull(live)
        val saved = stored(session.id)
        assertEquals(SessionStatus.INCOMPLETE, saved.status)
        assertEquals(120_000L, saved.timer.activeDurationMillis)
        assertNotNull(saved.endedAt)
        assertEquals(QuietDayTotals.empty(day), today())
    }

    @Test
    fun androidKillingTheAppSavesAPausedSessionAsIncomplete() {
        val session = blocking { controller.start() }.session
        pass(200_000)
        controller.pause()
        blocking { controller.flush() }

        killAndReopen()

        val saved = stored(session.id)
        assertEquals(SessionStatus.INCOMPLETE, saved.status)
        assertEquals(200_000L, saved.timer.activeDurationMillis)
    }

    @Test
    fun aSessionWhoseSavedTimeReachedTheTargetBeforeTheKillIsCompleted() {
        val session = blocking { controller.start() }.session
        // The phone slept past 5:00 and the app was closed right after a checkpoint, before the
        // completion was saved.
        pass(310_000)
        controller.checkpoint()
        blocking { controller.flush() }
        val checkpointedAt = time.clock.instant

        killAndReopen()

        val saved = stored(session.id)
        assertEquals(SessionStatus.COMPLETED, saved.status)
        assertEquals(300_000L, saved.timer.activeDurationMillis)
        assertTrue(saved.endedAt!! <= checkpointedAt)
        assertTrue(today().completed)
    }

    @Test
    fun aPhoneRestartSavesTheSessionAsIncompleteWithTheLastCheckpoint() {
        val session = blocking { controller.start() }.session
        pass(240_000)
        controller.checkpoint()
        blocking { controller.flush() }
        // Time after the last checkpoint is lost with the reboot.
        pass(50_000)

        rebootAndReopen()

        val saved = stored(session.id)
        assertEquals(SessionStatus.INCOMPLETE, saved.status)
        assertEquals(240_000L, saved.timer.activeDurationMillis)
        assertNull(live)
        assertFalse(today().completed)
        assertEquals(0, today().minutes)
    }

    @Test
    fun incompleteSessionsNeverCountAnywhere() {
        repeat(3) {
            blocking { controller.start() }
            pass(299_000)
            controller.checkpoint()
            blocking { controller.flush() }
            killAndReopen()
        }
        val month = blocking { app.repository.observeDays(day.withDayOfMonth(1), day.withDayOfMonth(30)).first() }
        assertTrue(month.isEmpty())
    }

    @Test
    fun repeatedStartsKeepOneSessionAndMoreSessionsADayAreAllowed() {
        val starts = blocking { (1..5).map { app.scope.async { controller.start() } }.awaitAll() }
        assertEquals(1, starts.map { it.session.id }.distinct().size)
        pass(300_000)
        blocking { controller.checkDeadlines() }

        blocking { controller.start() }
        pass(300_000)
        blocking { controller.checkDeadlines() }

        assertEquals(QuietDayTotals(day, completedSessions = 2, quietMillis = 600_000), today())
    }

    @Test
    fun aSessionAcrossMidnightCountsOnTheDayItStarted() {
        time.setLocal(day.atTime(23, 58))
        val session = blocking { controller.start() }.session
        pass(300_000)
        blocking { controller.checkDeadlines() }

        val done = stored(session.id)
        assertEquals(day, done.startDate)
        assertEquals(day.plusDays(1), done.endedAt!!.atZone(done.endedZoneId!!).toLocalDate())
        assertTrue(today(day).completed)
        assertEquals(QuietDayTotals.empty(day.plusDays(1)), today(day.plusDays(1)))
    }

    @Test
    fun aZoneChangeDuringASessionKeepsItsDayAndExactTime() {
        val session = blocking { controller.start() }.session
        pass(120_000)
        time.zone = ZoneId.of("Europe/London")
        pass(180_000)
        blocking { controller.checkDeadlines() }

        val done = stored(session.id)
        assertEquals(SessionStatus.COMPLETED, done.status)
        assertEquals(300_000L, done.timer.activeDurationMillis)
        assertEquals(ZoneId.of("Asia/Kolkata"), done.zoneId)
        assertEquals(ZoneId.of("Europe/London"), done.endedZoneId)
        assertEquals(day, done.startDate)
    }

    @Test
    fun aDstChangeDuringASessionDoesNotChangeItsLength() {
        // Clocks in London jump from 01:00 to 02:00 on 29 March 2026.
        val london = ZoneId.of("Europe/London")
        time.zone = london
        time.setLocal(LocalDate.of(2026, 3, 29).atTime(0, 58))
        val session = blocking { controller.start() }.session
        pass(300_000)
        blocking { controller.checkDeadlines() }

        val done = stored(session.id)
        assertEquals(300_000L, done.timer.activeDurationMillis)
        assertEquals("02:03", done.endedAt!!.atZone(london).toLocalTime().toString())
        assertEquals(LocalDate.of(2026, 3, 29), done.startDate)
    }
}
