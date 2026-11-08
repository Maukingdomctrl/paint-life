package com.mauforge.paintlife.quiet

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.TimerCheckpoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A quiet-time session. Only [SessionStatus.COMPLETED] with full target time counts as done. */
@Entity(
    tableName = "quiet_session",
    indices = [Index(value = ["startDate"]), Index(value = ["status"])],
)
data class QuietSessionEntity(
    @PrimaryKey val id: String,
    val status: SessionStatus,
    val startedAt: Instant,
    val zoneId: ZoneId,
    /** Local date at [startedAt] in [zoneId]; a session counts on the day it started. */
    val startDate: LocalDate,
    val endedAt: Instant?,
    val targetDurationMillis: Long,
    @Embedded val timer: TimerCheckpoint,
    val updatedAt: Instant,
)
