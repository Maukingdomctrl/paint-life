package com.mauforge.paintlife.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `clock shows minutes and seconds, hours only when needed`() {
        assertEquals("0:00", DurationFormat.clock(0))
        assertEquals("0:59", DurationFormat.clock(59_999))
        assertEquals("29:41", DurationFormat.clock((29 * 60 + 41) * 1000L))
        assertEquals("1:02:03", DurationFormat.clock((3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun `clock never shows a negative time`() {
        assertEquals("0:00", DurationFormat.clock(-5_000))
    }

    @Test
    fun `countdown rounds up so it reaches 0_00 only when time is up`() {
        assertEquals("5:00", DurationFormat.countdown(300_000))
        assertEquals("5:00", DurationFormat.countdown(299_001))
        assertEquals("0:01", DurationFormat.countdown(1))
        assertEquals("0:00", DurationFormat.countdown(0))
    }

    @Test
    fun `hours and minutes drop empty parts`() {
        assertEquals("0m", DurationFormat.hoursMinutes(0))
        assertEquals("45m", DurationFormat.hoursMinutes(45))
        assertEquals("5h", DurationFormat.hoursMinutes(300))
        assertEquals("5h 40m", DurationFormat.hoursMinutes(340))
    }
}
