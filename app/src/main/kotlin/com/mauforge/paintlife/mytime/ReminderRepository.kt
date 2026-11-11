package com.mauforge.paintlife.mytime

import androidx.room.withTransaction
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

sealed interface AddReminderResult {
    data class Added(val reminder: ReminderEntity) : AddReminderResult

    /** No title. */
    data object NoTitle : AddReminderResult

    /** The time is now or already past. */
    data object NotAhead : AddReminderResult
}

/**
 * What the user sets about reminders: one-off reminders and the bedtime reminder. (A planned entry's
 * and a schedule block's reminder are saved by [TimeRepository].)
 * [ReminderEngine] watches all of it and sets the alarm.
 */
class ReminderRepository @Inject constructor(
    private val db: PaintLifeDatabase,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) {
    private val dao = db.reminderDao()

    /** One-off reminders still to come, earliest first. */
    fun observeOneOffs(): Flow<List<ReminderEntity>> = dao.observeOneOffs()

    val bedtimeMinute: Flow<Int?> = preferences.bedtimeReminderMinute

    /** A one-off reminder at [at] (to the minute). [action] is what its "Start now" starts. */
    suspend fun addOneOff(title: String, at: Instant, action: StartAction): AddReminderResult {
        val clean = title.trim().replace('\n', ' ').take(ReminderRules.TITLE_MAX)
        if (clean.isEmpty()) return AddReminderResult.NoTitle
        val now = time.now()
        val fireAt = TimeRules.toMinute(at)
        if (fireAt <= now) return AddReminderResult.NotAhead
        val zone = time.zone()
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            kind = ReminderKind.ONE_OFF,
            title = clean,
            categoryId = null,
            note = null,
            fireAt = fireAt,
            zoneId = zone,
            activityStartAt = null,
            activityEndAt = null,
            // "Now doing" needs a category, which a one-off does not have.
            action = action.takeIf { it != StartAction.NOW_DOING } ?: StartAction.NONE,
            createdAt = now,
            createdZoneId = zone,
        )
        dao.insert(reminder)
        return AddReminderResult.Added(reminder)
    }

    /** Deletes a one-off at once; the returned row lets Undo put it back. */
    suspend fun delete(id: String): ReminderEntity? = db.withTransaction {
        dao.reminder(id)?.also { dao.delete(id) }
    }

    /** Undo of [delete]. False when its time has passed meanwhile (it would never fire). */
    suspend fun restore(reminder: ReminderEntity): Boolean = db.withTransaction {
        if (reminder.fireAt <= time.now() || dao.reminder(reminder.id) != null) return@withTransaction false
        dao.insert(reminder)
        true
    }

    /** The bedtime reminder: [minuteOfDay] every day, or off (null). */
    suspend fun setBedtime(minuteOfDay: Int?) {
        preferences.setBedtimeReminder(minuteOfDay, time.now())
    }
}
