package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.core.database.SeedData
import com.mauforge.paintlife.mytime.PlanningFixtures.london
import com.mauforge.paintlife.mytime.PlanningFixtures.minutes
import com.mauforge.paintlife.mytime.PlanningFixtures.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Pure reminder planning: lead times, one-off, snooze, bedtime, schedules, time zones and DST. */
class ReminderPlannerTest {

    /** A Monday, in winter time (GMT). */
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val tokyo: ZoneId = ZoneId.of("Asia/Tokyo")

    private fun at(dateTime: LocalDateTime, zone: ZoneId = london): Instant = dateTime.atZone(zone).toInstant()

    private fun at(h: Int, m: Int = 0, date: LocalDate = day): Instant = at(date.atTime(h, m))

    /** A planned entry of an hour, with its reminder set (the entry last saved) at [setAt]. */
    private fun plan(category: String, start: Instant, lead: Int?, setAt: Instant = at(8)): TimeEntryEntity =
        PlanningFixtures.entryAt(category, start, start.plus(Duration.ofHours(1)), TimeEntryKind.PLANNED)
            .copy(reminderMinutes = lead, updatedAt = setAt)

    private fun sources(
        planned: List<TimeEntryEntity> = emptyList(),
        schedules: List<ScheduleWithBlocks> = emptyList(),
        reminders: List<ReminderEntity> = emptyList(),
        bedtimeMinute: Int? = null,
        bedtimeSetAt: Instant? = null,
    ) = ReminderSources(planned, schedules, reminders, bedtimeMinute, bedtimeSetAt)

    private fun occurrences(sources: ReminderSources, now: Instant = at(8), after: Instant = now, zone: ZoneId = london) =
        ReminderPlanner.occurrences(sources, after, now, zone)

    private fun reminding(schedule: ScheduleWithBlocks, lead: Int, setAt: Instant? = null) = schedule.copy(
        schedule = schedule.schedule.copy(updatedAt = setAt ?: schedule.schedule.updatedAt),
        blocks = schedule.blocks.map { it.copy(reminderMinutes = lead) },
    )

    private fun reminder(kind: ReminderKind, fireAt: Instant, title: String? = null, action: StartAction = StartAction.NONE, id: String = "r1") = ReminderEntity(
        id = id,
        kind = kind,
        title = title,
        categoryId = null,
        note = null,
        fireAt = fireAt,
        zoneId = london,
        activityStartAt = null,
        activityEndAt = null,
        action = action,
        createdAt = at(8),
        createdZoneId = london,
    )

    private val nothingRunning = RunningNow(exerciseOpen = false, quietOpen = false, currentCategoryId = null)

    // Lead times on a planned entry

    @Test
    fun theLeadTimesAreFiveTenFifteenAndThirtyMinutes() {
        assertEquals(listOf(5, 10, 15, 30), ReminderRules.LEADS)
        assertTrue(ReminderRules.isLead(null))
        ReminderRules.LEADS.forEach { assertTrue(ReminderRules.isLead(it)) }
        listOf(0, 1, 7, 20, 45, 60, -5).forEach { assertFalse("$it", ReminderRules.isLead(it)) }
    }

    @Test
    fun aPlannedEntryRemindsItsLeadTimeBeforeItStarts() {
        ReminderRules.LEADS.forEach { lead ->
            val entry = plan("work", at(15), lead)

            val found = occurrences(sources(planned = listOf(entry))).single()

            assertEquals(at(15).minus(Duration.ofMinutes(lead.toLong())), found.fireAt)
            assertEquals("entry:${entry.id}", found.key)
            assertEquals(ReminderSource.PLANNED, found.source)
            assertEquals("work", found.categoryId)
            assertEquals(entry.startAt, found.activityStartAt)
            assertEquals(entry.endAt, found.activityEndAt)
            assertNull(found.title)
        }
    }

    @Test
    fun aPlannedEntryWithoutAReminderNeverReminds() {
        val quiet = plan("work", at(15), lead = null)

        assertEquals(emptyList<ReminderOccurrence>(), occurrences(sources(planned = listOf(quiet))))
    }

    @Test
    fun nothingIsInventedWhenNothingIsSet() {
        val approved = schedule(day, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS))

