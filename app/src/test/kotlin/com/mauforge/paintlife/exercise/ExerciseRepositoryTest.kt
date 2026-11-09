package com.mauforge.paintlife.exercise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ExerciseRepositoryTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(23, 50), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: ExerciseRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        repository = ExerciseRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun finishedSession(reps: Int): ExerciseSessionEntity = runBlocking {
        val started = repository.startVolumeSession(SessionTimer.started(0, 1))
        repository.finish(started.id, reps, SessionTimer.paused(started.timer, 60_000, 1))!!
    }

    private fun totals(date: LocalDate) = runBlocking { repository.observeDayTotals(date).first() }

    @Test
    fun aSessionThatRunsPastMidnightCountsOnTheDayItStarted() {
        val started = runBlocking { repository.startVolumeSession(SessionTimer.started(0, 1)) }
        time.setLocal(day.plusDays(1).atTime(0, 20))
        val finished = runBlocking { repository.finish(started.id, 90, SessionTimer.paused(started.timer, 1_800_000, 1))!! }

        assertEquals(day, finished.startDate)
        assertEquals(ExerciseDayTotals(90, 1), totals(day))
        assertEquals(ExerciseDayTotals(0, 0), totals(day.plusDays(1)))
    }

    @Test
    fun aZoneChangeKeepsTheStartDayAndRecordsEachZone() {
        val started = runBlocking { repository.startVolumeSession(SessionTimer.started(0, 1)) }
        // Flying west: in London it is still the afternoon of the same day.
        time.zone = ZoneId.of("Europe/London")
        val finished = runBlocking { repository.finish(started.id, 30, SessionTimer.paused(started.timer, 600_000, 1))!! }

        assertEquals(ZoneId.of("Asia/Kolkata"), finished.zoneId)
        assertEquals(ZoneId.of("Europe/London"), finished.endedZoneId)
        assertEquals(day, finished.startDate)
        assertEquals(600_000L, finished.timer.activeDurationMillis)
    }

    @Test
    fun aSessionWithNoRepsDoesNotPaintTheDay() {
        runBlocking { repository.startVolumeSession(SessionTimer.started(0, 1)) }
        assertEquals(ExerciseDayTotals(0, 0), totals(day))
    }

    @Test
    fun historyEditsCorrectRepsAndNotesAndTotalsFollow() {
        val session = finishedSession(reps = 120)

        assertTrue(runBlocking { repository.updateFinished(session.id, 110, "  Knee felt fine  ") })

        val edited = runBlocking { repository.observeFinishedSessions().first() }.single()
        assertEquals(110, edited.repetitions)
        assertEquals("Knee felt fine", edited.notes)
        assertEquals(110, runBlocking { repository.observeLifetimeReps().first() })

        assertTrue(runBlocking { repository.updateFinished(session.id, 110, "   ") })
        assertNull(runBlocking { repository.observeFinishedSessions().first() }.single().notes)
    }

    @Test
    fun deletingASessionRemovesItFromTotals() {
        val kept = finishedSession(reps = 50)
        val deleted = finishedSession(reps = 70)
        assertEquals(120, runBlocking { repository.observeLifetimeReps().first() })

        assertTrue(runBlocking { repository.deleteFinished(deleted.id) })

        assertEquals(listOf(kept.id), runBlocking { repository.observeFinishedSessions().first() }.map { it.id })
        assertEquals(50, runBlocking { repository.observeLifetimeReps().first() })
    }

    @Test
    fun anOpenSessionCannotBeEditedOrDeletedFromHistory() {
        val open = runBlocking { repository.startVolumeSession(SessionTimer.started(0, 1)) }
        assertFalse(runBlocking { repository.updateFinished(open.id, 5, "x") })
        assertFalse(runBlocking { repository.deleteFinished(open.id) })
        assertEquals(1, runBlocking { db.exerciseDao().openSessions() }.size)
    }

    @Test
    fun aTimedResultKeepsItsRepsAndCannotBeDeleted() {
        val timed = finishedSession(reps = 1_000).copy(type = ExerciseSessionType.TIMED_CHALLENGE, challengeLevel = 1)
        runBlocking { db.exerciseDao().updateSession(timed) }

        assertTrue(runBlocking { repository.updateFinished(timed.id, 400, "Windy day") })
        val stored = runBlocking { db.exerciseDao().session(timed.id) }!!
        assertEquals(1_000, stored.repetitions)
        assertEquals("Windy day", stored.notes)
        assertFalse(runBlocking { repository.deleteFinished(timed.id) })
    }

    @Test
    fun recoveryPausesARunningSessionAndLeavesFinishedOnesAlone() {
        val done = finishedSession(reps = 10)
        val open = runBlocking { repository.startVolumeSession(SessionTimer.checkpoint(SessionTimer.started(0, 1), 20_000, 1)) }

        val recovered = runBlocking { repository.recoverOpenSessions(now = 900_000, boot = 1) }!!

        assertEquals(open.id, recovered.id)
        assertEquals(SessionStatus.PAUSED, recovered.status)
        assertEquals(20_000L, recovered.timer.activeDurationMillis)
        assertNull(recovered.timer.segmentStartElapsedRealtime)
        assertEquals(SessionStatus.COMPLETED, runBlocking { db.exerciseDao().session(done.id) }!!.status)
    }

    @Test
    fun recoveryWithNothingOpenDoesNothing() {
        finishedSession(reps = 10)
        assertNull(runBlocking { repository.recoverOpenSessions(now = 900_000, boot = 1) })
    }

    @Test
    fun notesAreTrimmedAndCapped() {
        assertNull(SessionNote.clean("   "))
        assertEquals("a b", SessionNote.clean("  a b "))
        assertEquals(SessionNote.MAX_LENGTH, SessionNote.clean("x".repeat(400))!!.length)
    }
}
