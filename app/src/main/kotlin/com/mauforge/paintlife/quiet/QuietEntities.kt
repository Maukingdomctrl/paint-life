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

/**
 * A quiet-time session. Only [SessionStatus.COMPLETED] with the full target time counts as done
 * ([QuietRules.countsAsCompleted]); [SessionStatus.ENDED_EARLY] keeps its minutes, and
 * [SessionStatus.INCOMPLETE] (app killed or phone restarted) never counts.
 *
 * Every instant is stored with the zone it was recorded in.
 */
@Entity(
    tableName = "quiet_session",
    indices = [Index(value = ["startDate"]), Index(value = ["startedAt"]), Index(value = ["status"])],
)
data class QuietSessionEntity(
    @PrimaryKey val id: String,
    val status: SessionStatus,
    val startedAt: Instant,
    val zoneId: ZoneId,
    /** Local date at [startedAt] in [zoneId]; a session counts on the day it started. */
    val startDate: LocalDate,
    /** For a completed session, the moment it reached the target time. */
    val endedAt: Instant?,
    val endedZoneId: ZoneId?,
    val targetDurationMillis: Long,
    @Embedded val timer: TimerCheckpoint,
    val updatedAt: Instant,
    val updatedZoneId: ZoneId,
) {
    val isOpen: Boolean get() = status == SessionStatus.RUNNING || status == SessionStatus.PAUSED
}

/** The fields the history and totals need from one finished session. */
data class QuietSessionSummary(
    val startDate: LocalDate,
    val status: SessionStatus,
    val activeDurationMillis: Long,
    val targetDurationMillis: Long,
)