        assertEquals(emptyList<ReminderOccurrence>(), occurrences(sources(planned = listOf(plan("family", at(19), null)), schedules = listOf(approved))))
    }

    @Test
    fun aReminderSetAfterItsOwnMomentNeverFires() {
        // Saved at 14:50 for 15:00 with a 15-minute lead: 14:45 has gone.
        val late = plan("work", at(15), lead = 15, setAt = at(14, 50))
        val inTime = plan("family", at(16), lead = 15, setAt = at(14, 50))

        val found = occurrences(sources(planned = listOf(late, inTime)), now = at(14, 50), after = at(14))

        assertEquals(listOf(at(15, 45)), found.map { it.fireAt })
        assertTrue(ReminderPlanner.leadPassed(at(15), 15, at(14, 50)))
        assertTrue(ReminderPlanner.leadPassed(at(15), 15, at(14, 45)))
        assertFalse(ReminderPlanner.leadPassed(at(15), 15, at(14, 44)))
        assertFalse(ReminderPlanner.leadPassed(at(15), null, at(14, 50)))
    }

    @Test
    fun remindersAlreadyHandledAreNotFoundAgainAndTheRestComeEarliestFirst() {
        val morning = plan("work", at(9), 10)
        val evening = plan("family", at(19), 30)
        val afternoon = plan("learning", at(15), 5)
        val all = sources(planned = listOf(morning, evening, afternoon))

        assertEquals(listOf(at(8, 50), at(14, 55), at(18, 30)), occurrences(all, now = at(8, 5), after = at(8)).map { it.fireAt })
        // Handled up to 08:50 exactly: that one is done.
        assertEquals(listOf(at(14, 55), at(18, 30)), occurrences(all, now = at(8, 50)).map { it.fireAt })
    }

    @Test
    fun startNowFollowsTheCategory() {
        assertEquals(StartAction.EXERCISE, ReminderRules.actionFor("exercise"))
        assertEquals(StartAction.SLEEP, ReminderRules.actionFor("sleep"))
        SeedData.categories.map { it.id }.filter { it != "exercise" && it != "sleep" }.forEach {
            assertEquals(it, StartAction.NOW_DOING, ReminderRules.actionFor(it))
        }
        // The two special ids are real categories.
        assertTrue(SeedData.categories.any { it.id == ReminderRules.EXERCISE_ID })
        assertTrue(SeedData.categories.any { it.id == TimeRules.SLEEP_ID })

        val found = occurrences(sources(planned = listOf(plan("exercise", at(10), 5), plan("sleep", at(23), 5), plan("work", at(14), 5))))
        assertEquals(listOf(StartAction.EXERCISE, StartAction.NOW_DOING, StartAction.SLEEP), found.map { it.action })
    }

    // One-off and snoozed reminders

    @Test
    fun aOneOffFiresAtItsTimeWithItsTitleAndAction() {
        val callBank = reminder(ReminderKind.ONE_OFF, at(11, 30), "Call the bank", StartAction.QUIET)

        val found = occurrences(sources(reminders = listOf(callBank))).single()

        assertEquals("oneoff:r1", found.key)
        assertEquals(ReminderSource.ONE_OFF, found.source)
        assertEquals(at(11, 30), found.fireAt)
        assertEquals("Call the bank", found.title)
        assertEquals(StartAction.QUIET, found.action)
        assertNull(found.activityStartAt)
    }

    @Test
    fun aOneOffFarAheadIsStillPlannedAndAPastOneIsNot() {
        val nextYear = reminder(ReminderKind.ONE_OFF, at(9, date = day.plusDays(300)), "Renew passport", id = "far")
        val gone = reminder(ReminderKind.ONE_OFF, at(7), "Earlier today", id = "gone")

        assertEquals(listOf("oneoff:far"), occurrences(sources(reminders = listOf(nextYear, gone))).map { it.key })
    }

    @Test
    fun aSnoozedReminderComesBackAtItsNewTimeWithItsActivity() {
        val snoozed = reminder(ReminderKind.SNOOZE, at(8, 55), id = "z").copy(
            categoryId = "work",
            note = "deep work",
            activityStartAt = at(9),
            activityEndAt = at(10),
            action = StartAction.NOW_DOING,
        )

        val found = occurrences(sources(reminders = listOf(snoozed)), now = at(8, 50)).single()

        assertEquals("snooze:z", found.key)
        assertEquals(ReminderSource.SNOOZE, found.source)
        assertEquals(at(8, 55), found.fireAt)
        assertEquals("work", found.categoryId)
        assertEquals("deep work", found.note)
        assertEquals(at(9), found.activityStartAt)
        assertEquals(at(10), found.activityEndAt)
        assertEquals(StartAction.NOW_DOING, found.action)
        assertEquals(Duration.ofMinutes(5), ReminderRules.SNOOZE)
    }

    // Schedule blocks

    @Test
    fun aScheduleBlockRemindsOnEveryDayItShows() {
        // Weekdays 09:00-17:00, reminding 10 minutes before.
        val approved = reminding(schedule(day, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.WEEKDAYS), id = "s"), lead = 10)

        val found = occurrences(sources(schedules = listOf(approved)))

        val weekdays = listOf(9, 10, 11, 12, 13, 16, 17).map { LocalDate.of(2026, 11, it) }
        assertEquals(weekdays.map { at(8, 50, it) }, found.map { it.fireAt })
        assertEquals(weekdays.map { "schedule:s-b0:$it" }, found.map { it.key })
        assertTrue(found.all { it.source == ReminderSource.PLANNED && it.categoryId == "work" && it.action == StartAction.NOW_DOING })
        assertEquals(at(9) to at(17), found.first().activityStartAt to found.first().activityEndAt)
    }

    @Test
    fun aDayWithItsOwnPlanDoesNotGetTheScheduleReminder() {
        val approved = reminding(schedule(day, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS), id = "s"), lead = 10)
        val tuesday = day.plusDays(1)
        val ownPlan = plan("family", at(12, date = tuesday), lead = null)

        val found = occurrences(sources(planned = listOf(ownPlan), schedules = listOf(approved)))

        assertFalse(found.any { it.fireAt == at(8, 50, tuesday) })
        assertEquals(at(8, 50), found.first().fireAt)
        assertEquals(at(8, 50, day.plusDays(2)), found[1].fireAt)
    }

    @Test
    fun aBlockReminderSetAfterTodaysMomentWaitsForTheNextDay() {
        val approved = reminding(schedule(day, null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS)), lead = 10, setAt = at(8, 55))

        val found = occurrences(sources(schedules = listOf(approved)), now = at(8, 55), after = at(8))

        assertEquals(at(8, 50, day.plusDays(1)), found.first().fireAt)
    }

    @Test
    fun anEndedScheduleStopsReminding() {
        val ended = reminding(schedule(day.minusDays(7), day.plusDays(1), Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS)), lead = 10)

        val found = occurrences(sources(schedules = listOf(ended)))

        assertEquals(listOf(at(8, 50), at(8, 50, day.plusDays(1))), found.map { it.fireAt })
    }

    // Bedtime

    @Test
    fun bedtimeRemindsEveryDayAtItsTimeAndStartsSleep() {
        val found = occurrences(sources(bedtimeMinute = minutes(22, 30)))

        assertEquals((0L..8L).map { at(22, 30, day.plusDays(it)) }, found.map { it.fireAt })
        assertEquals("bedtime:$day", found.first().key)
        assertTrue(found.all { it.source == ReminderSource.BEDTIME && it.action == StartAction.SLEEP && it.title == null })
    }

    @Test
    fun aBedtimeSetAfterTonightsTimeWaitsForTomorrow() {
        val found = occurrences(sources(bedtimeMinute = minutes(22, 30), bedtimeSetAt = at(23)), now = at(23, 10), after = at(20))

        assertEquals(at(22, 30, day.plusDays(1)), found.first().fireAt)
    }

    @Test
    fun noBedtimeReminderWhileItIsOff() {
        assertEquals(emptyList<ReminderOccurrence>(), occurrences(sources(bedtimeMinute = null, bedtimeSetAt = at(7))))
    }

    // Time-zone change

    @Test
    fun aPlannedEntryKeepsItsMomentWhenTheTimeZoneChanges() {
        val entry = plan("work", at(15), 10)

        val inLondon = occurrences(sources(planned = listOf(entry)), zone = london).single()
        val inTokyo = occurrences(sources(planned = listOf(entry)), zone = tokyo).single()

        assertEquals(at(14, 50), inLondon.fireAt)
        assertEquals(inLondon, inTokyo)
    }

    @Test
    fun scheduleBlocksAndBedtimeFollowTheWallClockOfTheNewZone() {
        val approved = reminding(schedule(day.minusDays(7), null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS)), lead = 10)
        val all = sources(schedules = listOf(approved), bedtimeMinute = minutes(22, 30))

        // 08:00 in London is 17:00 in Tokyo: today's 08:50 there has gone, tonight's bedtime has not.
        val found = occurrences(all, zone = tokyo)

        assertEquals(at(day.atTime(22, 30), tokyo), found[0].fireAt)
        assertEquals(ReminderSource.BEDTIME, found[0].source)
        assertEquals(at(day.plusDays(1).atTime(8, 50), tokyo), found[1].fireAt)
        assertEquals(at(day.plusDays(1).atTime(9, 0), tokyo), found[1].activityStartAt)
        // In London the same sources give London wall-clock times.
        assertEquals(listOf(at(8, 50), at(22, 30)), occurrences(all, zone = london).take(2).map { it.fireAt })
    }

    // Daylight saving time

    @Test
    fun aBlockKeepsItsWallClockTimeWhenTheClocksGoForward() {
        // Europe/London: 01:00 becomes 02:00 on 28 March 2027.
        val change = LocalDate.of(2027, 3, 28)
        val approved = reminding(schedule(change.minusDays(20), null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS), id = "s"), lead = 10)
        val now = at(change.minusDays(1).atTime(8, 0))

        val found = occurrences(sources(schedules = listOf(approved)), now = now).take(3)

        assertEquals(
            listOf(Instant.parse("2027-03-27T08:50:00Z"), Instant.parse("2027-03-28T07:50:00Z"), Instant.parse("2027-03-29T07:50:00Z")),
            found.map { it.fireAt },
        )
        // 08:50 on the clock each day: 23 hours apart across the change.
        assertEquals(Duration.ofHours(23), Duration.between(found[0].fireAt, found[1].fireAt))
        assertTrue(found.all { it.fireAt.atZone(london).toLocalTime().toString() == "08:50" })
    }

    @Test
    fun aTimeSkippedWhenTheClocksGoForwardMovesToTheShiftedTime() {
        val change = LocalDate.of(2027, 3, 28)
        val now = at(change.minusDays(1).atTime(12, 0))

        // 01:30 does not exist that night: the reminder comes at 02:30, once.
        val thatNight = occurrences(sources(bedtimeMinute = minutes(1, 30)), now = now).filter { it.key == "bedtime:$change" }

        assertEquals(listOf(Instant.parse("2027-03-28T01:30:00Z")), thatNight.map { it.fireAt })
        assertEquals("02:30", thatNight.single().fireAt.atZone(london).toLocalTime().toString())
    }

    @Test
    fun aTimeRepeatedWhenTheClocksGoBackHappensOnceTheFirstTime() {
        // Europe/London: 02:00 becomes 01:00 on 31 October 2027, so 01:30 comes twice.
        val change = LocalDate.of(2027, 10, 31)
        val now = at(change.minusDays(1).atTime(12, 0))

        val found = occurrences(sources(bedtimeMinute = minutes(1, 30)), now = now)

        val thatNight = found.filter { it.fireAt.atZone(london).toLocalDate() == change }
        assertEquals(listOf(Instant.parse("2027-10-31T00:30:00Z")), thatNight.map { it.fireAt })
        // The next day is 25 hours later on the clock's 01:30.
        assertEquals(Instant.parse("2027-11-01T01:30:00Z"), found.first { it.key == "bedtime:${change.plusDays(1)}" }.fireAt)
    }

    @Test
    fun aBlockKeepsItsWallClockTimeWhenTheClocksGoBack() {
        val change = LocalDate.of(2027, 10, 31)
        val approved = reminding(schedule(change.minusDays(20), null, Triple("work", minutes(9) to minutes(17), ScheduleRules.ALL_DAYS)), lead = 30)
        val now = at(change.minusDays(1).atTime(8, 0))

        val found = occurrences(sources(schedules = listOf(approved)), now = now).take(2)

        assertEquals(listOf(Instant.parse("2027-10-30T07:30:00Z"), Instant.parse("2027-10-31T08:30:00Z")), found.map { it.fireAt })
        assertEquals(Duration.ofHours(25), Duration.between(found[0].fireAt, found[1].fireAt))
    }

    // What is shown when a reminder comes due

    @Test
    fun aPlannedReminderSaysHowLongUntilTheStartRoundedUp() {
        val due = occurrences(sources(planned = listOf(plan("work", at(9), 10)))).single()

        assertEquals(10, ReminderPlanner.present(due, at(8, 50), nothingRunning)?.minutesUntilStart)
        // The phone was a little late: 4 minutes 10 seconds to go reads "in 5 minutes".
        assertEquals(5, ReminderPlanner.present(due, at(8, 55).plusSeconds(50), nothingRunning)?.minutesUntilStart)
        assertEquals(1, ReminderPlanner.present(due, at(8, 59).plusSeconds(30), nothingRunning)?.minutesUntilStart)
    }

    @Test
    fun aPlannedReminderThatComesAfterTheStartIsDropped() {
        val due = occurrences(sources(planned = listOf(plan("work", at(9), 10)))).single()

        assertNull(ReminderPlanner.present(due, at(9), nothingRunning))
        assertNull(ReminderPlanner.present(due, at(9, 20), nothingRunning))
    }

    @Test
    fun otherRemindersAreDroppedOnceHalfAnHourLate() {
        val oneOff = occurrences(sources(reminders = listOf(reminder(ReminderKind.ONE_OFF, at(11), "Call the bank")))).single()
        val bedtime = occurrences(sources(bedtimeMinute = minutes(22, 30))).first()

        assertNotNull(ReminderPlanner.present(oneOff, at(11), nothingRunning))
        assertNotNull(ReminderPlanner.present(oneOff, at(11, 29), nothingRunning))
        assertNull(ReminderPlanner.present(oneOff, at(11, 30), nothingRunning))
        assertNull(ReminderPlanner.present(oneOff, at(11, 29), nothingRunning)?.minutesUntilStart)
        assertNotNull(ReminderPlanner.present(bedtime, at(22, 59), nothingRunning))
        assertNull(ReminderPlanner.present(bedtime, at(23), nothingRunning))
    }

    @Test
    fun aSnoozedPlannedReminderIsStillShownAfterTheStart() {
        val snoozed = reminder(ReminderKind.SNOOZE, at(9, 3)).copy(categoryId = "work", activityStartAt = at(9), activityEndAt = at(10), action = StartAction.NOW_DOING)
        val due = occurrences(sources(reminders = listOf(snoozed)), now = at(8, 58)).single()

        val shown = ReminderPlanner.present(due, at(9, 3), nothingRunning)

        assertNotNull(shown)
        assertNull(shown?.minutesUntilStart)
        // Snoozed while there was still time: it says how long.
        assertEquals(2, ReminderPlanner.present(due.copy(fireAt = at(8, 58)), at(8, 58), nothingRunning)?.minutesUntilStart)
    }

    @Test
    fun aReminderIsNotShownWhenWhatItWouldStartIsAlreadyInProgress() {
        fun due(category: String) = occurrences(sources(planned = listOf(plan(category, at(9), 10)))).single()
        val quietOneOff = occurrences(sources(reminders = listOf(reminder(ReminderKind.ONE_OFF, at(8, 50), "Sit", StartAction.QUIET))), now = at(8, 40)).single()
        val plain = occurrences(sources(reminders = listOf(reminder(ReminderKind.ONE_OFF, at(8, 50), "Bins"))), now = at(8, 40)).single()
        val now = at(8, 50)

        assertNull(ReminderPlanner.present(due("exercise"), now, nothingRunning.copy(exerciseOpen = true)))
        assertNotNull(ReminderPlanner.present(due("exercise"), now, nothingRunning.copy(quietOpen = true)))
        assertNull(ReminderPlanner.present(quietOneOff, now, nothingRunning.copy(quietOpen = true)))
        assertNull(ReminderPlanner.present(due("sleep"), now, nothingRunning.copy(currentCategoryId = "sleep")))
        assertNull(ReminderPlanner.present(due("work"), now, nothingRunning.copy(currentCategoryId = "work")))
        assertNotNull(ReminderPlanner.present(due("work"), now, nothingRunning.copy(currentCategoryId = "family")))
        // A reminder that starts nothing is always shown.
        assertNotNull(ReminderPlanner.present(plain, now, RunningNow(exerciseOpen = true, quietOpen = true, currentCategoryId = "work")))
    }
}
