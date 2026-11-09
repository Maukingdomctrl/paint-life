package com.mauforge.paintlife.exercise

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Cat League rules on a real (in-memory, seeded) database: unlocks, best times, what never counts. */
@RunWith(AndroidJUnit4::class)
class CatLeagueTest {

    private val time = TestTime(LocalDate.of(2026, 11, 9).atTime(7, 0), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: ExerciseRepository

    private val minutes = { m: Long -> TimeUnit.MINUTES.toMillis(m) }

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

    private fun level(n: Int) = runBlocking { repository.levels().single { it.level == n } }

    private fun best(n: Int) = runBlocking { repository.timedBest(n) }

    private fun timer(duration: Long) = TimerCheckpoint(duration, duration, null, 1)

    /** A finished 1,000-rep run at [level] taking [duration], then results applied. */
    private fun run(level: Int, duration: Long): ExerciseSessionEntity = runBlocking {
        val started = repository.startTimedSession(level, SessionTimer.started(0, 1))!!
        time.clock.instant = time.clock.instant.plusSeconds(3_600)
        val finished = repository.finish(started.id, 1_000, timer(duration))!!
        repository.applyTimedResults()
        finished
    }

    @Test
    fun oneMillisecondFasterThanTheBenchmarkUnlocksTheNextCat() {
        val win = run(level = 1, duration = minutes(30) - 1)

        assertEquals(win.endedAt, level(1).beatenAt)
        assertTrue(level(2).isUnlocked)
        assertEquals(win.endedAt, level(2).unlockedAt)
        assertEquals(minutes(30) - 1, best(1)!!.value)
        assertFalse(level(3).isUnlocked)
    }

    @Test
    fun aTieDoesNotUnlock() {
        run(level = 1, duration = minutes(30))

        assertNull(level(1).beatenAt)
        assertFalse(level(2).isUnlocked)
        // The run still counts as a best time.
        assertEquals(minutes(30), best(1)!!.value)
    }

    @Test
    fun aSlowerRunDoesNotUnlock() {
        run(level = 1, duration = minutes(31))
        assertNull(level(1).beatenAt)
        assertFalse(level(2).isUnlocked)
    }

    @Test
    fun aStoppedChallengeNeverUnlocksOrSetsABestButItsRepsCount() {
        val started = runBlocking { repository.startTimedSession(1, SessionTimer.started(0, 1))!! }
        val stopped = runBlocking { repository.stopTimed(started.id, 640, timer(minutes(10))) }!!
        runBlocking { repository.applyTimedResults() }

        assertEquals(SessionStatus.CANCELLED, stopped.status)
        assertNull(best(1))
        assertFalse(level(2).isUnlocked)
        assertEquals(640, runBlocking { repository.observeLifetimeReps().first() })
    }

    @Test
    fun stoppingWithNoRepsLeavesNoTrace() {
        val started = runBlocking { repository.startTimedSession(1, SessionTimer.started(0, 1))!! }
        runBlocking { repository.stopTimed(started.id, 0, timer(5_000)) }
        assertNull(runBlocking { repository.session(started.id) })
    }

    @Test
    fun aChallengeInterruptedByAPhoneRestartNeverUnlocksButKeepsItsReps() {
        val started = runBlocking { repository.startTimedSession(1, SessionTimer.started(now = 1_000, boot = 1))!! }
        runBlocking { repository.saveProgress(started.copy(repetitions = 1_000, timer = SessionTimer.checkpoint(started.timer, 600_000, 1))) }

        val open = runBlocking {
            val recovered = repository.recoverOpenSessions(now = 4_000, boot = 2)
            repository.applyTimedResults()
            recovered
        }

        assertNull(open)
        val stored = runBlocking { repository.session(started.id) }!!
        assertEquals(SessionStatus.INCOMPLETE, stored.status)
        assertEquals(599_000L, stored.timer.activeDurationMillis)
        assertNull(best(1))
        assertFalse(level(2).isUnlocked)
        assertEquals(1_000, runBlocking { repository.observeLifetimeReps().first() })
    }

    @Test
    fun aTimedChallengeFromThisBootComesBackPausedWithItsClockStillRunning() {
        val started = runBlocking { repository.startTimedSession(1, SessionTimer.started(now = 1_000, boot = 1))!! }

        val recovered = runBlocking { repository.recoverOpenSessions(now = 61_000, boot = 1) }!!

        assertEquals(started.id, recovered.id)
        assertEquals(SessionStatus.PAUSED, recovered.status)
        assertEquals(60_000L, SessionTimer.activeMillis(recovered.timer, 61_000, 1))
        assertTrue(SessionTimer.isRunning(recovered.timer))
    }

    @Test
    fun aLockedLevelCannotBeStarted() {
        assertNull(runBlocking { repository.startTimedSession(2, SessionTimer.started(0, 1)) })
        assertNull(runBlocking { repository.startTimedSession(11, SessionTimer.started(0, 1)) })
        assertEquals(0, runBlocking { db.exerciseDao().openSessions() }.size)
    }

    @Test
    fun aReplayOnlyImprovesTheBestAndNeverUnlocksFurther() {
        val firstWin = run(level = 1, duration = minutes(29))
        val beatenAt = level(1).beatenAt

        run(level = 1, duration = minutes(29) + 30_000)
        assertEquals(minutes(29), best(1)!!.value)
        assertEquals(firstWin.id, best(1)!!.sessionId)

        val faster = run(level = 1, duration = minutes(28))
        assertEquals(minutes(28), best(1)!!.value)
        assertEquals(faster.id, best(1)!!.sessionId)

        assertEquals(beatenAt, level(1).beatenAt)
        assertTrue(level(2).isUnlocked)
        assertFalse(level(3).isUnlocked)
    }

    @Test
    fun beatingChampionNeedsNoNextCat() {
        runBlocking {
            repository.levels().forEach { db.exerciseDao().updateLevel(it.copy(isUnlocked = true)) }
        }
        run(level = 10, duration = minutes(21) - 1)
        assertNotNull(level(10).beatenAt)
        assertEquals(10, runBlocking { repository.levels() }.size)
    }

    @Test
    fun applyingResultsTwiceChangesNothing() {
        run(level = 1, duration = minutes(29))
        val levels = runBlocking { repository.levels() }
        val bestBefore = best(1)

        runBlocking { repository.applyTimedResults() }

        assertEquals(levels, runBlocking { repository.levels() })
        assertEquals(bestBefore, best(1))
    }

    @Test
    fun undoWithinTheWindowReopensAFinishedRun() {
        val started = runBlocking { repository.startTimedSession(1, SessionTimer.started(0, 1))!! }
        runBlocking { repository.finish(started.id, 1_000, timer(minutes(20))) }

        val reopened = started.copy(repetitions = 995, status = SessionStatus.RUNNING)
        assertTrue(runBlocking { repository.reopenTimed(reopened) })

        val stored = runBlocking { repository.session(started.id) }!!
        assertEquals(SessionStatus.RUNNING, stored.status)
        assertEquals(995, stored.repetitions)
        assertNull(stored.endedAt)
        runBlocking { repository.applyTimedResults() }
        assertFalse(level(2).isUnlocked)
    }

    @Test
    fun aStepUpSessionCannotBeReopenedAsATimedRun() {
        val volume = runBlocking { repository.startVolumeSession(SessionTimer.started(0, 1)) }
        runBlocking { repository.finish(volume.id, 10, timer(1_000)) }
        assertFalse(runBlocking { repository.reopenTimed(volume.copy(status = SessionStatus.RUNNING)) })
    }

    @Test
    fun theLadderShowsBeatenCurrentAndLocked() {
        run(level = 1, duration = minutes(29))
        val league = runBlocking { repository.observeLeague().first() }

        assertEquals(10, league.size)
        assertEquals(LevelStatus.Beaten, league[0].status)
        assertEquals(minutes(29), league[0].personalBestMillis)
        assertEquals(LevelStatus.Current, league[1].status)
        assertTrue(league.drop(2).all { it.status == LevelStatus.Locked })
    }
}
