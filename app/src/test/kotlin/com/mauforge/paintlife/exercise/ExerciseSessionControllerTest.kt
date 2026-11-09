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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * The session controller on a real database file. "Restarting the app" closes the database and
 * builds a new controller on the same file, which is what a new process does.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseSessionControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val monotonic = FakeSessionClock(now = 1_000_000, boot = 3)
    private lateinit var file: File

    private class Process(val db: PaintLifeDatabase, val scope: CoroutineScope, val repository: ExerciseRepository, val controller: ExerciseSessionController)

    private lateinit var app: Process

    @Before
    fun setUp() {
        file = File(context.cacheDir, "exercise-controller-test.db").also { context.deleteDatabase(it.absolutePath) }
        app = launch()
    }

    @After
    fun tearDown() {
        stop(app)
        context.deleteDatabase(file.absolutePath)
    }

    private fun launch(): Process {
        val db = Room.databaseBuilder(context, PaintLifeDatabase::class.java, file.absolutePath).addCallback(SeedCallback).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = ExerciseRepository(db, time.provider)
        val controller = ExerciseSessionController(repository, monotonic.clock, scope)
        runBlocking { withTimeout(5_000) { controller.loaded.first { it } } }
        return Process(db, scope, repository, controller)
    }

    private fun stop(process: Process) {
        // Let the last save finish before the database closes, as a killed process never writes later.
        runBlocking { process.scope.coroutineContext.job.cancelAndJoin() }
        process.db.close()
    }

    /** The app is closed (process killed) and opened again. */
    private fun restart() {
        stop(app)
        app = launch()
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

    private val controller get() = app.controller
    private val live get() = controller.live.value

    private fun stored(): List<ExerciseSessionEntity> = blocking { app.db.exerciseDao().openSessions() + app.repository.observeFinishedSessions().first() }

    @Test
    fun countsTapsAndKeepsThemAcrossARestartAsPaused() {
        blocking { controller.startVolume() }
        repeat(3) { controller.add(1) }
        controller.add(5)
        controller.add(10)
        assertEquals(18, live?.counter?.reps)
        blocking { controller.flush() }

        restart()

        val restored = live!!
        assertEquals(18, restored.counter.reps)
        assertTrue(restored.isPaused)
        assertFalse(restored.counter.canUndo)
    }

    @Test
    fun pausedTimeIsNotCountedAndFinishSavesTheExactActiveTime() {
        blocking { controller.startVolume() }
        controller.add(10)
        monotonic.advance(60_000)
        controller.pause()
        monotonic.advance(300_000)
        assertEquals(60_000L, controller.activeMillis(live!!))
        controller.resume()
        monotonic.advance(30_000)
        assertEquals(90_000L, controller.activeMillis(live!!))

        val outcome = blocking { controller.finish() }

        val finished = (outcome as FinishOutcome.Finished).session
        assertEquals(SessionStatus.COMPLETED, finished.status)
        assertEquals(90_000L, finished.timer.activeDurationMillis)
        assertEquals(10, finished.repetitions)
        assertNull(live)
    }

    @Test
    fun theCountIsIgnoredWhilePausedButUndoStillWorks() {
        blocking { controller.startVolume() }
        controller.add(5)
        controller.pause()
        controller.add(10)
        assertEquals(5, live!!.counter.reps)
        controller.undo()
        assertEquals(0, live!!.counter.reps)
    }

    @Test
    fun afterTheAppIsKilledTheSessionResumesFromItsLastCheckpoint() {
        blocking { controller.startVolume() }
        monotonic.advance(30_000)
        controller.checkpoint()
        blocking { controller.flush() }
        // The app is killed a while after the last save; that unsaved time is not counted.
        monotonic.advance(70_000)

        restart()

        assertTrue(live!!.isPaused)
        assertEquals(30_000L, controller.activeMillis(live!!))
        controller.resume()
        monotonic.advance(10_000)
        assertEquals(40_000L, controller.activeMillis(live!!))
    }

    @Test
    fun afterAPhoneRestartTheSessionComesBackPausedWithItsReps() {
        blocking { controller.startVolume() }
        controller.add(40)
        monotonic.advance(120_000)
        controller.checkpoint()
        blocking { controller.flush() }
        // Reboot: new boot count, monotonic clock starts again near zero.
        monotonic.boot = 4
        monotonic.now = 8_000

        restart()

        assertEquals(40, live!!.counter.reps)
        assertTrue(live!!.isPaused)
        assertEquals(120_000L, controller.activeMillis(live!!))
    }

    @Test
    fun startingTwiceAtOnceGivesOneSession() {
        val ids = blocking {
            CoroutineScope(Dispatchers.Default).run { List(5) { async { controller.startVolume()!!.session.id } }.awaitAll() }
        }
        assertEquals(1, ids.toSet().size)
        assertEquals(1, blocking { app.db.exerciseDao().openSessions() }.size)
    }

    @Test
    fun finishingWithNothingCountedOffersADiscardThatLeavesNoTrace() {
        blocking { controller.startVolume() }
        controller.add(1)
        controller.undo()

        assertEquals(FinishOutcome.NothingCounted, blocking { controller.finish() })
        assertNotNull(live)
        assertTrue(blocking { controller.discard() })

        assertNull(live)
        assertEquals(emptyList<ExerciseSessionEntity>(), stored())
        assertEquals(0, blocking { app.repository.observeLifetimeReps().first() })
    }

    @Test
    fun discardIsRefusedOnceSomethingIsCounted() {
        blocking { controller.startVolume() }
        controller.add(1)
        assertFalse(blocking { controller.discard() })
        assertEquals(1, live!!.counter.reps)
    }

    @Test
    fun aRepeatedFinishActsOnce() {
        blocking { controller.startVolume() }
        controller.add(12)
        val first = blocking { controller.finish() }
        val second = blocking { controller.finish() }
        assertTrue(first is FinishOutcome.Finished)
        assertEquals(FinishOutcome.NothingOpen, second)
        assertEquals(listOf(12), stored().map { it.repetitions })
    }

    @Test
    fun aLateSaveNeverReopensAFinishedSession() {
        val started = blocking { controller.startVolume() }
        controller.add(3)
        blocking { controller.finish() }

        val stale = started!!.session.copy(repetitions = 99, status = SessionStatus.RUNNING)
        assertFalse(blocking { app.repository.saveProgress(stale) })

        val row = stored().single()
        assertEquals(SessionStatus.COMPLETED, row.status)
        assertEquals(3, row.repetitions)
    }

    @Test
    fun resetPutsCountAndClockBackToZeroInTheSameSession() {
        val started = blocking { controller.startVolume() }
        controller.add(10)
        monotonic.advance(45_000)

        controller.reset()

        assertEquals(started!!.session.id, live!!.session.id)
        assertEquals(0, live!!.counter.reps)
        assertFalse(live!!.counter.canUndo)
        assertEquals(0L, controller.activeMillis(live!!))
        monotonic.advance(5_000)
        assertEquals(5_000L, controller.activeMillis(live!!))
    }

    @Test
    fun aCorrectionOutOfRangeChangesNothing() {
        blocking { controller.startVolume() }
        controller.add(7)
        assertFalse(controller.correct(-1))
        assertFalse(controller.correct(SessionCounter.MAX_REPS + 1))
        assertTrue(controller.correct(70))
        assertEquals(70, live!!.counter.reps)
    }

    @Test
    fun lifetimeVolumeAddsUpAcrossSessionsIncludingTheOpenOne() {
        blocking { controller.startVolume() }
        controller.add(10)
        controller.add(10)
        blocking { controller.finish() }
        blocking { controller.startVolume() }
        controller.add(5)
        blocking { controller.flush() }
        assertEquals(25, blocking { app.repository.observeLifetimeReps().first() })

        blocking { controller.finish() }
        restart()

        assertEquals(25, blocking { app.repository.observeLifetimeReps().first() })
        assertEquals(ExerciseDayTotals(reps = 25, sessions = 2), blocking { app.repository.observeDayTotals(day).first() })
    }
}
