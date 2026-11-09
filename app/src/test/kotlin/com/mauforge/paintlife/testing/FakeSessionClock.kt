package com.mauforge.paintlife.testing

import com.mauforge.paintlife.core.time.SessionClock

/** A monotonic clock and boot count the test sets by hand. Set [boot] to simulate a reboot. */
class FakeSessionClock(@Volatile var now: Long = 0L, @Volatile var boot: Int? = 1) {
    val clock = SessionClock(elapsed = { now }, boot = { boot })

    fun advance(millis: Long) {
        now += millis
    }
}
