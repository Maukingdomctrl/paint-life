package com.mauforge.paintlife.core.backup

import com.mauforge.paintlife.core.database.SeedData
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.datastore.BackupFlags
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.exercise.ChallengeLevelEntity
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.ExerciseSessionType
import com.mauforge.paintlife.exercise.ExerciseSessionUiState
import com.mauforge.paintlife.exercise.PersonalBestEntity
import com.mauforge.paintlife.exercise.PersonalBestKind
import com.mauforge.paintlife.exercise.SessionCounter
import com.mauforge.paintlife.mytime.DailyScheduleEntity
import com.mauforge.paintlife.mytime.InsightsPeriod
import com.mauforge.paintlife.mytime.ReminderEntity
import com.mauforge.paintlife.mytime.ReminderKind
import com.mauforge.paintlife.mytime.ReminderRules
import com.mauforge.paintlife.mytime.StartAction
import com.mauforge.paintlife.mytime.ScheduleBlockEntity
import com.mauforge.paintlife.mytime.ScheduleRules
import com.mauforge.paintlife.mytime.ScheduleStatus
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRules
import com.mauforge.paintlife.progress.ProgressPeriod
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.today.DailyRecordEntity
import com.mauforge.paintlife.today.PriorityEntity
import com.mauforge.paintlife.today.PriorityTitle
import com.mauforge.paintlife.today.TodayRepository
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** How much a backup (or the phone) holds, for the import confirmation. */
data class BackupSummary(
    val priorities: Int,
    val exerciseSessions: Int,
    val quietSessions: Int,
    val timeEntries: Int,
    val schedules: Int,
) {
    val isEmpty: Boolean get() = priorities + exerciseSessions + quietSessions + timeEntries + schedules == 0
}

/** Every record of a backup as database rows: checked, and ready to replace what is stored. */
data class BackupContents(
    val exportedAt: Instant,
    val exportedZone: ZoneId,
    val flags: BackupFlags,
    val dailyRecords: List<DailyRecordEntity>,
    val priorities: List<PriorityEntity>,
    /** All ten levels. */
    val levels: List<ChallengeLevelEntity>,
    val exerciseSessions: List<ExerciseSessionEntity>,
    val personalBests: List<PersonalBestEntity>,
    val quietSessions: List<QuietSessionEntity>,
    /** Every fixed category's distraction flag. */
    val distractionFlags: Map<String, Boolean>,
    val timeEntries: List<TimeEntryEntity>,
    val schedules: List<DailyScheduleEntity>,
    val scheduleBlocks: List<ScheduleBlockEntity>,
    /** One-off reminders. */
    val reminders: List<ReminderEntity> = emptyList(),
) {
    val summary: BackupSummary
        get() = BackupSummary(priorities.size, exerciseSessions.size, quietSessions.size, timeEntries.size, schedules.size)
}

/**
 * Between database rows and the backup file. Reading a file checks everything the app relies
 * on (the same rules the screens enforce when records are made one at a time), so an import can
 * never leave the database in a state the app could not have produced itself. Pure: no clock,
 * no database.
 */
object BackupMapper {

    private val levelSeeds = SeedData.levels.associateBy { it.level }
    private val categorySeeds = SeedData.categories.associateBy { it.id }

    // Rows to file

