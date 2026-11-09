package com.mauforge.paintlife.testing

import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves by hand. */
class MutableClock(@Volatile var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant
}

/**
 * Hand-driven time for tests: set [zone] or move the clock, then call [announceChange] to play
 * the system's "time / date / zone changed" broadcast.
 */
class TestTime(start: LocalDateTime, @Volatile var zone: ZoneId) {
    val clock = MutableClock(start.atZone(zone).toInstant())
    val provider = TimeProvider(clock) { zone }
    val systemTimeChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val dayClock = DayClock(provider, systemTimeChanges)

    fun setLocal(dateTime: LocalDateTime) {
        clock.instant = dateTime.atZone(zone).toInstant()
    }

    fun announceChange() {
        check(systemTimeChanges.tryEmit(Unit))
    }
}
