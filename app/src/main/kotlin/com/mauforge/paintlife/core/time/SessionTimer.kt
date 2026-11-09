package com.mauforge.paintlife.core.time

/**
 * Pure timing rules over [TimerCheckpoint]. Every function takes the monotonic reading [now]
 * (SystemClock.elapsedRealtime) and the device [boot] count; nothing here reads a clock, so
 * the rules are tested with plain numbers.
 *
 * A timer is "running" while it has an open segment. Pausing closes the segment, so paused time
 * is never added. A segment from an earlier boot can no longer be measured: its time stops at the
 * last saved checkpoint.
 */
object SessionTimer {

    /** A new timer whose first segment starts at [now]. */
    fun started(now: Long, boot: Int?): TimerCheckpoint = TimerCheckpoint(
        activeDurationMillis = 0L,
        activeAtSegmentStartMillis = 0L,
        segmentStartElapsedRealtime = now,
        bootCount = boot,
    )

    /** A timer at zero that is not running. */
    fun stopped(boot: Int?): TimerCheckpoint = TimerCheckpoint(0L, 0L, null, boot)

    fun isRunning(timer: TimerCheckpoint): Boolean = timer.segmentStartElapsedRealtime != null

    /** True when the open segment was started in this boot, so it can still be measured. */
    fun isMeasurable(timer: TimerCheckpoint, now: Long, boot: Int?): Boolean {
        val start = timer.segmentStartElapsedRealtime ?: return false
        if (now < start) return false
        val saved = timer.bootCount
        return saved == null || boot == null || saved == boot
    }

    /** Active time at [now]. Never less than the last checkpoint, never negative. */
    fun activeMillis(timer: TimerCheckpoint, now: Long, boot: Int?): Long {
        val start = timer.segmentStartElapsedRealtime
        if (start == null || !isMeasurable(timer, now, boot)) return timer.activeDurationMillis.coerceAtLeast(0L)
        return maxOf(timer.activeAtSegmentStartMillis + (now - start), timer.activeDurationMillis).coerceAtLeast(0L)
    }

    /** Records the active time at [now] so it survives a reboot or the app being closed. */
    fun checkpoint(timer: TimerCheckpoint, now: Long, boot: Int?): TimerCheckpoint =
        if (!isRunning(timer)) timer else timer.copy(activeDurationMillis = activeMillis(timer, now, boot))

    /** Closes the open segment; the active time is then exact and stops growing. */
    fun paused(timer: TimerCheckpoint, now: Long, boot: Int?): TimerCheckpoint {
        if (!isRunning(timer)) return timer
        val active = activeMillis(timer, now, boot)
        return TimerCheckpoint(active, active, null, boot ?: timer.bootCount)
    }

    /** Opens a new segment at [now], continuing from the paused active time. */
    fun resumed(timer: TimerCheckpoint, now: Long, boot: Int?): TimerCheckpoint {
        if (isRunning(timer)) return timer
        return TimerCheckpoint(timer.activeDurationMillis, timer.activeDurationMillis, now, boot)
    }

    /**
     * After the app was closed (or the phone restarted) while running: the session comes back
     * paused, with the active time of the last saved checkpoint.
     */
    fun recoveredAsPaused(timer: TimerCheckpoint): TimerCheckpoint {
        if (!isRunning(timer)) return timer
        val active = timer.activeDurationMillis.coerceAtLeast(0L)
        return TimerCheckpoint(active, active, null, timer.bootCount)
    }
}