    fun toFile(contents: BackupContents, appVersion: String, databaseVersion: Int): BackupFile {
        val exportedIds = contents.exerciseSessions.mapTo(HashSet()) { it.id }
        val blocksBySchedule = contents.scheduleBlocks.groupBy { it.scheduleId }
        return BackupFile(
            appVersion = appVersion,
            databaseVersion = databaseVersion,
            exportedAt = contents.exportedAt.toEpochMilli(),
            exportedZoneId = contents.exportedZone.id,
            flags = contents.flags.let {
                FlagsDto(
                    firstLaunchDate = it.firstLaunchDate?.toString(),
                    exerciseSafetyRead = it.exerciseSafetyRead,
                    victorySeenUpTo = it.victorySeenUpTo.toEpochMilli(),
                    observationComplete = it.observationComplete,
                    insightsPeriod = it.insightsPeriod,
                    progressPeriod = it.progressPeriod,
                    bedtimeReminderMinute = it.bedtimeReminderMinute,
                )
            },
            dailyRecords = contents.dailyRecords.map {
                DailyRecordDto(it.id, it.date.toString(), it.note, it.carryOverDismissed, it.createdAt.toEpochMilli(), it.createdZoneId.id, it.updatedAt.toEpochMilli(), it.updatedZoneId.id)
            },
            priorities = contents.priorities.map {
                PriorityDto(
                    id = it.id,
                    date = it.date.toString(),
                    position = it.position,
                    title = it.title,
                    isCompleted = it.isCompleted,
                    completedAt = it.completedAt?.toEpochMilli(),
                    completedZoneId = it.completedZoneId?.id,
                    carriedFromId = it.carriedFromId,
                    createdAt = it.createdAt.toEpochMilli(),
                    createdZoneId = it.createdZoneId.id,
                    updatedAt = it.updatedAt.toEpochMilli(),
                    updatedZoneId = it.updatedZoneId.id,
                )
            },
            challengeLevels = contents.levels.map {
                ChallengeLevelDto(it.level, it.isUnlocked, it.unlockedAt?.toEpochMilli(), it.unlockedZoneId?.id, it.beatenAt?.toEpochMilli(), it.beatenZoneId?.id)
            },
            exerciseSessions = contents.exerciseSessions.map {
                ExerciseSessionDto(
                    id = it.id,
                    type = it.type.name,
                    status = it.status.name,
                    challengeLevel = it.challengeLevel,
                    startedAt = it.startedAt.toEpochMilli(),
                    zoneId = it.zoneId.id,
                    endedAt = it.endedAt?.toEpochMilli(),
                    endedZoneId = it.endedZoneId?.id,
                    repetitions = it.repetitions,
                    activeDurationMillis = it.timer.activeDurationMillis,
                    notes = it.notes,
                    updatedAt = it.updatedAt.toEpochMilli(),
                    updatedZoneId = it.updatedZoneId.id,
                )
            },
            personalBests = contents.personalBests.map {
                // A best never points at a session that is not in the file (an open one).
                PersonalBestDto(it.key, it.kind.name, it.challengeLevel, it.value, it.sessionId?.takeIf(exportedIds::contains), it.achievedAt.toEpochMilli(), it.zoneId.id)
            },
            quietSessions = contents.quietSessions.map {
                QuietSessionDto(
                    id = it.id,
                    status = it.status.name,
                    startedAt = it.startedAt.toEpochMilli(),
                    zoneId = it.zoneId.id,
                    endedAt = it.endedAt?.toEpochMilli(),
                    endedZoneId = it.endedZoneId?.id,
                    targetDurationMillis = it.targetDurationMillis,
                    activeDurationMillis = it.timer.activeDurationMillis,
                    updatedAt = it.updatedAt.toEpochMilli(),
                    updatedZoneId = it.updatedZoneId.id,
                )
            },
            categories = contents.distractionFlags.entries.sortedBy { categoryOrder(it.key) }.map { CategoryDto(it.key, it.value) },
            timeEntries = contents.timeEntries.map {
                TimeEntryDto(
                    id = it.id,
                    kind = it.kind.name,
                    categoryId = it.categoryId,
                    startAt = it.startAt.toEpochMilli(),
                    endAt = it.endAt.toEpochMilli(),
                    zoneId = it.zoneId.id,
                    endZoneId = it.endZoneId.id,
                    note = it.note,
                    createdAt = it.createdAt.toEpochMilli(),
                    createdZoneId = it.createdZoneId.id,
                    updatedAt = it.updatedAt.toEpochMilli(),
                    updatedZoneId = it.updatedZoneId.id,
                    reminderMinutes = it.reminderMinutes,
                )
            },
            reminders = contents.reminders.filter { it.kind == ReminderKind.ONE_OFF }.map {
                ReminderDto(it.id, it.title.orEmpty(), it.fireAt.toEpochMilli(), it.zoneId.id, it.action.name, it.createdAt.toEpochMilli(), it.createdZoneId.id)
            },
            schedules = contents.schedules.map { s ->
                ScheduleDto(
                    id = s.id,
                    name = s.name,
                    status = s.status.name,
                    createdAt = s.createdAt.toEpochMilli(),
                    createdZoneId = s.createdZoneId.id,
                    approvedAt = s.approvedAt?.toEpochMilli(),
                    approvedZoneId = s.approvedZoneId?.id,
                    updatedAt = s.updatedAt.toEpochMilli(),
                    updatedZoneId = s.updatedZoneId.id,
                    activeFrom = s.activeFrom?.toString(),
                    activeUntil = s.activeUntil?.toString(),
                    basedOnFrom = s.basedOnFrom?.toString(),
                    basedOnTo = s.basedOnTo?.toString(),
                    blocks = blocksBySchedule[s.id].orEmpty().map {
                        ScheduleBlockDto(it.id, it.categoryId, it.startMinuteOfDay, it.endMinuteOfDay, it.daysOfWeekMask, it.reminderMinutes)
                    },
                )
            },
        )
    }

