package com.mauforge.paintlife.mytime

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** A proposal the user approved: what gets written. */
data class ApprovedProposal(
    val name: String,
    val blocks: List<ProposalBlock>,
    val basedOnFrom: LocalDate?,
    val basedOnTo: LocalDate?,
)

sealed interface ApproveResult {
    data class Approved(val scheduleId: String) : ApproveResult

    /** No blocks, a block of the wrong length, two blocks overlapping, or an unknown category. */
    data object Invalid : ApproveResult
}

/**
 * Schedules. A suggestion lives only in memory until [approve] writes it, in one transaction,
 * as the schedule active from today; the previous one ends yesterday. Nothing here changes
 * entries or past days.
 */
class ScheduleRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao = db.timeDao()

    fun observeSchedules(): Flow<List<ScheduleWithBlocks>> = dao.observeSchedules()

    suspend fun approve(proposal: ApprovedProposal): ApproveResult = db.withTransaction {
        val blocks = proposal.blocks
        val valid = blocks.isNotEmpty() &&
            blocks.all { it.startMinute in 0 until TimeRules.MINUTES_PER_DAY && it.endMinute in 0 until TimeRules.MINUTES_PER_DAY && it.daysMask in 1..ScheduleRules.ALL_DAYS } &&
            !ScheduleSuggester.anyOverlap(blocks) &&
            blocks.map { it.categoryId }.distinct().all { dao.category(it) != null }
        if (!valid) return@withTransaction ApproveResult.Invalid
        val now = time.now()
        val zone = time.zone()
        val today = time.today()
        dao.schedules()
            .filter { it.schedule.activeUntil == null || it.schedule.activeUntil >= today }
            .forEach { (old, _) ->
                dao.updateSchedule(old.copy(status = ScheduleStatus.ARCHIVED, activeUntil = today.minusDays(1), updatedAt = now, updatedZoneId = zone))
            }
        val id = UUID.randomUUID().toString()
        dao.insertSchedule(
            DailyScheduleEntity(
                id = id,
                name = proposal.name.trim().take(NAME_MAX).ifEmpty { DEFAULT_NAME },
                status = ScheduleStatus.APPROVED,
                createdAt = now,
                createdZoneId = zone,
                approvedAt = now,
                approvedZoneId = zone,
                updatedAt = now,
                updatedZoneId = zone,
                activeFrom = today,
                activeUntil = null,
                basedOnFrom = proposal.basedOnFrom,
                basedOnTo = proposal.basedOnTo,
            ),
        )
        dao.insertBlocks(
            blocks.map {
                ScheduleBlockEntity(
                    id = UUID.randomUUID().toString(),
                    scheduleId = id,
                    categoryId = it.categoryId,
                    startMinuteOfDay = it.startMinute,
                    endMinuteOfDay = it.endMinute,
                    daysOfWeekMask = it.daysMask,
                )
            },
        )
        ApproveResult.Approved(id)
    }

    companion object {
        const val NAME_MAX = 40
        const val DEFAULT_NAME = "My schedule"
    }
}
