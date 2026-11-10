package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.mytime.PlanningFixtures.entry
import com.mauforge.paintlife.mytime.PlanningFixtures.london
import com.mauforge.paintlife.mytime.PlanningFixtures.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleSuggesterTest {

    /** A Tuesday: the window is the 28 days before it. */
    private val today: LocalDate = LocalDate.of(2026, 11, 10)

    private fun past(days: Int): List<LocalDate> = (1..days).map { today.minusDays(it.toLong()) }

    private fun suggest(actual: List<TimeEntryEntity>): SuggestionResult {
        var n = 0
        return ScheduleSuggester.suggest(today, london, actual) { "b${n++}" }
    }

    private fun ready(actual: List<TimeEntryEntity>) = suggest(actual) as SuggestionResult.Ready

    private fun ProposalBlock.short() = Triple(categoryId, startMinute, endMinute)

    @Test
    fun fewerThanSevenRecordedDaysSuggestsNothing() {
        // Six full days, plus a day with only 50 minutes (not a recorded day).
        val actual = past(6).map { entry("work", it.atTime(9, 0), it.atTime(17, 0)) } +
            entry("work", today.minusDays(7).atTime(9, 0), today.minusDays(7).atTime(9, 50))
        assertEquals(SuggestionResult.NotEnough(6), suggest(actual))
    }

    @Test
    fun aCategoryOnAtLeastHalfTheDaysGoesInAndLessStaysOpen() {
        // 8 recorded days, none on a weekend, so one pattern. Work 09:00-12:00 on all 8;
        // learning 18:00-19:00 on exactly 4 (half); family 20:00-21:00 on 3 (less than half).
        val days = past(12).filter { it.dayOfWeek < DayOfWeek.SATURDAY }.take(8)
        val actual = days.flatMapIndexed { i, d ->
            listOfNotNull(
                entry("work", d.atTime(9, 0), d.atTime(12, 0)),
                if (i < 4) entry("learning", d.atTime(18, 0), d.atTime(19, 0)) else null,
                if (i < 3) entry("family", d.atTime(20, 0), d.atTime(21, 0)) else null,
            )
        }
        val r = ready(actual)
        assertFalse(r.split)
        assertEquals(8, r.recordedDays)
        assertEquals(
            listOf(Triple("work", minutes(9), minutes(12)), Triple("learning", minutes(18), minutes(19))),
            r.blocks.map { it.short() },
        )
        assertEquals(listOf(8, 4), r.blocks.map { it.seenDays })
        assertTrue(r.blocks.all { it.daysMask == ScheduleRules.ALL_DAYS && it.groupDays == 8 })
    }

    @Test
    fun aTieLeavesTheSlotOpen() {
        // 8 days: work on 4, learning on 4, same hour.
        val days = past(12).filter { it.dayOfWeek < DayOfWeek.SATURDAY }.take(8)
        val actual = days.mapIndexed { i, d -> entry(if (i % 2 == 0) "work" else "learning", d.atTime(9, 0), d.atTime(12, 0)) }
        assertEquals(emptyList<ProposalBlock>(), ready(actual).blocks)
    }

    @Test
    fun aSlotNeedsFifteenMinutesToCount() {
        // 14:00-14:10 every day is not enough to fill the 14:00 slot.
        val days = past(10)
        val actual = days.flatMap { listOf(entry("work", it.atTime(9, 0), it.atTime(11, 0)), entry("rest", it.atTime(14, 0), it.atTime(14, 10))) }
        // Weekdays and weekends both had it, so each pattern has the same block.
        assertEquals(setOf(Triple("work", minutes(9), minutes(11))), ready(actual).blocks.map { it.short() }.toSet())
    }

    @Test
    fun patchyRecordingLeavesTimeOpenAndSaysHowMuch() {
        // 10 days, each with only 09:00-11:00 recorded. Unrecorded time stays in the count.
        val actual = past(10).map { entry("work", it.atTime(9, 0), it.atTime(11, 0)) }
        val r = ready(actual)
        assertEquals(setOf(Triple("work", minutes(9), minutes(11))), r.blocks.map { it.short() }.toSet())
        assertEquals(92, r.unrecordedPercent)
    }

    @Test
    fun anIrregularWeekGivesFewBlocks() {
        // The same 3 hours, but a different category every day.
        val cats = listOf("work", "learning", "household", "family", "rest", "entertainment", "phone_internet", "other")
        val actual = past(8).mapIndexed { i, d -> entry(cats[i], d.atTime(9, 0), d.atTime(12, 0)) }
        val r = ready(actual)
        assertEquals(8, r.recordedDays)
        assertEquals(emptyList<ProposalBlock>(), r.blocks)
    }

    @Test
    fun weekdaysAndWeekendsSplitWithEnoughOfEach() {
        val actual = past(14).map { d ->
            if (d.dayOfWeek >= DayOfWeek.SATURDAY) entry("family", d.atTime(10, 0), d.atTime(13, 0)) else entry("work", d.atTime(9, 0), d.atTime(17, 0))
        }
        val r = ready(actual)
        assertTrue(r.split)
        assertEquals(10, r.weekdayDays)
        assertEquals(4, r.weekendDays)
        assertEquals(
            listOf(Triple(Triple("work", minutes(9), minutes(17)), ScheduleRules.WEEKDAYS, 10), Triple(Triple("family", minutes(10), minutes(13)), ScheduleRules.WEEKEND, 4)),
            r.blocks.map { Triple(it.short(), it.daysMask, it.groupDays) },
        )
    }

    @Test
    fun oneWeekendDayIsNotEnoughToSplit() {
        // 7 days ending yesterday (Mon): Tue-Fri, Sat, Sun, Mon. Only use Sunday from the weekend.
        val actual = past(7).filter { it.dayOfWeek != DayOfWeek.SATURDAY }.map { entry("work", it.atTime(9, 0), it.atTime(17, 0)) } +
            past(14).filter { it.dayOfWeek < DayOfWeek.SATURDAY }.drop(5).take(1).map { entry("work", it.atTime(9, 0), it.atTime(17, 0)) }
        val r = ready(actual)
        assertFalse(r.split)
        assertEquals(ScheduleRules.ALL_DAYS, r.blocks.single().daysMask)
    }

    @Test
    fun sleepCrossesMidnightAsOneBlock() {
        val actual = past(9).flatMap { d ->
            listOf(entry("sleep", d.atTime(0, 0), d.atTime(7, 0)), entry("sleep", d.atTime(23, 0), d.plusDays(1).atTime(0, 0)))
        }.filter { it.endAt <= today.atStartOfDay(london).toInstant() }
        val r = ready(actual)
        // 6 weekdays and 3 weekend days: one sleep block in each pattern, both across midnight.
        assertEquals(listOf(ScheduleRules.WEEKDAYS, ScheduleRules.WEEKEND), r.blocks.map { it.daysMask })
        assertEquals(setOf(Triple("sleep", minutes(23), minutes(7))), r.blocks.map { it.short() }.toSet())
    }

    @Test
    fun aDstDaySkipsTheMissingHour() {
        // Around 29 March 2026 (UK clocks forward at 01:00): Learning 01:00-03:00 wall time
        // every night. On the 29th, 01:00-02:00 doesn't exist, so that day only fills 02:00-03:00.
        val springToday = LocalDate.of(2026, 3, 31)
        val days = (1..8).map { springToday.minusDays(it.toLong()) }
        val actual = days.map { d ->
            val start = if (d == LocalDate.of(2026, 3, 29)) d.atTime(2, 0) else d.atTime(1, 0)
            entry("learning", start, d.atTime(3, 0))
        }
        val r = ScheduleSuggester.suggest(springToday, london, actual) { "x" } as SuggestionResult.Ready
        // The 29th is a Sunday. Weekdays (6) fill 01:00-03:00 every time; at the weekend only
        // Saturday fills 01:00-02:00, which is still half of the 2 weekend days.
        assertTrue(r.split)
        val weekdays = r.blocks.single { it.daysMask == ScheduleRules.WEEKDAYS }
        val weekend = r.blocks.single { it.daysMask == ScheduleRules.WEEKEND }
        assertEquals(Triple("learning", minutes(1), minutes(3)), weekdays.short())
        assertEquals(6 to 6, weekdays.seenDays to weekdays.groupDays)
        assertEquals(Triple("learning", minutes(1), minutes(3)), weekend.short())
        assertEquals(1 to 2, weekend.seenDays to weekend.groupDays)
    }
}
