package com.mauforge.paintlife.mytime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimeRulesTest {

    private val london = ZoneId.of("Europe/London")
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 9)

    private fun at(date: LocalDate, h: Int, m: Int = 0, zone: ZoneId = london): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun entry(id: String, start: Instant, end: Instant, category: String = "work", zone: ZoneId = london) = TimeEntryEntity(
        id = id,
        kind = TimeEntryKind.ACTUAL,
        categoryId = category,
        startAt = start,
        endAt = end,
        zoneId = zone,
        endZoneId = zone,
        note = null,
        createdAt = start,
        createdZoneId = zone,
        updatedAt = start,
        updatedZoneId = zone,
    )

    // Midnight (spec §7)

    @Test
    fun aSleepCrossingMidnightGivesEachDayItsShare() {
        val split = TimeRules.minutesByDay(at(day, 23), at(day.plusDays(1), 7), london)
        assertEquals(mapOf(day to 60L, day.plusDays(1) to 420L), split)
        assertEquals(480L, split.values.sum())
    }

    @Test
    fun anEntryEndingExactlyAtMidnightStaysOnItsDay() {
        assertEquals(mapOf(day to 120L), TimeRules.minutesByDay(at(day, 22), at(day.plusDays(1), 0), london))
    }

    @Test
    fun theSplitUsesTheEntrysOwnZone() {
        // 23:00-01:00 in Kolkata is 17:30-19:30 in London: one London day, two Kolkata days.
        val start = at(day, 23, zone = kolkata)
        val end = at(day.plusDays(1), 1, zone = kolkata)
        assertEquals(mapOf(day to 60L, day.plusDays(1) to 60L), TimeRules.minutesByDay(start, end, kolkata))
        assertEquals(mapOf(day to 120L), TimeRules.minutesByDay(start, end, london))
    }

    // DST

    @Test
    fun aNightOverClocksGoingForwardIsAnHourShorter() {
        val night = LocalDate.of(2026, 3, 28)
        val split = TimeRules.minutesByDay(at(night, 23), at(night.plusDays(1), 7), london)
        assertEquals(mapOf(night to 60L, night.plusDays(1) to 360L), split)
    }

    @Test
    fun aNightOverClocksGoingBackIsAnHourLonger() {
        val night = LocalDate.of(2026, 10, 24)
        val split = TimeRules.minutesByDay(at(night, 23), at(night.plusDays(1), 7), london)
        assertEquals(mapOf(night to 60L, night.plusDays(1) to 480L), split)
    }

    @Test
    fun aTimeThatHappensTwiceIsMarkedFirstOrSecond() {
        val back = LocalDate.of(2026, 10, 25)
        val first = LocalDateTime.of(back, LocalTime.of(1, 30)).atZone(london).withEarlierOffsetAtOverlap().toInstant()
        val second = first.plus(Duration.ofHours(1))
        assertEquals(WallTime("01:30", 1), TimeRules.wallTime(first, london))
        assertEquals(WallTime("01:30", 2), TimeRules.wallTime(second, london))
        assertEquals(WallTime("03:00", null), TimeRules.wallTime(at(back, 3), london))
    }

    // Limits and rounding

    @Test
    fun entriesAreOneMinuteToTwentyFourHours() {
        val start = at(day, 9)
        assertFalse(TimeRules.isValidSpan(start, start))
        assertTrue(TimeRules.isValidSpan(start, start.plusSeconds(60)))
        assertTrue(TimeRules.isValidSpan(start, start.plus(Duration.ofHours(24))))
        assertFalse(TimeRules.isValidSpan(start, start.plus(Duration.ofHours(24)).plusSeconds(60)))
        assertFalse(TimeRules.isValidSpan(start, start.minusSeconds(60)))
    }

    @Test
    fun nowRoundsToTheNearestFiveMinutes() {
        assertEquals(at(day, 9, 5), TimeRules.roundToStep(at(day, 9, 7).minusSeconds(30)))
        assertEquals(at(day, 9, 10), TimeRules.roundToStep(at(day, 9, 8)))
        assertEquals(at(day, 9, 10), TimeRules.roundToStep(at(day, 9, 10)))
        // Zones with half-hour offsets still land on :00/:05 local times.
        assertEquals(at(day, 9, 30, kolkata), TimeRules.roundToStep(at(day, 9, 31, kolkata)))
    }

    // Overlaps

    @Test
    fun actualTimeEndsNoLaterThanNowOnTheFiveMinuteGrid() {
        assertEquals(at(day, 10, 0), TimeRules.latestActualEnd(at(day, 10, 0)))
        // 10:03 rounds up to 10:05, the mark the pickers offer as "now".
        assertEquals(at(day, 10, 5), TimeRules.latestActualEnd(at(day, 10, 3)))
        // 10:02 rounds down to 10:00, but this minute has begun: a running activity may stop in it.
        assertEquals(at(day, 10, 2), TimeRules.latestActualEnd(at(day, 10, 2).plusSeconds(20)))
    }

    @Test
    fun lastNightsSleepIsOfferedOnlyUpToNow() {
        val sleep = at(day.minusDays(1), 23) to at(day, 7)
        // Logged after waking: the whole night.
        assertEquals(sleep, TimeRules.sleepSoFar(sleep, at(day, 9)))
        // Up at 06:30, before the usual 07:00: the sleep ends now, not in the future.
        assertEquals(sleep.first to at(day, 6, 30), TimeRules.sleepSoFar(sleep, at(day, 6, 30)))
        // Before the usual bedtime there is no night to log yet.
        assertEquals(null, TimeRules.sleepSoFar(sleep, at(day.minusDays(1), 22)))
    }

    @Test
    fun fittingTrimsPartialNeighboursSplitsAContainingOneAndRemovesCoveredOnes() {
        val before = entry("before", at(day, 8), at(day, 10))
        val covered = entry("covered", at(day, 10, 15), at(day, 10, 45))
        val after = entry("after", at(day, 10, 50), at(day, 12))
        val changes = TimeRules.planOverlaps(at(day, 9, 30), at(day, 11), listOf(before, covered, after), Resolution.Fit)
        assertEquals(
            listOf(
                OverlapChange.Trim(before, at(day, 8), at(day, 9, 30)),
                OverlapChange.Remove(covered),
                OverlapChange.Trim(after, at(day, 11), at(day, 12)),
            ),
            changes,
        )

        val long = entry("long", at(day, 9), at(day, 12, 30))
        assertEquals(
            listOf(OverlapChange.Split(long, firstEnd = at(day, 10), secondStart = at(day, 10, 20))),
            TimeRules.planOverlaps(at(day, 10), at(day, 10, 20), listOf(long), Resolution.Fit),
        )
    }

    @Test
    fun replacingRemovesEveryOverlappingNeighbourAndTouchingIsNotOverlapping() {
        val before = entry("before", at(day, 8), at(day, 10))
        val touching = entry("touching", at(day, 11), at(day, 12))
        assertEquals(
            listOf(OverlapChange.Remove(before)),
            TimeRules.planOverlaps(at(day, 9), at(day, 11), listOf(before, touching), Resolution.Replace),
        )
    }

    // Observation

    @Test
    fun observationCountsDaysWithAtLeastAnHour() {
        val spans = listOf(
            EntrySpan(at(day, 9), at(day, 10), london),
            EntrySpan(at(day.plusDays(1), 9), at(day.plusDays(1), 9, 59), london),
            // 30 + 30 minutes on one day add up to an hour.
            EntrySpan(at(day.plusDays(2), 9), at(day.plusDays(2), 9, 30), london),
            EntrySpan(at(day.plusDays(2), 20), at(day.plusDays(2), 20, 30), london),
            // A night gives an hour to the first day and more to the next.
            EntrySpan(at(day.plusDays(3), 23), at(day.plusDays(4), 7), london),
        )
        assertEquals(4, TimeRules.observedDays(spans))
    }

    @Test
    fun observationNeverCountsMoreThanSevenDays() {
        val spans = (0L until 10L).map { EntrySpan(at(day.plusDays(it), 9), at(day.plusDays(it), 11), london) }
        assertEquals(7, TimeRules.observedDays(spans))
    }

    // Sleep

    @Test
    fun withoutSleepsTheSleepCardOffersElevenToSeven() {
        assertEquals(at(day.minusDays(1), 23) to at(day, 7), TimeRules.sleepPrefill(emptyList(), day, london))
    }

    @Test
    fun theSleepCardUsesTheMedianBedtimeAcrossMidnight() {
        val sleeps = listOf(
            entry("a", at(day.minusDays(4), 23, 30), at(day.minusDays(3), 6, 30), "sleep"),
            entry("b", at(day.minusDays(3), 0, 30), at(day.minusDays(3), 7, 30), "sleep"),
            entry("c", at(day.minusDays(2), 0, 0), at(day.minusDays(2), 7, 0), "sleep"),
        )
        // Bedtimes 23:30, 00:00, 00:30: the median is midnight, on the morning's own date.
        assertEquals(at(day, 0) to at(day, 7), TimeRules.sleepPrefill(sleeps, day, london))
    }

    @Test
    fun theWakeTimeIsAskedForAnHourAfterTheUsualWakeTime() {
        val bed = at(day.minusDays(1), 23, 14)
        val usualWake = at(day, 7)
        assertFalse(TimeRules.wakePromptDue(bed, usualWake, at(day, 7, 59)))
        assertTrue(TimeRules.wakePromptDue(bed, usualWake, at(day, 8)))
        // A nap that began after this morning's wake time is not a forgotten night.
        assertFalse(TimeRules.wakePromptDue(at(day, 14), usualWake, at(day, 18)))
    }
}
