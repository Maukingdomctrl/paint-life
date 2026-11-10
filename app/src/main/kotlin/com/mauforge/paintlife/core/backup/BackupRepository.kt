package com.mauforge.paintlife.core.backup

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.exercise.ExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Something still in progress; an import waits until it is finished. */
enum class InProgress { Exercise, Quiet, NowDoing }

sealed interface ReplaceResult {
    data class Replaced(val summary: BackupSummary) : ReplaceResult

    /** Nothing was changed: these are still in progress. */
    data class Blocked(val inProgress: Set<InProgress>) : ReplaceResult
}

/**
 * Tells screens that every record was just replaced by an imported backup, so they drop what
 * they remembered from before (an Undo offer for something deleted earlier must not put a row
 * back into the imported data).
 */
@Singleton
class DataReplaced @Inject constructor() {
    private val _count = MutableStateFlow(0)

    /** Goes up by one with every import. */
    val count: StateFlow<Int> = _count.asStateFlow()

    fun signal() {
        _count.update { it + 1 }
    }
}

/**
 * Manual backup: everything the user recorded, out to a JSON file and back. This is the only way
 * data leaves the device (Android's own backup is off).
 *
 * - Export reads every table in one transaction, so the file is one consistent moment. Sessions
 *   still open and a running "now doing" activity are left out: they are not history yet.
 * - Import is two steps, so nothing is ever overwritten silently: [read] parses and checks a file
 *   without touching the database, and [replaceAll] (after the user has confirmed) replaces every
 *   record in one transaction. If anything fails, nothing has changed.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val preferences: UserPreferencesRepository,
    private val exercise: ExerciseRepository,
    private val time: TimeProvider,
    private val replaced: DataReplaced,
) {
    private val dao = db.backupDao()

    /** How much is stored on the phone now (finished records only). */
    suspend fun summary(): BackupSummary = db.withTransaction {
        BackupSummary(dao.priorityCount(), dao.exerciseSessionCount(), dao.quietSessionCount(), dao.timeEntryCount(), dao.scheduleCount())
    }

    /** What is in progress right now; empty when an import may go ahead. */
    suspend fun inProgress(): Set<InProgress> = db.withTransaction { inProgressLocked() }

    private suspend fun inProgressLocked(): Set<InProgress> = buildSet {
        if (dao.openExerciseSessions() > 0) add(InProgress.Exercise)
        if (dao.openQuietSessions() > 0) add(InProgress.Quiet)
        if (dao.runningActivities() > 0) add(InProgress.NowDoing)
    }

    /** Everything stored now, as one consistent snapshot. */
    suspend fun snapshot(): BackupContents {
        val flags = preferences.backupFlags()
        return db.withTransaction {
            BackupContents(
                exportedAt = time.now(),
                exportedZone = time.zone(),
                flags = flags,
                dailyRecords = dao.dailyRecords(),
                priorities = dao.priorities(),
                levels = dao.levels(),
                exerciseSessions = dao.finishedExerciseSessions(),
                personalBests = dao.personalBests(),
                quietSessions = dao.finishedQuietSessions(),
                distractionFlags = dao.categories().associate { it.id to it.isDistraction },
                timeEntries = dao.timeEntries(),
                schedules = dao.schedules(),
                scheduleBlocks = dao.scheduleBlocks(),
            )
        }
    }

    /** Writes a backup of everything stored now to [output]. Throws [IOException] if writing fails. */
    suspend fun export(output: OutputStream, appVersion: String): BackupSummary {
        val contents = snapshot()
        val text = BackupFormat.encode(BackupMapper.toFile(contents, appVersion, DATABASE_VERSION))
        withContext(Dispatchers.IO) {
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        }
        return contents.summary
    }

    /**
     * Reads and checks a backup without changing anything. Throws [BackupException] when the
     * file is not a backup, is from a newer app, or holds records the app could not have made.
     */
    suspend fun read(input: InputStream): BackupContents = withContext(Dispatchers.IO) {
        val bytes = try {
            readLimited(input)
        } catch (_: IOException) {
            throw BackupException(BackupProblem.Unreadable)
        }
        BackupMapper.toContents(BackupFormat.decode(String(bytes, Charsets.UTF_8)))
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return out.toByteArray()
            out.write(buffer, 0, read)
            if (out.size() > BackupFormat.MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        }
    }

    /**
     * Replaces every record with [contents], in one transaction: if any row fails to go in, the
     * database is exactly as it was. Does nothing while something is in progress. Cat League
     * bests and unlocks are then brought in line with the imported results, as at every app start.
     */
    suspend fun replaceAll(contents: BackupContents): ReplaceResult {
        val blocked = db.withTransaction {
            val busy = inProgressLocked()
            if (busy.isNotEmpty()) return@withTransaction busy
            // Children before the rows they point at.
            dao.clearScheduleBlocks()
            dao.clearSchedules()
            dao.clearTimeEntries()
            dao.clearPersonalBests()
            dao.clearExerciseSessions()
            dao.clearQuietSessions()
            dao.clearPriorities()
            dao.clearDailyRecords()

            contents.distractionFlags.forEach { (id, flag) -> dao.setDistraction(id, flag) }
            dao.updateLevels(contents.levels)
            dao.insertDailyRecords(contents.dailyRecords)
            dao.insertPriorities(contents.priorities)
            dao.insertExerciseSessions(contents.exerciseSessions)
            dao.insertPersonalBests(contents.personalBests)
            dao.insertQuietSessions(contents.quietSessions)
            dao.insertTimeEntries(contents.timeEntries)
            dao.insertSchedules(contents.schedules)
            dao.insertScheduleBlocks(contents.scheduleBlocks)
            exercise.applyTimedResults()
            emptySet()
        }
        if (blocked.isNotEmpty()) return ReplaceResult.Blocked(blocked)
        // Preferences cannot join a database transaction. These flags only describe the records
        // (and the observation one is worked out again from the entries), so they follow.
        preferences.restoreBackupFlags(contents.flags)
        replaced.signal()
        return ReplaceResult.Replaced(contents.summary)
    }

    companion object {
        /** Written into the file for information. */
        const val DATABASE_VERSION = 5
    }
}
