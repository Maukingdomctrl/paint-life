package com.mauforge.paintlife.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeProviderTest {

    private val instant = Instant.parse("2026-11-09T23:30:00Z")

    @Test
    fun `today follows the current zone, not UTC`() {
        val provider = TimeProvider(Clock.fixed(instant, ZoneOffset.UTC)) { ZoneId.of("Asia/Kolkata") }
        assertEquals(LocalDate.of(2026, 11, 10), provider.today())
    }

    @Test
    fun `a zone change is picked up without restarting`() {
        var zone: ZoneId = ZoneId.of("America/New_York")
        val provider = TimeProvider(Clock.fixed(instant, ZoneOffset.UTC)) { zone }
        assertEquals(LocalDate.of(2026, 11, 9), provider.today())

        zone = ZoneId.of("Asia/Tokyo")
        assertEquals(LocalDate.of(2026, 11, 10), provider.today())
        assertEquals(instant, provider.now())
    }
}
