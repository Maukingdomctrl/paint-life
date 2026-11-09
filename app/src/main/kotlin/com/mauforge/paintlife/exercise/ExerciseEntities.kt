package com.mauforge.paintlife.exercise

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.TimerCheckpoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stored by name; never rename without a migration. */
enum class ExerciseSessionType {
    /** Counts towards cumulative volume; paused time is excluded. */
    VOLUME,

    /** Timed 1,000-repetition challenge against a cat; the clock keeps running while paused. */
    TIMED_CHALLENGE,
}

/** One Cat League opponent. Display content (name, breed, personality) lives in code, not here. */
@Entity(
    tableName = "challenge_level",
    indices = [Index(value = ["catKey"], unique = true)],
)
data class ChallengeLevelEntity(
    @PrimaryKey val level: Int,
    val catKey: String,
    val benchmarkMillis: Long,
    val isUnlocked: Boolean,
    val unlockedAt: Instant?,
    val unlockedZoneId: ZoneId?,
    /** When the user first finished 1,000 repetitions strictly faster than [benchmarkMillis]. */
    val beatenAt: Instant?,
    val beatenZoneId: ZoneId?,
)

@Entity(
    tableName = "exercise_session",
    foreignKeys = [
        ForeignKey(
            entity = ChallengeLevelEntity::class,
            parentColumns = ["level"],
            childColumns = ["challengeLevel"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["startDate"]),
        Index(value = ["startedAt"]),
        Index(value = ["status"]),
        Index(value = ["challengeLevel"]),
    ],
)
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val type: ExerciseSessionType,
    val status: SessionStatus,
    /** Set for timed challenges only. */
    val challengeLevel: Int?,
    val startedAt: Instant,
    val zoneId: ZoneId,
    /** Local date at [startedAt] in [zoneId]; a session counts on the day it started. */
    val startDate: LocalDate,
    val endedAt: Instant?,
    val endedZoneId: ZoneId?,
    val repetitions: Int,
    @Embedded val timer: TimerCheckpoint,
    val notes: String?,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
) {
    val isOpen: Boolean get() = status == SessionStatus.RUNNING || status == SessionStatus.PAUSED
}

/** Stored by name; never rename without a migration. */
enum class PersonalBestKind {
    /** Fastest completed timed challenge at a level; value is milliseconds. */
    TIMED_CHALLENGE_TIME,

    /** Most repetitions in one session; value is a count. */
    MOST_REPETITIONS_IN_SESSION,
}

/** Keyed by a stable string (e.g. "timed_level_3") so each record exists at most once. */
@Entity(
    tableName = "personal_best",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["sessionId"])],
)
data class PersonalBestEntity(
    @PrimaryKey val key: String,
    val kind: PersonalBestKind,
    val challengeLevel: Int?,
    val value: Long,
    val sessionId: String?,
    val achievedAt: Instant,
    val zoneId: ZoneId,
)