    private fun categoryOrder(id: String): Int = SeedData.categories.indexOfFirst { it.id == id }

    // File to rows

    /** The file's records as rows; throws [BackupException] at the first thing that is not valid. */
    fun toContents(file: BackupFile): BackupContents {
        val sessions = exerciseSessions(file.exerciseSessions)
        val schedules = file.schedules.map(::schedule)
        return BackupContents(
            exportedAt = instant(file.exportedAt),
            exportedZone = zone(file.exportedZoneId),
            flags = flags(file.flags),
            dailyRecords = dailyRecords(file.dailyRecords),
            priorities = priorities(file.priorities),
            levels = levels(file.challengeLevels),
            exerciseSessions = sessions,
            personalBests = personalBests(file.personalBests, sessions.mapTo(HashSet()) { it.id }),
            quietSessions = quietSessions(file.quietSessions),
            distractionFlags = distractionFlags(file.categories),
            timeEntries = timeEntries(file.timeEntries),
            schedules = schedules.also { unique(it.map { s -> s.id }, "schedule") },
            scheduleBlocks = scheduleBlocks(file.schedules),
            reminders = reminders(file.reminders),
        )
    }

    private fun flags(dto: FlagsDto) = BackupFlags(
        firstLaunchDate = dto.firstLaunchDate?.let(::date),
        exerciseSafetyRead = dto.exerciseSafetyRead,
        victorySeenUpTo = instant(dto.victorySeenUpTo.coerceAtLeast(0L)),
        observationComplete = dto.observationComplete,
        // A period this app does not know is simply not remembered.
        insightsPeriod = dto.insightsPeriod?.takeIf { name -> InsightsPeriod.entries.any { it.name == name } },
        progressPeriod = dto.progressPeriod?.takeIf { name -> ProgressPeriod.entries.any { it.name == name } },
        bedtimeReminderMinute = dto.bedtimeReminderMinute?.also {
            if (it !in 0 until TimeRules.MINUTES_PER_DAY) fail(BackupProblem.Reminders, "bedtime $it")
        },
    )

    private fun dailyRecords(rows: List<DailyRecordDto>): List<DailyRecordEntity> {
        unique(rows.map { it.id }, "daily record")
        unique(rows.map { it.date }, "daily record date")
        return rows.map {
            DailyRecordEntity(it.id, date(it.date), it.note, it.carryOverDismissed, instant(it.createdAt), zone(it.createdZoneId), instant(it.updatedAt), zone(it.updatedZoneId))
        }
    }

    private fun priorities(rows: List<PriorityDto>): List<PriorityEntity> {
        unique(rows.map { it.id }, "priority")
        val entities = rows.map {
            if (it.title.isBlank() || it.title.length > PriorityTitle.MAX_LENGTH) fail(BackupProblem.Priorities, "title of ${it.id}")
            if ((it.completedAt == null) != (it.completedZoneId == null)) fail(BackupProblem.Priorities, "completion of ${it.id}")
            PriorityEntity(
                id = it.id,
                date = date(it.date),
                position = it.position,
                title = it.title,
                isCompleted = it.isCompleted,
                completedAt = it.completedAt?.let(::instant),
                completedZoneId = it.completedZoneId?.let(::zone),
                carriedFromId = it.carriedFromId,
                createdAt = instant(it.createdAt),
                createdZoneId = zone(it.createdZoneId),
                updatedAt = instant(it.updatedAt),
                updatedZoneId = zone(it.updatedZoneId),
            )
        }
        entities.groupBy { it.date }.forEach { (day, onDay) ->
            // At most three a day, in places 0, 1, 2, and nothing brought to a day twice.
            if (onDay.size > TodayRepository.MAX_PRIORITIES_PER_DAY) fail(BackupProblem.Priorities, "more than three on $day")
            if (onDay.map { it.position }.sorted() != onDay.indices.toList()) fail(BackupProblem.Priorities, "positions on $day")
            val carried = onDay.mapNotNull { it.carriedFromId }
            if (carried.size != carried.toSet().size) fail(BackupProblem.Priorities, "carried twice to $day")
        }
        return entities
    }

