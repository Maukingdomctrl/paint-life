package com.mauforge.paintlife.mytime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DayViewTest {

    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val day = LocalDate.of(2026, 11, 9)

    private fun at(date: LocalDate, h: Int, m: Int = 0, zone: ZoneId = london): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun item(id: String, start: Instant, end: Instant, category: String, color: String, zone: ZoneId = london) = EntryWithCategory(
        TimeEntryEntity(id, TimeEntryKind.ACTUAL, category, start, end, zone, zone, null, start, zone, start, zone),
        categoryName = category.replaceFirstChar { it.uppercase() },
        colorKey = color,
        isDistraction = false,
    )

    private val night = item("night", at(day.minusDays(1), 23), at(day, 7), "sleep", "night")
    private val work = item("work", at(day, 9), at(day, 12, 30), "work", "slate")
    private val evening = item("evening", at(day, 22), at(day.plusDays(1), 6), "sleep", "night")

    @Test
    fun blocksAreClippedToTheDayAndSayTheyContinue() {
        val blocks = DayView.blocks(listOf(night, work, evening), day, london)
        assertEquals(listOf(MinuteSpan(0, 420), MinuteSpan(540, 750), MinuteSpan(1320, 1440)), blocks.map { it.span })
        assertTrue(blocks[0].continuesBefore)
        assertTrue(blocks[2].continuesAfter)
        assertEquals("23:00", blocks[0].startLabel)
        assertEquals("06:00", blocks[2].endLabel)
    }

    @Test
    fun totalsCountOnlyTheDaysShareOfACrossingEntry() {
        val totals = DayView.categoryTotals(listOf(night, work, evening), day, london)
        assertEquals(listOf("sleep" to 540L, "work" to 210L), totals.map { it.categoryId to it.minutes })
    }

    @Test
    fun theDaySoFarHasRecordedStretchesAndGaps() {
        val stretches = DayView.stretches(listOf(night, work), day, london, untilMinute = 600)
        assertEquals(
            listOf(DayStretch(0, 420, "night"), DayStretch(420, 540, null), DayStretch(540, 600, "slate")),
            stretches,
        )
    }

    @Test
    fun todayUsesTheCurrentZoneAndAPastDayTheZoneOfItsLatestEntry() {
        val inTokyo = item("tokyo", at(day, 9, zone = tokyo), at(day, 10, zone = tokyo), "work", "slate", tokyo)
        assertEquals(london, DayView.zoneFor(day, today = day, currentZone = london, entries = listOf(inTokyo)))
        assertEquals(tokyo, DayView.zoneFor(day, today = day.plusDays(3), currentZone = london, entries = listOf(night, inTokyo)))
        assertEquals(london, DayView.zoneFor(day.minusDays(5), today = day, currentZone = london, entries = emptyList()))
        assertTrue(DayView.hasOtherZones(listOf(work, inTokyo), day, london))
        assertFalse(DayView.hasOtherZones(listOf(work), day, london))
    }

    @Test
    fun theReadWindowCoversTheDayInEveryZone() {
        val (from, to) = DayView.readWindow(day)
        assertTrue(from <= day.atStartOfDay(ZoneId.of("Pacific/Kiritimati")).toInstant())
        assertTrue(to >= day.plusDays(1).atStartOfDay(ZoneId.of("Pacific/Pago_Pago")).toInstant())
    }
}
