package com.mauforge.paintlife.today

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** One row per local calendar day the user has interacted with. */
@Entity(
    tableName = "daily_record",
    indices = [Index(value = ["date"], unique = true)],
)
data class DailyRecordEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** A daily priority (maximum three per day, enforced by the repository in Iteration 3). */
@Entity(
    tableName = "priority",
    indices = [Index(value = ["date"])],
)
data class PriorityEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val position: Int,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
