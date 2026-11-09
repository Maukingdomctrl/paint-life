package com.mauforge.paintlife.core.time

/** Display formatting for durations. Pure functions; locale-independent digits. */
object DurationFormat {

    /** "0:00", "4:59", "29:41", "1:02:03". Negative values show as 0:00. */
    fun clock(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes, seconds)
        } else {
            "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
        }
    }

    /**
     * A result time to the tenth of a second ("27:59.9"), truncated, so a run that missed a
     * benchmark by a fraction never displays as equal to it.
     */
    fun clockTenths(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        return clock(safe) + "." + ((safe % 1000) / 100)
    }

    /**
     * A countdown clock: rounds up to the next whole second, so a timer shows 5:00 at the start
     * and reaches 0:00 only when time is really up.
     */
    fun countdown(remainingMillis: Long): String = clock(((remainingMillis.coerceAtLeast(0L) + 999) / 1000) * 1000)

    /** "0m", "45m", "5h", "5h 40m". */
    fun hoursMinutes(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "${rest}m"
            rest == 0 -> "${hours}h"
            else -> "${hours}h ${rest}m"
        }
    }
}
