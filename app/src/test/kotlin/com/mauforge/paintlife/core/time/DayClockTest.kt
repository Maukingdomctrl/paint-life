package com.mauforge.paintlife.core.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class DayClockTest {

    /** Wall clock that moves with the test's virtual time, so waiting until midnight is instant. */
    private class SchedulerClock(private val start: Instant, private val scheduler: TestCoroutineScheduler) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
    }

    private val london: ZoneId = ZoneId.of("Europe/London")

    private fun TestScope.collect(start: LocalDateTime, zone: () -> ZoneId, signals: MutableSharedFlow<Unit> = MutableSharedFlow()): MutableList<DayMoment> {
        val clock = SchedulerClock(start.atZone(zone()).toInstant(), testScheduler)
        val dayClock = DayClock(TimeProvider(clock, zone), signals)
        val seen = mutableListOf<DayMoment>()
        backgroundScope.launch { dayClock.moments.collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun `the new day arrives at local midnight`() = runTest {
        val seen = collect(LocalDateTime.of(2026, 11, 9, 23, 59, 30), { london })
        assertEquals(LocalDate.of(2026, 11, 9), seen.last().date)

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(1, seen.size)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(DayMoment(LocalDate.of(2026, 11, 10), london, DayPart.Morning), seen.last())
        assertEquals(2, seen.size)
    }

    @Test
    fun `the greeting changes at noon and six in the evening`() = runTest {
        val seen = collect(LocalDateTime.of(2026, 11, 9, 11, 59), { london })
        assertEquals(DayPart.Morning, seen.last().part)

        advanceTimeBy(Duration.ofMinutes(1).toMillis() + 1)
        runCurrent()
        assertEquals(DayPart.Afternoon, seen.last().part)

        advanceTimeBy(Duration.ofHours(6).toMillis())
        runCurrent()
        assertEquals(DayPart.Evening, seen.last().part)
        assertEquals(listOf(DayPart.Morning, DayPart.Afternoon, DayPart.Evening), seen.map { it.part })
    }

    @Test
    fun `a time zone change is picked up as soon as the system reports it`() = runTest {
        var zone: ZoneId = ZoneId.of("Pacific/Honolulu")
        val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val seen = collect(LocalDateTime.of(2026, 11, 9, 1, 0), { zone }, signals)
        assertEquals(LocalDate.of(2026, 11, 9), seen.last().date)

        zone = ZoneId.of("Pacific/Kiritimati")
        signals.tryEmit(Unit)
        runCurrent()
        assertEquals(LocalDate.of(2026, 11, 10), seen.last().date)
        assertEquals(zone, seen.last().zone)
    }

    @Test
    fun `a missed broadcast is caught within a minute`() = runTest {
        var zone: ZoneId = ZoneId.of("Pacific/Honolulu")
        val seen = collect(LocalDateTime.of(2026, 11, 9, 1, 0), { zone })

        zone = ZoneId.of("Pacific/Kiritimati")
        advanceTimeBy(DayClock.RECHECK.toMillis() + 1)
        runCurrent()
        assertEquals(LocalDate.of(2026, 11, 10), seen.last().date)
    }

    @Test
    fun `nothing is emitted again while the day and part stay the same`() = runTest {
        val seen = collect(LocalDateTime.of(2026, 11, 9, 13, 0), { london })
        advanceTimeBy(Duration.ofHours(4).toMillis())
        runCurrent()
        assertEquals(1, seen.size)
    }

    @Test
    fun `DST spring-forward day is 23 hours and the next day still arrives at midnight`() = runTest {
        // Europe/London jumps from 01:00 to 02:00 on 29 March 2026.
        val seen = collect(LocalDateTime.of(2026, 3, 29, 0, 30), { london })
        advanceTimeBy(Duration.ofHours(23).toMillis() - Duration.ofMinutes(30).toMillis() - 1_000)
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 29), seen.last().date)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 30), seen.last().date)
    }

    @Test
    fun `DST fall-back day is 25 hours long`() = runTest {
        // Europe/London goes back from 02:00 to 01:00 on 25 October 2026.
        val seen = collect(LocalDateTime.of(2026, 10, 25, 0, 0), { london })
        advanceTimeBy(Duration.ofHours(25).toMillis() - 1_000)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 25), seen.last().date)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 26), seen.last().date)
    }

    @Test
    fun `a day that has no midnight starts at its first valid instant`() {
        // Cuba moves from 00:00 straight to 01:00 on 8 March 2026.
        val havana = ZoneId.of("America/Havana")
        val lateEvening = ZonedDateTime.of(2026, 3, 7, 23, 30, 0, 0, havana).toInstant()

        val next = DayMoment.nextChange(lateEvening, havana)

        assertEquals(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, havana).toInstant(), next)
        assertEquals(LocalDate.of(2026, 3, 8), DayMoment.at(next, havana).date)
    }

    @Test
    fun `day parts split at noon and six`() {
        assertEquals(DayPart.Morning, DayPart.at(java.time.LocalTime.MIDNIGHT))
        assertEquals(DayPart.Morning, DayPart.at(java.time.LocalTime.of(11, 59, 59)))
        assertEquals(DayPart.Afternoon, DayPart.at(java.time.LocalTime.NOON))
        assertEquals(DayPart.Afternoon, DayPart.at(java.time.LocalTime.of(17, 59)))
        assertEquals(DayPart.Evening, DayPart.at(java.time.LocalTime.of(18, 0)))
    }
}
