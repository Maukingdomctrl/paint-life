package com.mauforge.paintlife.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Timing rules with plain monotonic readings (milliseconds since boot) and boot counts. */
class SessionTimerTest {

    private val boot = 7

    @Test
    fun runningTimeGrowsWithTheMonotonicClock() {
        val timer = SessionTimer.started(now = 10_000, boot = boot)
        assertEquals(0L, SessionTimer.activeMillis(timer, 10_000, boot))
        assertEquals(95_500L, SessionTimer.activeMillis(timer, 105_500, boot))
    }

    @Test
    fun pausedTimeIsLeftOut() {
        var timer = SessionTimer.started(now = 0, boot = boot)
        timer = SessionTimer.paused(timer, now = 60_000, boot = boot)
        // Five minutes paused.
        assertEquals(60_000L, SessionTimer.activeMillis(timer, 360_000, boot))
        timer = SessionTimer.resumed(timer, now = 360_000, boot = boot)
        assertEquals(120_000L, SessionTimer.activeMillis(timer, 420_000, boot))
        timer = SessionTimer.paused(timer, now = 420_000, boot = boot)
        assertEquals(TimerCheckpoint(120_000, 120_000, null, boot), timer)
    }

    @Test
    fun repeatedPauseAndResumeChangeNothing() {
        val running = SessionTimer.started(now = 0, boot = boot)
        assertEquals(running, SessionTimer.resumed(running, now = 5_000, boot = boot))
        val paused = SessionTimer.paused(running, now = 5_000, boot = boot)
        assertEquals(paused, SessionTimer.paused(paused, now = 9_000, boot = boot))
    }

    @Test
    fun manyShortPausesAddUpExactly() {
        var timer = SessionTimer.started(now = 0, boot = boot)
        var now = 0L
        repeat(100) {
            now += 1_000
            timer = SessionTimer.paused(timer, now, boot)
            now += 7_000
            timer = SessionTimer.resumed(timer, now, boot)
        }
        assertEquals(100_000L, SessionTimer.activeMillis(timer, now, boot))
    }

    @Test
    fun checkpointRecordsTheActiveTimeWithoutClosingTheSegment() {
        val timer = SessionTimer.checkpoint(SessionTimer.started(now = 1_000, boot = boot), now = 16_000, boot = boot)
        assertEquals(15_000L, timer.activeDurationMillis)
        assertTrue(SessionTimer.isRunning(timer))
        assertEquals(20_000L, SessionTimer.activeMillis(timer, 21_000, boot))
    }

    @Test
    fun afterARebootTheTimeStopsAtTheLastCheckpoint() {
        val timer = SessionTimer.checkpoint(SessionTimer.started(now = 1_000, boot = boot), now = 31_000, boot = boot)
        // New boot: the monotonic clock restarted, so the open segment can't be measured.
        assertFalse(SessionTimer.isMeasurable(timer, now = 900_000, boot = boot + 1))
        assertEquals(30_000L, SessionTimer.activeMillis(timer, now = 900_000, boot = boot + 1))
    }

    @Test
    fun aRebootIsCaughtWhenTheBootCountIsUnknown() {
        val timer = SessionTimer.checkpoint(SessionTimer.started(now = 500_000, boot = null), now = 530_000, boot = null)
        // The monotonic clock is lower than the segment start: it restarted.
        assertEquals(30_000L, SessionTimer.activeMillis(timer, now = 4_000, boot = null))
    }

    @Test
    fun recoveredSessionsComeBackPausedWithTheCheckpointTime() {
        val timer = SessionTimer.checkpoint(SessionTimer.started(now = 0, boot = boot), now = 45_000, boot = boot)
        val recovered = SessionTimer.recoveredAsPaused(timer)
        assertFalse(SessionTimer.isRunning(recovered))
        assertEquals(45_000L, SessionTimer.activeMillis(recovered, now = 999_999, boot = boot))
        val resumed = SessionTimer.resumed(recovered, now = 1_000, boot = boot + 1)
        assertEquals(55_000L, SessionTimer.activeMillis(resumed, now = 11_000, boot = boot + 1))
    }

    @Test
    fun activeTimeNeverGoesBackwardsOrBelowZero() {
        val timer = TimerCheckpoint(activeDurationMillis = 50_000, activeAtSegmentStartMillis = 0, segmentStartElapsedRealtime = 100, bootCount = boot)
        assertEquals(50_000L, SessionTimer.activeMillis(timer, now = 200, boot = boot))
        assertEquals(0L, SessionTimer.activeMillis(SessionTimer.stopped(boot), now = 0, boot = boot))
    }
}
