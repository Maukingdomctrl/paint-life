package com.mauforge.paintlife.mytime

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** An entry to save. [id] null = a new entry. Times are rounded down to the minute on save. */
data class EntryDraft(
    val id: String?,
    val kind: TimeEntryKind,
    val categoryId: String,
    val note: String?,
    val start: Instant,
    val end: Instant,
    /** Set when the draft is the running activity being stopped: saving it also clears it. */
    val fromCurrent: Boolean = false,
)

/** Rows as they were ([before]) and as they are now ([after]); undoing swaps them back. */
data class EntryChange(val before: List<TimeEntryEntity>, val after: List<TimeEntryEntity>)

sealed interface SaveResult {
    /** Saved. [change] is set when other entries were changed too, so Undo can be offered. */
    data class Saved(val entry: TimeEntryEntity, val change: EntryChange?) : SaveResult

    /** Nothing was saved: these neighbours overlap. Save again with Fit or Replace. */
    data class Overlaps(val fit: List<OverlapChange>) : SaveResult

    /** Shorter than a minute, longer than 24 hours, or an unknown category. */
    data object Invalid : SaveResult

    /** The entry being edited no longer exists. */
    data object Missing : SaveResult
}

/** What starting or stopping the running activity did. */
sealed interface RunResult {
    /** Done; [saved] is the entry the previous activity became, if any. */
    data class Done(val saved: TimeEntryEntity?) : RunResult

    /** The running activity is over 16 hours old: its end must be confirmed in the sheet first. */
    data class ConfirmEnd(val draft: EntryDraft) : RunResult

    /** Saving the running activity would overlap entries: resolve it in the sheet first. */
    data class Overlaps(val draft: EntryDraft, val fit: List<OverlapChange>) : RunResult
}

/**
 * Stored time entries and the running activity. Wall-clock instants are stamped here with their
 * zone; entries of one kind never overlap, and every multi-row write is one transaction.
 */
class TimeRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val time: TimeProvider,
) {
    private val dao = db.timeDao()

    fun observeCategories(): Flow<List<ActivityCategoryEntity>> = dao.observeCategories()

    /** Entries of [kind] overlapping [from, to). */
    fun observeEntries(kind: TimeEntryKind, from: Instant, to: Instant): Flow<List<EntryWithCategory>> =
        dao.observeEntries(kind, from, to, TimeRules.earliestStart(from))

    /** Actual entries that may touch [date] in any zone (see [DayView.readWindow]). */
    fun observeDayEntries(date: LocalDate, kind: TimeEntryKind = TimeEntryKind.ACTUAL): Flow<List<EntryWithCategory>> {
        val (from, to) = DayView.readWindow(date)
        return observeEntries(kind, from, to)
    }

    /** The last few distinct activities (category + note), newest first. */
    fun observeRecent(): Flow<List<RecentActivityRow>> =
        dao.observeRecent(time.now().minus(TimeRules.RECENT_WINDOW), TimeRules.RECENT_LIMIT)

    fun observeCurrent(): Flow<CurrentActivityEntity?> = dao.observeCurrent()

    /** Days (up to 7) with at least an hour of actual time recorded. */
    fun observeObservedDays(): Flow<Int> = dao.observeActualSpans().map(TimeRules::observedDays).distinctUntilChanged()

    /** True once a sleep overlaps the night before [date] (18:00 the day before to 14:00) in [zone]. */
    fun observeSleepLogged(date: LocalDate, zone: ZoneId): Flow<Boolean> {
        val from = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val to = date.atTime(TimeRules.MORNING_ENDS).atZone(zone).toInstant()
        return dao.observeSleepCount(TimeRules.SLEEP_ID, from, to, TimeRules.earliestStart(from)).map { it > 0 }.distinctUntilChanged()
    }

    suspend fun entry(id: String): TimeEntryEntity? = dao.entry(id)

    /** Where the latest actual entry that ended by [until] ended, looking back a day. */
    suspend fun lastActualEnd(until: Instant): Instant? = dao.lastActualEnd(until.minus(Duration.ofDays(2)), until)

    /** Last night's sleep, prefilled from the last 7 logged sleeps. */
    suspend fun sleepPrefill(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> {
        val since = date.atStartOfDay(zone).toInstant().minus(Duration.ofDays(60))
        return TimeRules.sleepPrefill(dao.recentSleeps(TimeRules.SLEEP_ID, since, 7), date, zone)
    }

    /**
     * Saves [draft]. With [Resolution.None] nothing is written when it would overlap another entry
     * of its kind; the overlaps come back for the user to choose. Fit and Replace change the
     * neighbours as [TimeRules.planOverlaps] says, in the same transaction.
     */
    suspend fun save(draft: EntryDraft, resolution: Resolution): SaveResult = db.withTransaction { saveLocked(draft, resolution) }

    private suspend fun saveLocked(draft: EntryDraft, resolution: Resolution): SaveResult {
        val start = TimeRules.toMinute(draft.start)
        val end = TimeRules.toMinute(draft.end)
        if (!TimeRules.isValidSpan(start, end) || dao.category(draft.categoryId) == null) return SaveResult.Invalid
        val original = draft.id?.let { dao.entry(it) ?: return SaveResult.Missing }
        val neighbours = dao.overlapping(draft.kind, start, end, TimeRules.earliestStart(start)).filter { it.id != draft.id }
        if (neighbours.isNotEmpty() && resolution == Resolution.None) {
            return SaveResult.Overlaps(TimeRules.planOverlaps(start, end, neighbours, Resolution.Fit))
        }
        val now = time.now()
        val zone = time.zone()
        val before = mutableListOf<TimeEntryEntity>()
        val after = mutableListOf<TimeEntryEntity>()
        TimeRules.planOverlaps(start, end, neighbours, resolution).forEach { change ->
            before += change.original
            when (change) {
                is OverlapChange.Remove -> dao.delete(listOf(change.original.id))
                is OverlapChange.Trim -> change.original.copy(startAt = change.start, endAt = change.end, updatedAt = now, updatedZoneId = zone)
                    .also { dao.update(it); after += it }
                is OverlapChange.Split -> {
                    val first = change.original.copy(endAt = change.firstEnd, endZoneId = change.original.zoneId, updatedAt = now, updatedZoneId = zone)
                    val second = change.original.copy(
                        id = UUID.randomUUID().toString(),
                        startAt = change.secondStart,
                        zoneId = change.original.endZoneId,
                        createdAt = now,
                        createdZoneId = zone,
                        updatedAt = now,
                        updatedZoneId = zone,
                    )
                    dao.update(first)
                    dao.insert(second)
                    after += first
                    after += second
                }
            }
        }
        val note = draft.note?.trim()?.take(NOTE_MAX)?.ifEmpty { null }
        val saved = if (original == null) {
            TimeEntryEntity(
                id = UUID.randomUUID().toString(),
                kind = draft.kind,
                categoryId = draft.categoryId,
                startAt = start,
                endAt = end,
                zoneId = zone,
                endZoneId = zone,
                note = note,
                createdAt = now,
                createdZoneId = zone,
                updatedAt = now,
                updatedZoneId = zone,
            ).also { dao.insert(it) }
        } else {
            original.copy(categoryId = draft.categoryId, note = note, startAt = start, endAt = end, updatedAt = now, updatedZoneId = zone)
                .also { dao.update(it) }
        }
        if (draft.fromCurrent) dao.clearCurrent()
        val change = if (before.isEmpty()) null else EntryChange(before = before + listOfNotNull(original), after = after + saved)
        return SaveResult.Saved(saved, change)
    }

    /** Deletes an entry at once; the returned change lets Undo put it back. */
    suspend fun delete(id: String): EntryChange? = db.withTransaction {
        val stored = dao.entry(id) ?: return@withTransaction null
        dao.delete(listOf(id))
        EntryChange(before = listOf(stored), after = emptyList())
    }

    /**
     * Undo: the rows of [change] go back to how they were. False (and nothing changes) when any of
     * them has been edited since, or putting them back would overlap something saved since.
     */
    suspend fun undo(change: EntryChange): Boolean = db.withTransaction {
        val current = dao.entries(change.after.map { it.id })
        if (current.toSet() != change.after.toSet()) return@withTransaction false
        val afterIds = change.after.map { it.id }.toSet()
        val clash = change.before.any { row ->
            dao.overlapping(row.kind, row.startAt, row.endAt, TimeRules.earliestStart(row.startAt))
                .any { it.id !in afterIds && change.before.none { b -> b.id == it.id } }
        }
        if (clash) return@withTransaction false
        dao.delete(afterIds.toList())
        change.before.forEach { dao.insert(it) }
        true
    }

    /**
     * Starts "now doing" [categoryId]. A running activity ends at this moment and is saved first;
     * if that needs the user (over 16 hours, or overlaps), nothing changes and the result says so.
     */
    suspend fun startCurrent(categoryId: String, note: String?): RunResult = db.withTransaction {
        val now = TimeRules.toMinute(time.now())
        val ended = endCurrentLocked(now) ?: return@withTransaction pendingRun(now)
        dao.upsertCurrent(CurrentActivityEntity(categoryId = categoryId, note = note?.trim()?.ifEmpty { null }, startAt = now, zoneId = time.zone()))
        RunResult.Done(ended.getOrNull())
    }

    /** Stops the running activity now and saves it. */
    suspend fun stopCurrent(): RunResult = db.withTransaction {
        val now = TimeRules.toMinute(time.now())
        val ended = endCurrentLocked(now) ?: return@withTransaction pendingRun(now)
        RunResult.Done(ended.getOrNull())
    }

    /** Discards the running activity without saving it. */
    suspend fun discardCurrent(): Boolean = dao.clearCurrent() > 0

    /**
     * Ends the running activity at [now] if it can be saved without the user: Result(entry) when
     * saved, Result(null) when there was none or it ran under a minute (discarded), null when the
     * user must confirm (see [pendingRun]).
     */
    private suspend fun endCurrentLocked(now: Instant): Result<TimeEntryEntity?>? {
        val current = dao.current() ?: return Result.success(null)
        val length = Duration.between(current.startAt, now)
        if (length < Duration.ofMinutes(1)) {
            dao.clearCurrent()
            return Result.success(null)
        }
        if (length > TimeRules.LONG_RUNNING) return null
        return when (val saved = saveLocked(currentDraft(current, now), Resolution.None)) {
            is SaveResult.Saved -> Result.success(saved.entry)
            // Its category no longer exists: nothing can be saved, so it is dropped.
            SaveResult.Invalid, SaveResult.Missing -> {
                dao.clearCurrent()
                Result.success(null)
            }
            is SaveResult.Overlaps -> null
        }
    }

    private suspend fun pendingRun(now: Instant): RunResult {
        val current = dao.current() ?: return RunResult.Done(null)
        val draft = currentDraft(current, now)
        if (Duration.between(current.startAt, now) > TimeRules.LONG_RUNNING) {
            return RunResult.ConfirmEnd(draft.copy(end = minOf(now, current.startAt.plus(TimeRules.MAX_DURATION))))
        }
        val neighbours = dao.overlapping(TimeEntryKind.ACTUAL, draft.start, draft.end, TimeRules.earliestStart(draft.start))
        return RunResult.Overlaps(draft, TimeRules.planOverlaps(draft.start, draft.end, neighbours, Resolution.Fit))
    }

    private fun currentDraft(current: CurrentActivityEntity, now: Instant) = EntryDraft(
        id = null,
        kind = TimeEntryKind.ACTUAL,
        categoryId = current.categoryId,
        note = current.note,
        start = current.startAt,
        end = now,
        fromCurrent = true,
    )

    companion object {
        const val NOTE_MAX = 120
    }
}
