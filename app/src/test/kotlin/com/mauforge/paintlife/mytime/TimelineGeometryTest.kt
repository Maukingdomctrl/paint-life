package com.mauforge.paintlife.mytime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimelineGeometryTest {

    private val london = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 11, 12)

    private fun at(date: LocalDate, h: Int, m: Int = 0) = ZonedDateTime.of(date, LocalTime.of(h, m), london).toInstant()

    @Test
    fun `an ordinary day is 1440 minutes with 24 hour marks`() {
        assertEquals(1440, TimelineGeometry.dayLengthMinutes(day, london))
        val marks = TimelineGeometry.hourMarks(day, london)
        assertEquals(24, marks.size)
        assertEquals((0..23).toList(), marks.map { it.hour })
    }

    @Test
    fun `an entry inside the day maps to minutes from midnight`() {
        assertEquals(MinuteSpan(540, 750), TimelineGeometry.clipToDay(at(day, 9), at(day, 12, 30), day, london))
    }

    @Test
    fun `an entry crossing midnight is split so each day gets its share`() {
        val start = at(day, 22, 30)
        val end = at(day.plusDays(1), 7, 0)
        assertEquals(MinuteSpan(1350, 1440), TimelineGeometry.clipToDay(start, end, day, london))
        assertEquals(MinuteSpan(0, 420), TimelineGeometry.clipToDay(start, end, day.plusDays(1), london))
    }

    @Test
    fun `an entry on another day does not appear`() {
        assertNull(TimelineGeometry.clipToDay(at(day.minusDays(1), 9), at(day.minusDays(1), 10), day, london))
    }

    @Test
    fun `spring forward day is 23 hours and skips the 01_00 label`() {
        val spring = LocalDate.of(2026, 3, 29)
        assertEquals(1380, TimelineGeometry.dayLengthMinutes(spring, london))
        val hours = TimelineGeometry.hourMarks(spring, london).map { it.hour }
        assertEquals(23, hours.size)
        assertEquals(listOf(0, 2, 3), hours.take(3))
    }

    @Test
    fun `fall back day is 25 hours and shows 01_00 twice`() {
        val autumn = LocalDate.of(2026, 10, 25)
        assertEquals(1500, TimelineGeometry.dayLengthMinutes(autumn, london))
        val hours = TimelineGeometry.hourMarks(autumn, london).map { it.hour }
        assertEquals(25, hours.size)
        assertEquals(listOf(0, 1, 1, 2), hours.take(4))
    }

    @Test
    fun `durations on a DST day use real elapsed minutes`() {
        val autumn = LocalDate.of(2026, 10, 25)
        // 00:00 to 03:00 local on the fall-back day is four real hours.
        assertEquals(MinuteSpan(0, 240), TimelineGeometry.clipToDay(at(autumn, 0), at(autumn, 3), autumn, london))
    }

    @Test
    fun `gaps are the unrecorded stretches up to now`() {
        val spans = listOf(MinuteSpan(0, 420), MinuteSpan(480, 600), MinuteSpan(590, 700))
        assertEquals(
            listOf(MinuteSpan(420, 480), MinuteSpan(700, 800)),
            TimelineGeometry.unrecordedGaps(spans, untilMinute = 800),
        )
    }

    @Test
    fun `no gaps when the day is fully recorded or nothing has passed`() {
        assertEquals(emptyList<MinuteSpan>(), TimelineGeometry.unrecordedGaps(listOf(MinuteSpan(0, 1440)), 1440))
        assertEquals(emptyList<MinuteSpan>(), TimelineGeometry.unrecordedGaps(emptyList(), 0))
    }

    @Test
    fun `a gap stops at now even when a later entry exists`() {
        assertEquals(listOf(MinuteSpan(0, 300)), TimelineGeometry.unrecordedGaps(listOf(MinuteSpan(600, 700)), untilMinute = 300))
    }
}
