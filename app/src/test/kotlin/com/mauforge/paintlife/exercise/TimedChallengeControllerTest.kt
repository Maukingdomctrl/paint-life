package com.mauforge.paintlife.exercise

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Timed challenges through the session controller on a real database file. "Restarting the app"
 * builds a new controller on the same file, as a new process would.
 */
@RunWith(AndroidJUnit4::class)
class TimedChallengeControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = TestTime(LocalDate.of(2026, 11, 9).atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val monotonic = FakeSessionClock(now = 2_000_000, boot = 5)
    private lateinit var file: File

    private class Process(val db: PaintLifeDatabase, val scope: CoroutineScope, val repository: ExerciseRepository, val controller: ExerciseSessionController)

    private lateinit var app: Process

    @Before
    fun setUp() {
        file = File(context.cacheDir, "timed-controller-test.db").also { context.deleteDatabase(it.absolutePath) }
        app = launch()
    }

    @After
    fun tearDown() {
        stop(app)
        context.deleteDatabase(file.absolutePath)
    }

    private fun launch(undoWindowMillis: Long = 300): Process {
        val db = Room.databaseBuilder(context, PaintLifeDatabase::class.java, file.absolutePath).addCallback(SeedCallback).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = ExerciseRepository(db, time.provider)
        val controller = ExerciseSessionController(repository, monotonic.clock, scope).also { it.undoWindowMillis = undoWindowMillis }
        blocking { controller.loaded.first { it } }
        return Process(db, scope, repository, controller)
    }

    private fun stop(process: Process) {
        runBlocking { process.scope.coroutineContext.job.cancelAndJoin() }
        process.db.close()
    }

    private fun restart(undoWindowMillis: Long = 300) {
        stop(app)
        app = launch(undoWindowMillis)
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private val controller get() = app.controller
    private val live get() = controller.live.value
    private fun stored(id: String) = blocking { app.repository.session(id) }!!
    private fun level(n: Int) = blocking { app.repository.levels() }.single { it.level == n }

    private fun waitFor(condition: () -> Boolean) = blocking {
        while (!condition()) delay(10)
    }

    /** Starts a challenge against Aero and brings the count to [reps] without finishing. */
    private fun startAt(reps: Int): ExerciseSessionController.Live {
        val started = blocking { controller.startTimed(1) }!!
        if (reps > 0) assertTrue(controller.correct(reps))
        return started
    }

    @Test
    fun aLockedCatCannotBeChallenged() {
        assertNull(blocking { controller.startTimed(2) })
        assertNull(live)
    }

    @Test
    fun pausingStopsCountingButTheChallengeClockKeepsRunning() {
        startAt(0)
        monotonic.advance(60_000)
        controller.pause()
        controller.add(10)
        monotonic.advance(60_000)

        assertEquals(0, live!!.counter.reps)
        assertTrue(live!!.isPaused)
        assertEquals(120_000L, controller.activeMillis(live!!))
    }

    @Test
    fun reachingOneThousandStopsTheClockOnThatTapAndCapsTheCount() {
        val started = startAt(995)
        monotonic.advance(TimeUnit.MINUTES.toMillis(25))

        controller.add(10)

        val window = controller.finishWindow.value!!
        assertNull(live)
        assertEquals(1_000, window.finished.repetitions)
        assertEquals(TimeUnit.MINUTES.toMillis(25), window.finished.timer.activeDurationMillis)
        // Later taps have no session to count into.
        controller.add(1)
        assertNull(live)

        waitFor { controller.lastResult.value?.sessionId == started.session.id }
        assertNull(controller.finishWindow.value)
        val saved = stored(started.session.id)
        assertEquals(SessionStatus.COMPLETED, saved.status)
        assertEquals(TimeUnit.MINUTES.toMillis(25), saved.timer.activeDurationMillis)
        assertTrue(level(2).isUnlocked)
        assertEquals(TimeUnit.MINUTES.toMillis(25), blocking { app.repository.timedBest(1) }!!.value)
    }

    @Test
    fun undoInTheWindowReopensTheChallengeAndAppliesNothing() {
        restart(undoWindowMillis = 60_000)
        val started = startAt(995)
        monotonic.advance(TimeUnit.MINUTES.toMillis(20))
        controller.add(10)
        waitFor { stored(started.session.id).status == SessionStatus.COMPLETED }

        assertTrue(blocking { controller.undoFinish() })

        assertNull(controller.finishWindow.value)
        assertEquals(995, live!!.counter.reps)
        assertFalse(live!!.isPaused)
        // The clock never stopped.
        monotonic.advance(30_000)
        assertEquals(TimeUnit.MINUTES.toMillis(20) + 30_000, controller.activeMillis(live!!))
        assertEquals(SessionStatus.RUNNING, stored(started.session.id).status)
        assertFalse(level(2).isUnlocked)
        assertNull(blocking { app.repository.timedBest(1) })
        assertFalse(blocking { controller.undoFinish() })
    }

    @Test
    fun undoStraightAfterTheTapWinsTheRaceWithTheSave() {
        restart(undoWindowMillis = 60_000)
        val started = startAt(999)
        controller.add(1)
        assertTrue(blocking { controller.undoFinish() })

        waitFor { stored(started.session.id).isOpen }
        assertEquals(999, live!!.counter.reps)
        assertNull(stored(started.session.id).endedAt)
    }

    @Test
    fun aResultSavedInTheWindowIsAppliedAtTheNextStartIfTheAppWasClosed() {
        restart(undoWindowMillis = 60_000)
        val started = startAt(990)
        monotonic.advance(TimeUnit.MINUTES.toMillis(26))
        controller.add(10)
        waitFor { stored(started.session.id).status == SessionStatus.COMPLETED }
        assertFalse(level(2).isUnlocked)

        restart()

        assertTrue(level(2).isUnlocked)
        assertEquals(TimeUnit.MINUTES.toMillis(26), blocking { app.repository.timedBest(1) }!!.value)
        assertNull(live)
    }

    @Test
    fun stopChallengeEndsItWithoutATimeAndKeepsTheReps() {
        val started = startAt(640)
        monotonic.advance(600_000)

        assertTrue(blocking { controller.stopChallenge() })

        assertNull(live)
        val saved = stored(started.session.id)
        assertEquals(SessionStatus.CANCELLED, saved.status)
        assertEquals(640, saved.repetitions)
        assertEquals(640, blocking { app.repository.observeLifetimeReps().first() })
        assertNull(blocking { app.repository.timedBest(1) })
        assertFalse(level(2).isUnlocked)
    }

    @Test
    fun afterTheAppIsKilledTheChallengeComesBackPausedWithItsClockStillExact() {
        startAt(300)
        monotonic.advance(30_000)
        blocking { controller.flush() }
        monotonic.advance(70_000)

        restart()

        assertTrue(live!!.isPaused)
        assertEquals(300, live!!.counter.reps)
        assertEquals(100_000L, controller.activeMillis(live!!))
    }

    @Test
    fun aPhoneRestartEndsTheChallengeAsIncompleteWithItsReps() {
        val started = startAt(420)
        monotonic.advance(90_000)
        controller.checkpoint()
        blocking { controller.flush() }
        monotonic.boot = 6
        monotonic.now = 9_000

        restart()

        assertNull(live)
        val saved = stored(started.session.id)
        assertEquals(SessionStatus.INCOMPLETE, saved.status)
        assertEquals(420, saved.repetitions)
        assertEquals(90_000L, saved.timer.activeDurationMillis)
        assertFalse(level(2).isUnlocked)
    }

    @Test
    fun aCorrectionToOneThousandFinishesButBeyondIsRefused() {
        startAt(0)
        assertFalse(controller.correct(1_001))
        assertTrue(controller.correct(1_000))
        assertTrue(controller.finishWindow.value != null)
    }

    @Test
    fun resetRestartsTheAttemptWithTheClockRunning() {
        startAt(200)
        monotonic.advance(120_000)
        controller.pause()

        controller.reset()

        assertEquals(0, live!!.counter.reps)
        monotonic.advance(5_000)
        assertEquals(5_000L, controller.activeMillis(live!!))
    }
}
