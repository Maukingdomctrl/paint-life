package com.mauforge.paintlife.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The backup file: one JSON object, written by a manual export and read by an import.
 *
 * - Every moment is UTC epoch milliseconds beside the id of the zone it was recorded in; local
 *   dates are ISO-8601 text; enums are their names.
 * - Only what the user recorded is in it. Fixed reference data (cat names and benchmarks,
 *   category names and colours) stays in the app; the file carries just each level's unlock
 *   state and each category's distraction flag. Local dates that follow from a moment and its
 *   zone (a session's start date) are derived again on import.
 * - [formatVersion] changes only when a file from an older app could no longer be read as is.
 *   Fields added later get defaults, and unknown fields are ignored.
 *
 * These classes are the file format, separate from the Room entities on purpose: the database
 * can change without changing what an existing backup means.
 */
@Serializable
data class BackupFile(
    val format: String = BackupFormat.TAG,
    val formatVersion: Int = BackupFormat.VERSION,
    /** The app and database version that wrote the file (information only). */
    val appVersion: String = "",
    val databaseVersion: Int = 0,
    val exportedAt: Long,
    val exportedZoneId: String,
    val flags: FlagsDto = FlagsDto(),
    val dailyRecords: List<DailyRecordDto> = emptyList(),
    val priorities: List<PriorityDto> = emptyList(),
    val challengeLevels: List<ChallengeLevelDto> = emptyList(),
    val exerciseSessions: List<ExerciseSessionDto> = emptyList(),
    val personalBests: List<PersonalBestDto> = emptyList(),
    val quietSessions: List<QuietSessionDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val timeEntries: List<TimeEntryDto> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
)

/** Progress flags only; phone-specific ones are not backed up. */
@Serializable
data class FlagsDto(
    val firstLaunchDate: String? = null,
    val exerciseSafetyRead: Boolean = false,
    val victorySeenUpTo: Long = 0L,
    val observationComplete: Boolean = false,
    val insightsPeriod: String? = null,
    val progressPeriod: String? = null,
)

@Serializable
data class DailyRecordDto(
    val id: String,
    val date: String,
    val note: String? = null,
    val carryOverDismissed: Boolean = false,
    val createdAt: Long,
    val createdZoneId: String,
    val updatedAt: Long,
    val updatedZoneId: String,
)

@Serializable
data class PriorityDto(
    val id: String,
    val date: String,
    val position: Int,
    val title: String,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val completedZoneId: String? = null,
    val carriedFromId: String? = null,
    val createdAt: Long,
    val createdZoneId: String,
    val updatedAt: Long,
    val updatedZoneId: String,
)

/** A Cat League level's state. Its cat and benchmark are fixed in the app. */
@Serializable
data class ChallengeLevelDto(
    val level: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null,
    val unlockedZoneId: String? = null,
    val beatenAt: Long? = null,
    val beatenZoneId: String? = null,
)

/** A finished exercise session. Open sessions are never exported. */
@Serializable
data class ExerciseSessionDto(
    val id: String,
    val type: String,
    val status: String,
    val challengeLevel: Int? = null,
    val startedAt: Long,
    val zoneId: String,
    val endedAt: Long? = null,
    val endedZoneId: String? = null,
    val repetitions: Int,
    val activeDurationMillis: Long,
    val notes: String? = null,
    val updatedAt: Long,
    val updatedZoneId: String,
)

@Serializable
data class PersonalBestDto(
    val key: String,
    val kind: String,
    val challengeLevel: Int? = null,
    val value: Long,
    val sessionId: String? = null,
    val achievedAt: Long,
    val zoneId: String,
)

/** A finished quiet session. Open sessions are never exported. */
@Serializable
data class QuietSessionDto(
    val id: String,
    val status: String,
    val startedAt: Long,
    val zoneId: String,
    val endedAt: Long? = null,
    val endedZoneId: String? = null,
    val targetDurationMillis: Long,
    val activeDurationMillis: Long,
    val updatedAt: Long,
    val updatedZoneId: String,
)

/** One of the fixed categories and its distraction flag (the only thing the user can change). */
@Serializable
data class CategoryDto(val id: String, val isDistraction: Boolean)

@Serializable
data class TimeEntryDto(
    val id: String,
    val kind: String,
    val categoryId: String,
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val endZoneId: String,
    val note: String? = null,
    val createdAt: Long,
    val createdZoneId: String,
    val updatedAt: Long,
    val updatedZoneId: String,
)

@Serializable
data class ScheduleDto(
    val id: String,
    val name: String,
    val status: String,
    val createdAt: Long,
    val createdZoneId: String,
    val approvedAt: Long? = null,
    val approvedZoneId: String? = null,
    val updatedAt: Long,
    val updatedZoneId: String,
    val activeFrom: String? = null,
    val activeUntil: String? = null,
    val basedOnFrom: String? = null,
    val basedOnTo: String? = null,
    val blocks: List<ScheduleBlockDto> = emptyList(),
)

@Serializable
data class ScheduleBlockDto(
    val id: String,
    val categoryId: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysOfWeekMask: Int,
)

/** Why a file cannot be imported (or could not be read or written). */
enum class BackupProblem {
    /** Not JSON, or not a Paint Life backup. */
    NotABackup,

    /** Written by a newer app, in a format this one does not know. */
    TooNew,
    TooLarge,

    /** The file could not be opened, read or written. */
    Unreadable,

    /** A field is missing or has the wrong type. */
    Damaged,

    /** A date, or a time-zone id, that does not exist. */
    BadTime,

    /** A status, type or kind this app does not know. */
    UnknownValue,
    DuplicateId,

    /** A record refers to another one that is not in the file. */
    BrokenLink,

    /** More than three priorities on a day, or positions that are not 0, 1, 2. */
    Priorities,

    /** Not exactly the ten levels, or unlocks that skip a level. */
    CatLeague,

    /** A session that is still open, or has values that cannot be (negative time, too many reps). */
    Sessions,

    /** A category that is not one of the fixed eleven. */
    Categories,

    /** A time entry that is not whole minutes, 1 minute to 24 hours long. */
    TimeEntries,

    /** Two entries of the same layer overlap. */
    Overlap,

    /** A schedule block outside the day, without days, or overlapping another. */
    Schedules,
}

class BackupException(val problem: BackupProblem, detail: String? = null) :
    Exception(if (detail == null) problem.name else "${problem.name}: $detail")

/** Reads and writes the file text. Checking what the records mean is [BackupMapper]'s job. */
object BackupFormat {
    const val TAG = "paint-life-backup"

    /** The newest format this app writes and reads. */
    const val VERSION = 1

    /** Larger files are refused unread; decades of records stay far below this. */
    const val MAX_BYTES = 32 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Parses [text]; throws [BackupException] when it is not a backup this app can read. */
    fun decode(text: String): BackupFile {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw BackupException(BackupProblem.NotABackup)
        }
        val fields = root as? JsonObject ?: throw BackupException(BackupProblem.NotABackup)
        val tag = (fields["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (tag != TAG) throw BackupException(BackupProblem.NotABackup)
        val version = (fields["formatVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            ?: throw BackupException(BackupProblem.Damaged, "formatVersion")
        if (version > VERSION) throw BackupException(BackupProblem.TooNew, "format $version")
        if (version < 1) throw BackupException(BackupProblem.Damaged, "formatVersion")
        return try {
            json.decodeFromJsonElement(BackupFile.serializer(), fields)
        } catch (error: SerializationException) {
            throw BackupException(BackupProblem.Damaged, error.message?.lineSequence()?.firstOrNull())
        } catch (error: IllegalArgumentException) {
            throw BackupException(BackupProblem.Damaged, error.message?.lineSequence()?.firstOrNull())
        }
    }
}
