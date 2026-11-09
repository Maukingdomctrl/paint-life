package com.mauforge.paintlife.mytime

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
 * A block of time on the My Time timeline, to the whole minute, 1 minute to 24 hours long
 * ([TimeRules]). Stored as instants plus the zones they were recorded in; entries crossing
 * midnight are split per local day when shown or totalled, never in storage. Entries of one
 * [kind] never overlap each other (enforced by [TimeRepository]).
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
    /** Zone at [startAt]; the entry is split at local midnight in this zone. */
    val zoneId: ZoneId,
    /** Zone at [endAt] (differs from [zoneId] when the zone changed during the activity). */
    val endZoneId: ZoneId,
    val note: String?,
    val createdAt: Instant,
    val createdZoneId: ZoneId,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
) {
    val durationMinutes: Long get() = Duration.between(startAt, endAt).toMinutes()
}

/** An entry with its category's display fields (joined in [TimeDao]). */
data class EntryWithCategory(
    @Embedded val entry: TimeEntryEntity,
    val categoryName: String,
    val colorKey: String,
    val isDistraction: Boolean,
)

/**
 * The running "now doing" activity: at most one row, id [ID]. Wall-clock based, so it survives
 * a reboot. Stopping it (or starting another) saves it as an actual [TimeEntryEntity].
 */
@Entity(
    tableName = "current_activity",
    foreignKeys = [
        ForeignKey(
            entity = ActivityCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["categoryId"])],
)
data class CurrentActivityEntity(
    @PrimaryKey val id: String = ID,
    val categoryId: String,
    val note: String?,
    val startAt: Instant,
    val zoneId: ZoneId,
) {
    companion object {
        const val ID = "current"
    }
}

/** Stored by name; never rename without a migration. */
enum class ScheduleStatus {
    /** Suggested from observed data; has no effect until the user approves it. */
    PROPOSED,
    APPROVED,
    ARCHIVED,
}

/**
 * A schedule (Iteration 6b). The planned layer of a day without its own planned entries shows the
 * blocks of the schedule active that day ([activeFrom]..[activeUntil], inclusive), so editing or
 * replacing a schedule never rewrites history. Nothing is active until the user approves it.
 */
@Entity(tableName = "daily_schedule")
data class DailyScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: ScheduleStatus,
    val createdAt: Instant,
    val createdZoneId: ZoneId,
    val approvedAt: Instant?,
    val approvedZoneId: ZoneId?,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
    val activeFrom: LocalDate?,
    val activeUntil: LocalDate?,
    /** The observed days a suggestion was built from. */
    val basedOnFrom: LocalDate?,
    val basedOnTo: LocalDate?,
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
