package com.mauforge.paintlife.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `instant round-trips at millisecond precision`() {
        val instant = Instant.parse("2026-03-29T00:59:59.123Z")
        val stored = converters.instantToEpochMillis(instant)
        assertEquals(instant, converters.epochMillisToInstant(stored))
    }

    @Test
    fun `sub-millisecond precision is truncated, not rounded`() {
        val instant = Instant.parse("2026-11-09T10:15:30.123999999Z")
        val restored = converters.epochMillisToInstant(converters.instantToEpochMillis(instant))
        assertEquals(Instant.parse("2026-11-09T10:15:30.123Z"), restored)
    }

    @Test
    fun `instant plus zone restores the original local wall-clock time across a DST change`() {
        // 29 March 2026: UK clocks jump from 01:00 GMT to 02:00 BST; 02:30 is BST (UTC+1).
        val zone = ZoneId.of("Europe/London")
        val original = ZonedDateTime.of(2026, 3, 29, 2, 30, 0, 0, zone)
        val storedInstant = converters.instantToEpochMillis(original.toInstant())
        val storedZone = converters.zoneIdToString(original.zone)

        val restored = ZonedDateTime.ofInstant(
            converters.epochMillisToInstant(storedInstant),
            converters.stringToZoneId(storedZone),
        )
        assertEquals(original, restored)
    }

    @Test
    fun `zone ids and fixed offsets round-trip`() {
        listOf(ZoneId.of("Asia/Kolkata"), ZoneId.of("America/New_York"), ZoneOffset.ofHoursMinutes(5, 30), ZoneOffset.UTC)
            .forEach { zone -> assertEquals(zone, converters.stringToZoneId(converters.zoneIdToString(zone))) }
    }

    @Test
    fun `local dates are stored as ISO text that sorts chronologically`() {
        val dates = listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 2, 1), LocalDate.of(2027, 1, 1))
        val stored = dates.map { converters.localDateToString(it)!! }
        assertEquals("2026-12-31", stored[0])
        assertEquals(dates.sorted(), stored.sorted().map { converters.stringToLocalDate(it) })
    }

    @Test
    fun `nulls stay null`() {
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
        assertNull(converters.zoneIdToString(null))
        assertNull(converters.stringToZoneId(null))
        assertNull(converters.localDateToString(null))
        assertNull(converters.stringToLocalDate(null))
    }
}
