package com.mauforge.paintlife.core.backup

import com.mauforge.paintlife.core.backup.BackupFixtures.ms
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.mytime.TimeEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What an import accepts. Every rule the screens enforce one record at a time is checked for the
 * whole file, and the first thing that breaks one refuses the file.
 */
class BackupMapperTest {

    private val full = BackupFixtures.full

    private fun problemOf(file: BackupFile): BackupProblem = try {
        BackupMapper.toContents(file)
        fail("Expected the backup to be refused")
        throw AssertionError()
    } catch (error: BackupException) {
        error.problem
    }

    private fun entry(id: String, kind: String, start: String, end: String, category: String = "work") =
        TimeEntryDto(id, kind, category, ms(start), ms(end), "UTC", "UTC", null, 0, "UTC", 0, "UTC")

    @Test
    fun aValidFileBecomesRowsWithDerivedDatesAndFixedReferenceData() {
        val contents = BackupMapper.toContents(full)

        assertEquals(BackupSummary(priorities = 3, exerciseSessions = 3, quietSessions = 3, timeEntries = 3, schedules = 2), contents.summary)
        // A session counts on the local day it started, in its own zone: 04:50 UTC on the 4th is still 3 November in New York.
        val lateNight = contents.exerciseSessions.single { it.id == "e3" }
        assertEquals(LocalDate.of(2026, 11, 3), lateNight.startDate)
        assertEquals(ZoneId.of("America/New_York"), lateNight.zoneId)
        assertEquals(LocalDate.of(2026, 11, 9), contents.quietSessions.single { it.id == "q2" }.startDate)
        // Finished sessions come back with a closed timer.
        assertEquals(1_200_000L, lateNight.timer.activeDurationMillis)
        assertNull(lateNight.timer.segmentStartElapsedRealtime)
        // Cat names and benchmarks come from the app, not the file.
        assertEquals("aero" to 30 * 60_000L, contents.levels.first().let { it.catKey to it.benchmarkMillis })
        assertEquals(10, contents.levels.size)
        assertEquals(true, contents.distractionFlags["work"])
        assertEquals(false, contents.distractionFlags["phone_internet"])
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), contents.timeEntries.single { it.id == "t1" }.endAt)
        assertEquals(listOf("s1", "s2", "s2"), contents.scheduleBlocks.map { it.scheduleId })
    }

    @Test
    fun rowsBecomeTheSameFileAgain() {
        assertEquals(full, BackupMapper.toFile(BackupMapper.toContents(full), full.appVersion, full.databaseVersion))
    }

    @Test
    fun categoriesMissingFromTheFileKeepTheirDefaults() {
        val contents = BackupMapper.toContents(full.copy(categories = emptyList()))
        assertEquals(true, contents.distractionFlags["phone_internet"])
        assertEquals(true, contents.distractionFlags["entertainment"])
        assertEquals(false, contents.distractionFlags["work"])
    }

    @Test
    fun unknownPeriodsAreForgottenRatherThanRefused() {
        val contents = BackupMapper.toContents(full.copy(flags = full.flags.copy(insightsPeriod = "Decade", progressPeriod = "Forever")))
        assertNull(contents.flags.insightsPeriod)
        assertNull(contents.flags.progressPeriod)
    }

    @Test
    fun datesAndZonesMustExist() {
        assertEquals(BackupProblem.BadTime, problemOf(full.copy(exportedZoneId = "Mars/Olympus")))
        assertEquals(BackupProblem.BadTime, problemOf(full.copy(dailyRecords = listOf(full.dailyRecords[0].copy(date = "2026-02-30")))))
        assertEquals(BackupProblem.BadTime, problemOf(full.copy(timeEntries = listOf(full.timeEntries[0].copy(endZoneId = "")))))
        assertEquals(BackupProblem.BadTime, problemOf(full.copy(flags = full.flags.copy(firstLaunchDate = "yesterday"))))
    }

    @Test
    fun unknownStatusesTypesAndKindsAreRefused() {
        assertEquals(BackupProblem.UnknownValue, problemOf(full.copy(exerciseSessions = listOf(full.exerciseSessions[0].copy(status = "DONE")))))
        assertEquals(BackupProblem.UnknownValue, problemOf(full.copy(exerciseSessions = listOf(full.exerciseSessions[0].copy(type = "SWIM")))))
        assertEquals(BackupProblem.UnknownValue, problemOf(full.copy(timeEntries = listOf(full.timeEntries[0].copy(kind = "IMAGINED")))))
        assertEquals(BackupProblem.UnknownValue, problemOf(full.copy(schedules = listOf(full.schedules[0].copy(status = "MAYBE")))))
        assertEquals(BackupProblem.UnknownValue, problemOf(full.copy(personalBests = listOf(full.personalBests[0].copy(kind = "TALLEST")))))
    }

    @Test
    fun idsMustBeUnique() {
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(priorities = full.priorities + full.priorities[1].copy(date = "2026-11-20", position = 0))))
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(dailyRecords = full.dailyRecords + full.dailyRecords[0].copy(id = "other"))))
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(exerciseSessions = full.exerciseSessions + full.exerciseSessions[0])))
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(quietSessions = full.quietSessions + full.quietSessions[0])))
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(timeEntries = full.timeEntries + full.timeEntries[2])))
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(schedules = full.schedules + full.schedules[0].copy(blocks = emptyList()))))
        // The same block id in two schedules.
        assertEquals(BackupProblem.DuplicateId, problemOf(full.copy(schedules = listOf(full.schedules[0], full.schedules[1].copy(blocks = full.schedules[0].blocks)))))
    }

    @Test
    fun referencesMustResolve() {
        assertEquals(BackupProblem.BrokenLink, problemOf(full.copy(personalBests = listOf(full.personalBests[0].copy(sessionId = "gone")))))
        assertEquals(BackupProblem.BrokenLink, problemOf(full.copy(personalBests = listOf(full.personalBests[0].copy(challengeLevel = 11)))))
        assertEquals(BackupProblem.Categories, problemOf(full.copy(timeEntries = listOf(full.timeEntries[0].copy(categoryId = "gardening")))))
        assertEquals(BackupProblem.Categories, problemOf(full.copy(categories = full.categories + CategoryDto("gardening", false))))
        assertEquals(BackupProblem.Categories, problemOf(full.copy(schedules = listOf(full.schedules[0].copy(blocks = listOf(full.schedules[0].blocks[0].copy(categoryId = "gardening")))))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(full.exerciseSessions[1].copy(challengeLevel = 12)))))
    }

    @Test
    fun aDayNeverHasMoreThanThreePrioritiesInPlacesZeroOneTwo() {
        val day = full.priorities[0]
        fun on(id: String, position: Int) = day.copy(id = id, position = position, isCompleted = false, completedAt = null, completedZoneId = null)
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0), on("b", 1), on("c", 2), on("d", 3)))))
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0), on("b", 2)))))
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0), on("b", 0)))))
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0).copy(title = "   ")))))
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0).copy(title = "x".repeat(81))))))
        // The same earlier priority brought to one day twice.
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0).copy(carriedFromId = "z"), on("b", 1).copy(carriedFromId = "z")))))
        assertEquals(BackupProblem.Priorities, problemOf(full.copy(priorities = listOf(on("a", 0).copy(completedAt = 5L)))))
        // Three on a day is fine.
        BackupMapper.toContents(full.copy(priorities = listOf(on("a", 2), on("b", 0), on("c", 1))))
    }

    @Test
    fun theCatLeagueMustBeTheTenLevelsUnlockedInOrder() {
        val levels = full.challengeLevels
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.dropLast(1))))
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels + ChallengeLevelDto(11, false))))
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.dropLast(1) + ChallengeLevelDto(9, false))))
        // The first cat locked.
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.map { if (it.level == 1) it.copy(isUnlocked = false, beatenAt = null, beatenZoneId = null) else it })))
        // Level 5 open while level 3 and 4 are locked: an unlock that skips cats.
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.map { if (it.level == 5) it.copy(isUnlocked = true) else it })))
        // Beaten but locked.
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.map { if (it.level == 3) it.copy(beatenAt = 1L, beatenZoneId = "UTC") else it })))
        // A moment without its zone.
        assertEquals(BackupProblem.CatLeague, problemOf(full.copy(challengeLevels = levels.map { if (it.level == 2) it.copy(unlockedZoneId = null) else it })))
    }

    @Test
    fun onlyFinishedSessionsWithPossibleValuesAreAccepted() {
        val volume = full.exerciseSessions[0]
        val timed = full.exerciseSessions[1]
        val quiet = full.quietSessions[0]
        listOf(SessionStatus.RUNNING, SessionStatus.PAUSED).forEach { open ->
            assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(status = open.name)))))
            assertEquals(BackupProblem.Sessions, problemOf(full.copy(quietSessions = listOf(quiet.copy(status = open.name)))))
        }
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(repetitions = -1)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(repetitions = 10_001)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(timed.copy(repetitions = 1_001)), personalBests = emptyList())))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(activeDurationMillis = -5)))))
        // A step-up session has no opponent; a timed challenge needs one.
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(challengeLevel = 1)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(timed.copy(challengeLevel = null)), personalBests = emptyList())))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(exerciseSessions = listOf(volume.copy(endedZoneId = null)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(quietSessions = listOf(quiet.copy(targetDurationMillis = 0)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(quietSessions = listOf(quiet.copy(activeDurationMillis = -1)))))
        assertEquals(BackupProblem.Sessions, problemOf(full.copy(personalBests = listOf(full.personalBests[0].copy(value = -1)))))
    }

    @Test
    fun timeEntriesAreWholeMinutesFromOneMinuteToTwentyFourHours() {
        fun with(entry: TimeEntryDto) = full.copy(timeEntries = listOf(entry))
        assertEquals(BackupProblem.TimeEntries, problemOf(with(entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-08T08:00:00Z"))))
        assertEquals(BackupProblem.TimeEntries, problemOf(with(entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-08T07:00:00Z"))))
        assertEquals(BackupProblem.TimeEntries, problemOf(with(entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-08T08:00:30Z"))))
        assertEquals(BackupProblem.TimeEntries, problemOf(with(entry("a", "ACTUAL", "2026-11-08T08:00:10Z", "2026-11-08T09:00:00Z"))))
        assertEquals(BackupProblem.TimeEntries, problemOf(with(entry("a", "PLANNED", "2026-11-08T08:00:00Z", "2026-11-09T08:01:00Z"))))
        // Exactly one minute, and exactly 24 hours, are both fine.
        BackupMapper.toContents(with(entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-08T08:01:00Z")))
        BackupMapper.toContents(with(entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-09T08:00:00Z")))
    }

    @Test
    fun entriesOfOneLayerNeverOverlapButTheLayersAreIndependent() {
        val morning = entry("a", "ACTUAL", "2026-11-08T08:00:00Z", "2026-11-08T10:00:00Z")
        val clash = entry("b", "ACTUAL", "2026-11-08T09:59:00Z", "2026-11-08T11:00:00Z", "family")
        assertEquals(BackupProblem.Overlap, problemOf(full.copy(timeEntries = listOf(clash, morning))))
        assertEquals(BackupProblem.Overlap, problemOf(full.copy(timeEntries = listOf(morning.copy(kind = "PLANNED"), clash.copy(kind = "PLANNED")))))
        // Touching is not overlapping, and a plan may sit over actual time.
        val touching = entry("c", "ACTUAL", "2026-11-08T10:00:00Z", "2026-11-08T11:00:00Z")
        val contents = BackupMapper.toContents(full.copy(timeEntries = listOf(morning, touching, clash.copy(kind = "PLANNED"))))
        assertEquals(listOf(TimeEntryKind.ACTUAL, TimeEntryKind.ACTUAL, TimeEntryKind.PLANNED), contents.timeEntries.map { it.kind })
    }

    @Test
    fun scheduleBlocksStayInsideTheDayHaveDaysAndNeverOverlap() {
        val schedule = full.schedules[1]
        fun with(vararg blocks: ScheduleBlockDto) = full.copy(schedules = listOf(schedule.copy(blocks = blocks.toList())))
        val work = schedule.blocks[0]
        assertEquals(BackupProblem.Schedules, problemOf(with(work.copy(startMinuteOfDay = -1))))
        assertEquals(BackupProblem.Schedules, problemOf(with(work.copy(endMinuteOfDay = 1440))))
        assertEquals(BackupProblem.Schedules, problemOf(with(work.copy(daysOfWeekMask = 0))))
        assertEquals(BackupProblem.Schedules, problemOf(with(work.copy(daysOfWeekMask = 128))))
        // 16:00 to 18:00 on Mondays overlaps work, which ends at 17:00 on weekdays.
        assertEquals(BackupProblem.Schedules, problemOf(with(work, ScheduleBlockDto("x", "family", 960, 1080, 0x01))))
        // Sunday night's sleep runs into Monday morning, where an early block would clash with it.
        assertEquals(BackupProblem.Schedules, problemOf(with(schedule.blocks[1], ScheduleBlockDto("x", "exercise", 360, 400, 0x01))))
        assertEquals(BackupProblem.Schedules, problemOf(full.copy(schedules = listOf(schedule.copy(approvedZoneId = null)))))
        // The same hours on the weekend do not overlap a weekday block.
        BackupMapper.toContents(with(work, ScheduleBlockDto("x", "family", 540, 1020, 0x60)))
    }
}
