package com.mauforge.paintlife.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single source of "now". Instants come from [clock] (UTC); the zone is read on
 * every call so a time-zone or DST change is picked up immediately instead of
 * being captured once at startup. Tests pass a fixed clock and zone.
 */
class TimeProvider(
    private val clock: Clock,
    private val zoneSupplier: () -> ZoneId,
) {
    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = zoneSupplier()

    // atZone() rather than LocalDate.ofInstant(), which needs API 34.
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
}