    private fun levels(rows: List<ChallengeLevelDto>): List<ChallengeLevelEntity> {
        if (rows.size != levelSeeds.size || rows.map { it.level }.toSet() != levelSeeds.keys) fail(BackupProblem.CatLeague, "levels")
        val ordered = rows.sortedBy { it.level }
        ordered.forEachIndexed { index, row ->
            if ((row.unlockedAt == null) != (row.unlockedZoneId == null) || (row.beatenAt == null) != (row.beatenZoneId == null)) {
                fail(BackupProblem.CatLeague, "level ${row.level}")
            }
            // The first cat is always open; every other one opens only after the one before it.
            val reachable = if (index == 0) row.isUnlocked else !row.isUnlocked || ordered[index - 1].isUnlocked
            if (!reachable || (row.beatenAt != null && !row.isUnlocked)) fail(BackupProblem.CatLeague, "level ${row.level}")
        }
        return ordered.map { row ->
            val seed = levelSeeds.getValue(row.level)
            ChallengeLevelEntity(
                level = row.level,
                catKey = seed.catKey,
                benchmarkMillis = TimeUnit.MINUTES.toMillis(seed.benchmarkMinutes),
                isUnlocked = row.isUnlocked,
                unlockedAt = row.unlockedAt?.let(::instant),
                unlockedZoneId = row.unlockedZoneId?.let(::zone),
                beatenAt = row.beatenAt?.let(::instant),
                beatenZoneId = row.beatenZoneId?.let(::zone),
            )
        }
    }

    private fun exerciseSessions(rows: List<ExerciseSessionDto>): List<ExerciseSessionEntity> {
        unique(rows.map { it.id }, "exercise session")
        return rows.map {
            val type = enumOf<ExerciseSessionType>(it.type)
            val status = finishedStatus(it.status, it.id)
            val timed = type == ExerciseSessionType.TIMED_CHALLENGE
            val levelFits = if (timed) it.challengeLevel in levelSeeds.keys else it.challengeLevel == null
            val maxReps = if (timed) ExerciseSessionUiState.TIMED_TARGET_REPS else SessionCounter.MAX_REPS
            if (!levelFits || it.repetitions !in 0..maxReps || it.activeDurationMillis < 0 || (it.endedAt == null) != (it.endedZoneId == null)) {
                fail(BackupProblem.Sessions, "exercise session ${it.id}")
            }
            val startedAt = instant(it.startedAt)
            val zone = zone(it.zoneId)
            ExerciseSessionEntity(
                id = it.id,
                type = type,
                status = status,
                challengeLevel = it.challengeLevel,
                startedAt = startedAt,
                zoneId = zone,
                startDate = startedAt.atZone(zone).toLocalDate(),
                endedAt = it.endedAt?.let(::instant),
                endedZoneId = it.endedZoneId?.let(::zone),
                repetitions = it.repetitions,
                timer = closedTimer(it.activeDurationMillis),
                notes = it.notes,
                updatedAt = instant(it.updatedAt),
                updatedZoneId = zone(it.updatedZoneId),
            )
        }
    }

    private fun personalBests(rows: List<PersonalBestDto>, sessionIds: Set<String>): List<PersonalBestEntity> {
        unique(rows.map { it.key }, "personal best")
        return rows.map {
            if (it.challengeLevel != null && it.challengeLevel !in levelSeeds.keys) fail(BackupProblem.BrokenLink, "level of best ${it.key}")
            if (it.sessionId != null && it.sessionId !in sessionIds) fail(BackupProblem.BrokenLink, "session of best ${it.key}")
            if (it.value < 0) fail(BackupProblem.Sessions, "best ${it.key}")
            PersonalBestEntity(it.key, enumOf<PersonalBestKind>(it.kind), it.challengeLevel, it.value, it.sessionId, instant(it.achievedAt), zone(it.zoneId))
        }
    }

