package com.mauforge.paintlife.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * The monotonic clock used to time sessions, and the device boot count that tells whether its
 * readings are still comparable. [elapsedRealtime] keeps counting in deep sleep and ignores
 * wall-clock, zone and DST changes; it restarts from zero when the phone reboots.
 *
 * [bootCount] is null when the system does not report it; reboots are then still caught when
 * the monotonic clock is lower than a saved reading.
 */
class SessionClock(
    private val elapsed: () -> Long,
    private val boot: () -> Int?,
) {
    fun elapsedRealtime(): Long = elapsed()

    fun bootCount(): Int? = boot()

    companion object {
        fun system(context: Context): SessionClock {
            val resolver = context.applicationContext.contentResolver
            return SessionClock(
                elapsed = SystemClock::elapsedRealtime,
                // Settings.Global.BOOT_COUNT needs no permission (API 24+).
                boot = { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 } },
            )
        }
    }
}
