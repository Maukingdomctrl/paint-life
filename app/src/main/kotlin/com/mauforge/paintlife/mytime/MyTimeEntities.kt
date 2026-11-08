package com.mauforge.paintlife.mytime

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId

/** Activity category. Built-in rows use stable keys as ids (e.g. "phone_internet"). */
@Entity(tableName = "activity_category")
data class ActivityCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Design-system colour token name, resolved in the UI. */
    val colorKey: String,
    /** User-editable flag; distraction analytics count categories with this flag. */
    val isDistraction: Boolean,
    val sortOrder: Int,
    val isBuiltIn: Boolean,
    /** Categories are archived, never deleted, so history keeps its labels. */
    val isArchived: Boolean,
)

/** Stored by name; never rename without a migration. */
enum class TimeEntryKind {
    ACTUAL,
    PLANNED,
}

/**
 * A block of time on the My Time timeline. Stored as instants plus the zone it was
 * recorded in; entries crossing midnight are split per local day at query time, never
 * in storage. Actual entries never overlap (enforced by the repository in Iteration 6).
 */
@Entity(
    tableName = "time_entry",
    foreignKeys = [
        ForeignKey(
            entity = ActivityCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["kind", "startAt"]), Index(value = ["categoryId"])],
)
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val kind: TimeEntryKind,
    val categoryId: String,
    val startAt: Instant,
    val endAt: Instant,
    val zoneId: ZoneId,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Stored by name; never rename without a migration. */
enum class ScheduleStatus {
    /** Suggested from observed data; has no effect until the user approves it. */
    PROPOSED,
    APPROVED,
    ARCHIVED,
}

@Entity(tableName = "daily_schedule")
data class DailyScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: ScheduleStatus,
    val createdAt: Instant,
    val approvedAt: Instant?,
    val updatedAt: Instant,
)

/**
 * One block of a schedule, in local wall-clock minutes (0-1439). A block whose end is
 * earlier than its start crosses midnight. [daysOfWeekMask] bit 0 = Monday … bit 6 = Sunday.
 */
@Entity(
    tableName = "schedule_block",
    foreignKeys = [
        ForeignKey(
            entity = DailyScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["scheduleId"]), Index(value = ["categoryId"])],
)
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val categoryId: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysOfWeekMask: Int,
)
