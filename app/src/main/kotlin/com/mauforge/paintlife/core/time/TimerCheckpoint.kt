package com.mauforge.paintlife.core.time

/**
 * Persisted timing state for exercise and quiet sessions (embedded in both tables).
 *
 * Active time is measured with the monotonic clock (SystemClock.elapsedRealtime), which
 * keeps counting while the screen is off but resets on reboot:
 * - While running, active time = [activeAtSegmentStartMillis] + (elapsedRealtime now - [segmentStartElapsedRealtime]),
 *   valid only while the device boot count still equals [bootCount].
 * - [activeDurationMillis] is the best known total as of the last saved checkpoint. After a
 *   reboot (boot count changed) it is the value that survives, and the session is recovered
 *   as paused (exercise) or incomplete (quiet).
 * - When paused, [segmentStartElapsedRealtime] is null and [activeDurationMillis] is exact.
 */
data class TimerCheckpoint(
    val activeDurationMillis: Long,
    val activeAtSegmentStartMillis: Long,
    val segmentStartElapsedRealtime: Long?,
    val bootCount: Int?,
)