    private fun quietSessions(rows: List<QuietSessionDto>): List<QuietSessionEntity> {
        unique(rows.map { it.id }, "quiet session")
        return rows.map {
            val status = finishedStatus(it.status, it.id)
            if (it.targetDurationMillis <= 0 || it.activeDurationMillis < 0 || (it.endedAt == null) != (it.endedZoneId == null)) {
                fail(BackupProblem.Sessions, "quiet session ${it.id}")
            }
            val startedAt = instant(it.startedAt)
            val zone = zone(it.zoneId)
            QuietSessionEntity(
                id = it.id,
                status = status,
                startedAt = startedAt,
                zoneId = zone,
                startDate = startedAt.atZone(zone).toLocalDate(),
                endedAt = it.endedAt?.let(::instant),
                endedZoneId = it.endedZoneId?.let(::zone),
                targetDurationMillis = it.targetDurationMillis,
                timer = closedTimer(it.activeDurationMillis),
                updatedAt = instant(it.updatedAt),
                updatedZoneId = zone(it.updatedZoneId),
            )
        }
    }

    /** Every fixed category's flag: the file's where it has one, else the category's default. */
    private fun distractionFlags(rows: List<CategoryDto>): Map<String, Boolean> {
        unique(rows.map { it.id }, "category")
        rows.firstOrNull { it.id !in categorySeeds.keys }?.let { fail(BackupProblem.Categories, it.id) }
        val fromFile = rows.associate { it.id to it.isDistraction }
        return SeedData.categories.associate { it.id to (fromFile[it.id] ?: it.isDistraction) }
    }

    private fun timeEntries(rows: List<TimeEntryDto>): List<TimeEntryEntity> {
        unique(rows.map { it.id }, "time entry")
        val entities = rows.map {
            if (it.categoryId !in categorySeeds.keys) fail(BackupProblem.Categories, "${it.categoryId} on entry ${it.id}")
            val start = instant(it.startAt)
            val end = instant(it.endAt)
            if (TimeRules.toMinute(start) != start || TimeRules.toMinute(end) != end || !TimeRules.isValidSpan(start, end)) {
                fail(BackupProblem.TimeEntries, "entry ${it.id}")
            }
            val kind = enumOf<TimeEntryKind>(it.kind)
            // Only a plan can remind, and only at the lead times the app offers.
            if (it.reminderMinutes != null && (kind != TimeEntryKind.PLANNED || it.reminderMinutes !in ReminderRules.LEADS)) {
                fail(BackupProblem.Reminders, "reminder on entry ${it.id}")
            }
            TimeEntryEntity(
                id = it.id,
                kind = kind,
                categoryId = it.categoryId,
                startAt = start,
                endAt = end,
                zoneId = zone(it.zoneId),
                endZoneId = zone(it.endZoneId),
                note = it.note,
                createdAt = instant(it.createdAt),
                createdZoneId = zone(it.createdZoneId),
                updatedAt = instant(it.updatedAt),
                updatedZoneId = zone(it.updatedZoneId),
                reminderMinutes = it.reminderMinutes,
            )
        }
        // Entries of one layer never overlap; the two layers are independent.
        entities.groupBy { it.kind }.values.forEach { layer ->
            layer.sortedBy { it.startAt }.zipWithNext { a, b ->
                if (b.startAt < a.endAt) fail(BackupProblem.Overlap, "entries ${a.id} and ${b.id}")
            }
        }
        return entities
    }

    private fun schedule(dto: ScheduleDto): DailyScheduleEntity {
        if ((dto.approvedAt == null) != (dto.approvedZoneId == null)) fail(BackupProblem.Schedules, "approval of ${dto.id}")
        return DailyScheduleEntity(
            id = dto.id,
            name = dto.name,
            status = enumOf<ScheduleStatus>(dto.status),
            createdAt = instant(dto.createdAt),
            createdZoneId = zone(dto.createdZoneId),
            approvedAt = dto.approvedAt?.let(::instant),
            approvedZoneId = dto.approvedZoneId?.let(::zone),
            updatedAt = instant(dto.updatedAt),
            updatedZoneId = zone(dto.updatedZoneId),
            activeFrom = dto.activeFrom?.let(::date),
            activeUntil = dto.activeUntil?.let(::date),
            basedOnFrom = dto.basedOnFrom?.let(::date),
            basedOnTo = dto.basedOnTo?.let(::date),
        )
    }

