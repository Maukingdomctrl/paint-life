package com.mauforge.paintlife.today

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.time.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Unfinished priorities from an earlier day that the user may copy to [targetDate]. */
data class CarryOverOffer(
    val sourceDate: LocalDate,
    val targetDate: LocalDate,
    val candidates: List<PriorityEntity>,
)

sealed interface AddResult {
    data class Added(val priority: PriorityEntity) : AddResult
    data object LimitReached : AddResult
    data object InvalidTitle : AddResult
}

enum class MoveDirection { Up, Down }

/**
 * Daily priorities. Every write that touches more than one row runs in a transaction, and the
 * three-per-day limit is checked inside the same transaction as the insert, so concurrent or
 * repeated requests can never produce a fourth priority. Nothing here deletes data because a
 * date changed: earlier days stay exactly as they were.
 */
@Singleton
class TodayRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao: TodayDao = db.todayDao()

    fun observePriorities(date: LocalDate): Flow<List<PriorityEntity>> = dao.observePriorities(date)

    /**
     * The offer to bring unfinished priorities to [today]: they come from the latest earlier
     * day (within [CARRY_OVER_LOOKBACK_DAYS]) that had any priorities. Null when there is
     * nothing open there, or the user said "Not now" today.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCarryOver(today: LocalDate): Flow<CarryOverOffer?> =
        dao.observeLatestPlannedDay(before = today, earliest = today.minusDays(CARRY_OVER_LOOKBACK_DAYS))
            .flatMapLatest { source ->
                if (source == null) {
                    flowOf(null)
                } else {
                    combine(dao.observeOpenNotCarried(source, today), dao.observeDailyRecord(today)) { open, record ->
                        if (open.isEmpty() || record?.carryOverDismissed == true) null else CarryOverOffer(source, today, open)
                    }
                }
            }

    suspend fun add(date: LocalDate, rawTitle: String): AddResult {
        val title = PriorityTitle.clean(rawTitle) ?: return AddResult.InvalidTitle
        return db.withTransaction {
            val count = dao.countPriorities(date)
            if (count >= MAX_PRIORITIES_PER_DAY) return@withTransaction AddResult.LimitReached
            ensureDailyRecord(date)
            val now = time.now()
            val zone = time.zone()
            val priority = PriorityEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                position = count,
                title = title,
                isCompleted = false,
                completedAt = null,
                completedZoneId = null,
                carriedFromId = null,
                createdAt = now,
                createdZoneId = zone,
                updatedAt = now,
                updatedZoneId = zone,
            )
            dao.insertPriority(priority)
            AddResult.Added(priority)
        }
    }

    /** Returns false if the title is blank or too long, or the priority no longer exists. */
    suspend fun rename(id: String, rawTitle: String): Boolean {
        val title = PriorityTitle.clean(rawTitle) ?: return false
        return db.withTransaction {
            val current = dao.priority(id) ?: return@withTransaction false
            if (current.title != title) {
                dao.updatePriority(current.copy(title = title, updatedAt = time.now(), updatedZoneId = time.zone()))
            }
            true
        }
    }

    /** Sets (never flips) the completed state, so a repeated tap cannot undo the first one. */
    suspend fun setCompleted(id: String, completed: Boolean) {
        db.withTransaction {
            val current = dao.priority(id) ?: return@withTransaction
            if (current.isCompleted == completed) return@withTransaction
            val now = time.now()
            val zone = time.zone()
            dao.updatePriority(
                current.copy(
                    isCompleted = completed,
                    completedAt = if (completed) now else null,
                    completedZoneId = if (completed) zone else null,
                    updatedAt = now,
                    updatedZoneId = zone,
                ),
            )
        }
    }

    /** Deletes one priority and closes the gap. Returns the deleted row so it can be restored. */
    suspend fun delete(id: String): PriorityEntity? = db.withTransaction {
        val deleted = dao.priority(id) ?: return@withTransaction null
        dao.deletePriority(id)
        // One row at a time, lowest first, so the unique (date, position) index never clashes.
        dao.priorities(deleted.date).forEachIndexed { index, row ->
            if (row.position != index) dao.setPosition(row.id, index)
        }
        deleted
    }

    /**
     * Puts a deleted priority back with its id, state and place in the list. Returns false if
     * its day has meanwhile filled up to three, or it already exists.
     */
    suspend fun restore(priority: PriorityEntity): Boolean = db.withTransaction {
        if (dao.priority(priority.id) != null) return@withTransaction false
        val rows = dao.priorities(priority.date)
        if (rows.size >= MAX_PRIORITIES_PER_DAY) return@withTransaction false
        val position = priority.position.coerceIn(0, rows.size)
        // Shift the rows after it down by one, highest first, to keep positions unique.
        rows.asReversed().forEach { row ->
            if (row.position >= position) dao.setPosition(row.id, row.position + 1)
        }
        ensureDailyRecord(priority.date)
        dao.insertPriority(priority.copy(position = position))
        true
    }

    /** Swaps a priority with its neighbour. Does nothing at either end of the list. */
    suspend fun move(id: String, direction: MoveDirection) {
        db.withTransaction {
            val current = dao.priority(id) ?: return@withTransaction
            val rows = dao.priorities(current.date)
            val index = rows.indexOfFirst { it.id == id }
            val otherIndex = if (direction == MoveDirection.Up) index - 1 else index + 1
            val other = rows.getOrNull(otherIndex) ?: return@withTransaction
            // Park one row outside the valid range while swapping, so (date, position) stays unique.
            dao.setPosition(current.id, PARKED_POSITION)
            dao.setPosition(other.id, index)
            dao.setPosition(current.id, otherIndex)
        }
    }

    /**
     * Copies the chosen earlier priorities to [target] as new, unfinished priorities, in their
     * original order, up to the free places on that day. The originals are never changed.
     * Ids already copied to [target], not open any more, or not from an earlier day are
     * skipped, so repeating the request copies nothing twice. Returns how many were copied.
     */
    suspend fun carryOver(target: LocalDate, ids: Collection<String>): Int = db.withTransaction {
        val existing = dao.priorities(target)
        val alreadyCarried = existing.mapNotNullTo(HashSet()) { it.carriedFromId }
        val free = MAX_PRIORITIES_PER_DAY - existing.size
        if (free <= 0) return@withTransaction 0
        val sources = ids.distinct()
            .mapNotNull { dao.priority(it) }
            .filter { !it.isCompleted && it.date < target && it.id !in alreadyCarried }
            .sortedWith(compareBy({ it.date }, { it.position }))
            .take(free)
        if (sources.isEmpty()) return@withTransaction 0
        ensureDailyRecord(target)
        val now = time.now()
        val zone = time.zone()
        sources.forEachIndexed { offset, source ->
            dao.insertPriority(
                PriorityEntity(
                    id = UUID.randomUUID().toString(),
                    date = target,
                    position = existing.size + offset,
                    title = source.title,
                    isCompleted = false,
                    completedAt = null,
                    completedZoneId = null,
                    carriedFromId = source.id,
                    createdAt = now,
                    createdZoneId = zone,
                    updatedAt = now,
                    updatedZoneId = zone,
                ),
            )
        }
        sources.size
    }

    /** "Not now": hides the carry-over offer for [date] only. */
    suspend fun dismissCarryOver(date: LocalDate) {
        db.withTransaction {
            ensureDailyRecord(date)
            dao.dismissCarryOver(date, time.now(), time.zone())
        }
    }

    private suspend fun ensureDailyRecord(date: LocalDate) {
        val now = time.now()
        val zone = time.zone()
        dao.insertDailyRecordIfAbsent(
            DailyRecordEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                note = null,
                carryOverDismissed = false,
                createdAt = now,
                createdZoneId = zone,
                updatedAt = now,
                updatedZoneId = zone,
            ),
        )
    }

    companion object {
        const val MAX_PRIORITIES_PER_DAY = 3

        /** How far back the carry-over offer looks for the last planned day. */
        const val CARRY_OVER_LOOKBACK_DAYS = 7L

        private const val PARKED_POSITION = -1
    }
}

/** Title rules shared by the repository and the editor. */
object PriorityTitle {
    const val MAX_LENGTH = 80

    /** Trimmed single-line title, or null if it is blank or longer than [MAX_LENGTH]. */
    fun clean(raw: String): String? {
        val title = raw.replace(Regex("\\s+"), " ").trim()
        return if (title.isEmpty() || title.length > MAX_LENGTH) null else title
    }
}
