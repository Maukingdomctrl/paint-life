package com.mauforge.paintlife.core.backup

import com.mauforge.paintlife.core.database.SeedData
import java.time.Instant

/**
 * A valid backup with a row in every table, in the order an export writes them, so "import, then
 * export" must give exactly this file back. It is also consistent with itself (the win at level 1
 * is the one recorded on the level and in the best time), as every real export is.
 */
object BackupFixtures {

    fun ms(text: String): Long = Instant.parse(text).toEpochMilli()

    private const val KOLKATA = "Asia/Kolkata"
    private const val NEW_YORK = "America/New_York"
    private const val LONDON = "Europe/London"
    private const val KATHMANDU_OFFSET = "+05:45"

    private val win = ms("2026-11-03T02:29:00Z")

    val levels: List<ChallengeLevelDto> = SeedData.levels.map { seed ->
        when (seed.level) {
            1 -> ChallengeLevelDto(1, isUnlocked = true, beatenAt = win, beatenZoneId = KOLKATA)
            2 -> ChallengeLevelDto(2, isUnlocked = true, unlockedAt = win, unlockedZoneId = KOLKATA)
            else -> ChallengeLevelDto(seed.level, isUnlocked = false)
        }
    }

    /** Every category, with two flags changed from their defaults. */
    val categories: List<CategoryDto> = SeedData.categories.map {
        when (it.id) {
            "work" -> CategoryDto("work", isDistraction = true)
            "phone_internet" -> CategoryDto("phone_internet", isDistraction = false)
            else -> CategoryDto(it.id, it.isDistraction)
        }
    }