    private fun scheduleBlocks(schedules: List<ScheduleDto>): List<ScheduleBlockEntity> {
        unique(schedules.flatMap { s -> s.blocks.map { it.id } }, "schedule block")
        val minutes = 0 until TimeRules.MINUTES_PER_DAY
        return schedules.flatMap { s ->
            s.blocks.forEachIndexed { i, a ->
                if (a.categoryId !in categorySeeds.keys) fail(BackupProblem.Categories, "${a.categoryId} on block ${a.id}")
                if (a.startMinuteOfDay !in minutes || a.endMinuteOfDay !in minutes || a.daysOfWeekMask !in 1..ScheduleRules.ALL_DAYS) {
                    fail(BackupProblem.Schedules, "block ${a.id}")
                }
                if (!ReminderRules.isLead(a.reminderMinutes)) fail(BackupProblem.Reminders, "reminder on block ${a.id}")
                s.blocks.drop(i + 1).forEach { b ->
                    val clash = ScheduleRules.overlaps(a.startMinuteOfDay, a.endMinuteOfDay, a.daysOfWeekMask, b.startMinuteOfDay, b.endMinuteOfDay, b.daysOfWeekMask)
                    if (clash) fail(BackupProblem.Schedules, "blocks ${a.id} and ${b.id}")
                }
            }
            s.blocks.map { ScheduleBlockEntity(it.id, s.id, it.categoryId, it.startMinuteOfDay, it.endMinuteOfDay, it.daysOfWeekMask, it.reminderMinutes) }
        }
    }

    /** One-off reminders: a title, a whole-minute time, and something the app can start (or nothing). */
    private fun reminders(rows: List<ReminderDto>): List<ReminderEntity> {
        unique(rows.map { it.id }, "reminder")
        val startable = setOf(StartAction.NONE, StartAction.EXERCISE, StartAction.QUIET, StartAction.SLEEP)
        return rows.map {
            val fireAt = instant(it.fireAt)
            val action = enumOf<StartAction>(it.action)
            if (it.title.isBlank() || it.title.length > ReminderRules.TITLE_MAX || TimeRules.toMinute(fireAt) != fireAt || action !in startable) {
                fail(BackupProblem.Reminders, "reminder ${it.id}")
            }
            ReminderEntity(
                id = it.id,
                kind = ReminderKind.ONE_OFF,
                title = it.title,
                categoryId = null,
                note = null,
                fireAt = fireAt,
                zoneId = zone(it.zoneId),
                activityStartAt = null,
                activityEndAt = null,
                action = action,
                createdAt = instant(it.createdAt),
                createdZoneId = zone(it.createdZoneId),
            )
        }
    }

    // Small pieces

    /** A finished session's timer: its active time, with no segment open. */
    private fun closedTimer(activeMillis: Long) = TimerCheckpoint(activeMillis, activeMillis, null, null)

    /** A backup holds finished sessions only: one still running or paused cannot be restored. */
    private fun finishedStatus(name: String, id: String): SessionStatus {
        val status = enumOf<SessionStatus>(name)
        if (status == SessionStatus.RUNNING || status == SessionStatus.PAUSED) fail(BackupProblem.Sessions, "session $id is still open")
        return status
    }

    private fun unique(ids: List<String>, what: String) {
        val seen = HashSet<String>()
        ids.firstOrNull { !seen.add(it) }?.let { fail(BackupProblem.DuplicateId, "$what $it") }
    }

    private fun instant(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    private fun zone(id: String): ZoneId = try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        fail(BackupProblem.BadTime, "zone $id")
    }

    private fun date(text: String): LocalDate = try {
        LocalDate.parse(text)
    } catch (_: DateTimeException) {
        fail(BackupProblem.BadTime, "date $text")
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fail(BackupProblem.UnknownValue, name)

    private fun fail(problem: BackupProblem, detail: String): Nothing = throw BackupException(problem, detail)
}
