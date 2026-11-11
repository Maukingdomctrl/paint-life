package com.mauforge.paintlife.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The flags that travel in a backup: the ones that describe the user's progress. Phone-specific
 * ones (notification permission asked, sleep card dismissed today) stay on the phone.
 */
data class BackupFlags(
    val firstLaunchDate: LocalDate?,
    val exerciseSafetyRead: Boolean,
    val victorySeenUpTo: Instant,
    val observationComplete: Boolean,
    val insightsPeriod: String?,
    val progressPeriod: String?,
    /** The bedtime reminder's minute of the day (0-1439); null = off. */
    val bedtimeReminderMinute: Int? = null,
)

/** What the reminder engine keeps between runs. */
data class ReminderState(
    /** Reminders due up to this moment have been dealt with; null before the first run. */
    val handledUpTo: Instant?,
    val bedtimeMinute: Int?,
    val bedtimeSetAt: Instant?,
)

/** Small app preferences. Records, not settings, belong in Room. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val data: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    /** Local date of the first launch; My Time's 7-day observation period starts here. */
    val firstLaunchDate: Flow<LocalDate?> = data.map { prefs ->
        prefs[FIRST_LAUNCH_DATE]?.let(LocalDate::parse)
    }

    /** Stores [today] only if no first-launch date exists yet; returns the stored date. */
    suspend fun recordFirstLaunchIfAbsent(today: LocalDate): LocalDate {
        var stored = today
        dataStore.edit { prefs ->
            val existing = prefs[FIRST_LAUNCH_DATE]
            if (existing == null) {
                prefs[FIRST_LAUNCH_DATE] = today.toString()
            } else {
                stored = LocalDate.parse(existing)
            }
        }
        return stored
    }

    /** True once the exercise safety guidance has been read ("I've read this"). */
    val hasReadExerciseSafety: Flow<Boolean> = data.map { prefs -> prefs[EXERCISE_SAFETY_READ] == true }

    suspend fun markExerciseSafetyRead() {
        dataStore.edit { prefs -> prefs[EXERCISE_SAFETY_READ] = true }
    }

    /** Wins that ended at or before this instant have had their victory moment. */
    val victorySeenUpTo: Flow<Instant> = data.map { prefs -> Instant.ofEpochMilli(prefs[VICTORY_SEEN_UP_TO] ?: 0L) }

    /** Records that the victory for a win that ended at [endedAt] has been shown. Never moves back. */
    suspend fun markVictorySeen(endedAt: Instant) {
        dataStore.edit { prefs ->
            val current = prefs[VICTORY_SEEN_UP_TO] ?: 0L
            prefs[VICTORY_SEEN_UP_TO] = maxOf(current, endedAt.toEpochMilli())
        }
    }

    /** True once Quiet Time has asked for the notification permission; it never asks again. */
    val quietNotificationsAsked: Flow<Boolean> = data.map { prefs -> prefs[QUIET_NOTIFICATIONS_ASKED] == true }

    suspend fun markQuietNotificationsAsked() {
        dataStore.edit { prefs -> prefs[QUIET_NOTIFICATIONS_ASKED] = true }
    }

    /** True once My Time's observation week is over (7 days with an hour recorded); never reset. */
    val myTimeObservationComplete: Flow<Boolean> = data.map { prefs -> prefs[MY_TIME_OBSERVATION_COMPLETE] == true }

    suspend fun markMyTimeObservationComplete() {
        dataStore.edit { prefs -> prefs[MY_TIME_OBSERVATION_COMPLETE] = true }
    }

    /** The local date the "Log last night's sleep" card was last dismissed on. */
    val sleepCardDismissedOn: Flow<LocalDate?> = data.map { prefs -> prefs[SLEEP_CARD_DISMISSED_ON]?.let(LocalDate::parse) }

    suspend fun dismissSleepCard(date: LocalDate) {
        dataStore.edit { prefs -> prefs[SLEEP_CARD_DISMISSED_ON] = date.toString() }
    }

    /** The name of My Time's insights period ("Week" or "FourWeeks"), null until chosen. */
    val myTimeInsightsPeriod: Flow<String?> = data.map { prefs -> prefs[MY_TIME_INSIGHTS_PERIOD] }

    suspend fun setMyTimeInsightsPeriod(name: String) {
        dataStore.edit { prefs -> prefs[MY_TIME_INSIGHTS_PERIOD] = name }
    }

    /** The name of the Progress period last chosen, null until chosen. */
    val progressPeriod: Flow<String?> = data.map { prefs -> prefs[PROGRESS_PERIOD] }

    suspend fun setProgressPeriod(name: String) {
        dataStore.edit { prefs -> prefs[PROGRESS_PERIOD] = name }
    }

    /** The bedtime reminder's minute of the day; null = off. */
    val bedtimeReminderMinute: Flow<Int?> = data.map { prefs -> prefs[BEDTIME_REMINDER_MINUTE] }.distinctUntilChanged()

    /** Sets the bedtime reminder ([minute] null = off). [at] is now: a bedtime already past today waits for tomorrow. */
    suspend fun setBedtimeReminder(minute: Int?, at: Instant) {
        dataStore.edit { prefs ->
            if (minute == null) prefs.remove(BEDTIME_REMINDER_MINUTE) else prefs[BEDTIME_REMINDER_MINUTE] = minute.coerceIn(0, MINUTES_PER_DAY - 1)
            prefs[BEDTIME_REMINDER_SET_AT] = at.toEpochMilli()
        }
    }

    suspend fun reminderState(): ReminderState {
        val prefs = data.first()
        return ReminderState(
            handledUpTo = prefs[REMINDERS_HANDLED_UP_TO]?.let(Instant::ofEpochMilli),
            bedtimeMinute = prefs[BEDTIME_REMINDER_MINUTE],
            bedtimeSetAt = prefs[BEDTIME_REMINDER_SET_AT]?.let(Instant::ofEpochMilli),
        )
    }

    /** Reminders due up to [instant] have been shown or dropped. Phone-specific: not backed up. */
    suspend fun setRemindersHandledUpTo(instant: Instant) {
        dataStore.edit { prefs -> prefs[REMINDERS_HANDLED_UP_TO] = instant.toEpochMilli() }
    }

    /** The progress flags as they are now, for a backup. */
    suspend fun backupFlags(): BackupFlags {
        val prefs = data.first()
        return BackupFlags(
            firstLaunchDate = prefs[FIRST_LAUNCH_DATE]?.let(LocalDate::parse),
            exerciseSafetyRead = prefs[EXERCISE_SAFETY_READ] == true,
            victorySeenUpTo = Instant.ofEpochMilli(prefs[VICTORY_SEEN_UP_TO] ?: 0L),
            observationComplete = prefs[MY_TIME_OBSERVATION_COMPLETE] == true,
            insightsPeriod = prefs[MY_TIME_INSIGHTS_PERIOD],
            progressPeriod = prefs[PROGRESS_PERIOD],
            bedtimeReminderMinute = prefs[BEDTIME_REMINDER_MINUTE],
        )
    }

    /**
     * Replaces the progress flags with those of an imported backup, in one write. Unlike the
     * everyday setters this can move a flag back, because the records they describe were replaced
     * too. A backup without a first-launch date keeps this phone's. [at] is now (a restored
     * bedtime reminder already past today waits for tomorrow).
     */
    suspend fun restoreBackupFlags(flags: BackupFlags, at: Instant = Instant.now()) {
        dataStore.edit { prefs ->
            flags.firstLaunchDate?.let { prefs[FIRST_LAUNCH_DATE] = it.toString() }
            prefs[EXERCISE_SAFETY_READ] = flags.exerciseSafetyRead
            prefs[VICTORY_SEEN_UP_TO] = flags.victorySeenUpTo.toEpochMilli()
            prefs[MY_TIME_OBSERVATION_COMPLETE] = flags.observationComplete
            if (flags.insightsPeriod == null) prefs.remove(MY_TIME_INSIGHTS_PERIOD) else prefs[MY_TIME_INSIGHTS_PERIOD] = flags.insightsPeriod
            if (flags.progressPeriod == null) prefs.remove(PROGRESS_PERIOD) else prefs[PROGRESS_PERIOD] = flags.progressPeriod
            val bedtime = flags.bedtimeReminderMinute?.takeIf { it in 0 until MINUTES_PER_DAY }
            if (bedtime == null) prefs.remove(BEDTIME_REMINDER_MINUTE) else prefs[BEDTIME_REMINDER_MINUTE] = bedtime
            prefs[BEDTIME_REMINDER_SET_AT] = at.toEpochMilli()
        }
    }

    private companion object {
        val FIRST_LAUNCH_DATE = stringPreferencesKey("first_launch_date")
        val EXERCISE_SAFETY_READ = booleanPreferencesKey("exercise_safety_read")
        val VICTORY_SEEN_UP_TO = longPreferencesKey("victory_seen_up_to")
        val QUIET_NOTIFICATIONS_ASKED = booleanPreferencesKey("quiet_notifications_asked")
        val MY_TIME_OBSERVATION_COMPLETE = booleanPreferencesKey("my_time_observation_complete")
        val SLEEP_CARD_DISMISSED_ON = stringPreferencesKey("sleep_card_dismissed_on")
        val MY_TIME_INSIGHTS_PERIOD = stringPreferencesKey("my_time_insights_period")
        val PROGRESS_PERIOD = stringPreferencesKey("progress_period")
        val BEDTIME_REMINDER_MINUTE = intPreferencesKey("bedtime_reminder_minute")
        val BEDTIME_REMINDER_SET_AT = longPreferencesKey("bedtime_reminder_set_at")
        val REMINDERS_HANDLED_UP_TO = longPreferencesKey("reminders_handled_up_to")
        const val MINUTES_PER_DAY = 24 * 60
    }
}