    val full = BackupFile(
        appVersion = "1.0.0",
        databaseVersion = BackupRepository.DATABASE_VERSION,
        exportedAt = ms("2026-11-10T08:30:00Z"),
        exportedZoneId = KOLKATA,
        flags = FlagsDto(
            firstLaunchDate = "2026-10-01",
            exerciseSafetyRead = true,
            victorySeenUpTo = win,
            observationComplete = true,
            insightsPeriod = "FourWeeks",
            progressPeriod = "ThisMonth",
        ),
        dailyRecords = listOf(
            DailyRecordDto("r1", "2026-11-08", "a note", true, ms("2026-11-08T03:00:00Z"), KOLKATA, ms("2026-11-08T04:00:00Z"), KOLKATA),
            DailyRecordDto("r2", "2026-11-09", null, false, ms("2026-11-09T03:00:00Z"), LONDON, ms("2026-11-09T03:00:00Z"), LONDON),
        ),
        priorities = listOf(
            PriorityDto("p1", "2026-11-08", 0, "Call the dentist", true, ms("2026-11-08T09:00:00Z"), KOLKATA, null, ms("2026-11-08T03:00:00Z"), KOLKATA, ms("2026-11-08T09:00:00Z"), KOLKATA),
            PriorityDto("p2", "2026-11-08", 1, "Water the plants", false, null, null, null, ms("2026-11-08T03:01:00Z"), KOLKATA, ms("2026-11-08T03:01:00Z"), KOLKATA),
            PriorityDto("p3", "2026-11-09", 0, "Water the plants", false, null, null, "p2", ms("2026-11-09T03:00:00Z"), LONDON, ms("2026-11-09T03:00:00Z"), LONDON),
        ),
        challengeLevels = levels,
        exerciseSessions = listOf(
            ExerciseSessionDto("e1", "VOLUME", "COMPLETED", null, ms("2026-11-01T01:00:00Z"), KOLKATA, ms("2026-11-01T01:20:00Z"), KOLKATA, 120, 1_100_000, "felt good", ms("2026-11-01T01:20:00Z"), KOLKATA),
            // 29:00 against Ana's 30:00: the win recorded on level 1.
            ExerciseSessionDto("e2", "TIMED_CHALLENGE", "COMPLETED", 1, ms("2026-11-03T02:00:00Z"), KOLKATA, win, KOLKATA, 1000, 29 * 60_000L, null, win, KOLKATA),
            // A stopped attempt, started at 23:50 on 3 November in New York (already 4 November in UTC).
            ExerciseSessionDto("e3", "TIMED_CHALLENGE", "CANCELLED", 2, ms("2026-11-04T04:50:00Z"), NEW_YORK, ms("2026-11-04T05:10:00Z"), NEW_YORK, 300, 1_200_000, null, ms("2026-11-04T05:10:00Z"), NEW_YORK),
        ),
        personalBests = listOf(
            PersonalBestDto("timed_level_1", "TIMED_CHALLENGE_TIME", 1, 29 * 60_000L, "e2", win, KOLKATA),
        ),
        quietSessions = listOf(
            QuietSessionDto("q1", "COMPLETED", ms("2026-11-08T01:00:00Z"), KOLKATA, ms("2026-11-08T01:05:00Z"), KOLKATA, 300_000, 300_000, ms("2026-11-08T01:05:00Z"), KOLKATA),
            QuietSessionDto("q2", "ENDED_EARLY", ms("2026-11-08T18:25:00Z"), KATHMANDU_OFFSET, ms("2026-11-08T18:27:30Z"), KATHMANDU_OFFSET, 300_000, 120_000, ms("2026-11-08T18:27:30Z"), KATHMANDU_OFFSET),
            QuietSessionDto("q3", "INCOMPLETE", ms("2026-11-09T01:00:00Z"), LONDON, ms("2026-11-09T01:10:00Z"), LONDON, 300_000, 40_000, ms("2026-11-09T01:10:00Z"), LONDON),
        ),
        categories = categories,
        timeEntries = listOf(
            // Sleep across the New York fall-back night (01:30 happens twice): 9 real hours.
            TimeEntryDto("t1", "ACTUAL", "sleep", ms("2026-11-01T03:00:00Z"), ms("2026-11-01T12:00:00Z"), NEW_YORK, NEW_YORK, null, ms("2026-11-01T12:05:00Z"), NEW_YORK, ms("2026-11-01T12:05:00Z"), NEW_YORK),
            // Started in London, ended in Kolkata.
            TimeEntryDto("t2", "ACTUAL", "work", ms("2026-11-08T08:00:00Z"), ms("2026-11-08T12:30:00Z"), LONDON, KOLKATA, "deep work", ms("2026-11-08T12:31:00Z"), KOLKATA, ms("2026-11-08T12:40:00Z"), KOLKATA),
            // Planned at the same time as t2: the layers are independent.
            TimeEntryDto("t3", "PLANNED", "learning", ms("2026-11-08T09:00:00Z"), ms("2026-11-08T10:00:00Z"), KOLKATA, KOLKATA, null, ms("2026-11-07T10:00:00Z"), KOLKATA, ms("2026-11-07T10:00:00Z"), KOLKATA),
        ),
        schedules = listOf(
            ScheduleDto(
                "s1", "First try", "ARCHIVED", ms("2026-10-20T05:00:00Z"), KOLKATA, ms("2026-10-20T05:00:00Z"), KOLKATA, ms("2026-11-05T05:00:00Z"), KOLKATA,
                "2026-10-20", "2026-11-04", "2026-10-01", "2026-10-19",
                listOf(ScheduleBlockDto("b1", "work", 540, 1020, 0x1F)),
            ),
            ScheduleDto(
                "s2", "My schedule", "APPROVED", ms("2026-11-05T05:00:00Z"), KOLKATA, ms("2026-11-05T05:00:00Z"), KOLKATA, ms("2026-11-05T05:00:00Z"), KOLKATA,
                "2026-11-05", null, "2026-10-08", "2026-11-04",
                listOf(
                    ScheduleBlockDto("b2", "work", 540, 1020, 0x1F),
                    // Sleep from 23:00 to 07:00: crosses midnight, every day.
                    ScheduleBlockDto("b3", "sleep", 1380, 420, 0x7F),
                ),
            ),
        ),
    )
}
