package com.mauforge.paintlife.today

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One row per local calendar day the user has done something on (added a priority, dismissed
 * the carry-over offer). Opening the app alone never creates one.
 */
@Entity(
    tableName = "daily_record",
    indices = [Index(value = ["date"], unique = true)],
)
data class DailyRecordEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val note: String?,
    /** The user chose "Not now" on the offer to bring unfinished priorities to this day. */
    val carryOverDismissed: Boolean,
    val createdAt: Instant,
    val createdZoneId: ZoneId,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
)

/**
 * A daily priority. [date] is the local day it was planned for, which is a calendar label
 * rather than a moment; every moment is stored as an [Instant] plus the zone it was recorded in.
 *
 * At most three per day (enforced in [TodayRepository] inside a transaction). (date, position)
 * is unique, so positions stay 0, 1, 2 with no gaps or duplicates. (date, carriedFromId) is
 * unique, so the same unfinished priority can be brought to a day only once.
 */
@Entity(
    tableName = "priority",
    indices = [
        Index(value = ["date", "position"], unique = true),
        Index(value = ["date", "carriedFromId"], unique = true),
    ],
)
data class PriorityEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val position: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val completedZoneId: ZoneId?,
    /** The earlier priority this one was copied from; the original is never changed. */
    val carriedFromId: String?,
    val createdAt: Instant,
    val createdZoneId: ZoneId,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
)
