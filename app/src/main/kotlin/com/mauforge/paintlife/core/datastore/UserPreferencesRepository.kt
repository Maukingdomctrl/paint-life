package com.mauforge.paintlife.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

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

    private companion object {
        val FIRST_LAUNCH_DATE = stringPreferencesKey("first_launch_date")
        val EXERCISE_SAFETY_READ = booleanPreferencesKey("exercise_safety_read")
        val VICTORY_SEEN_UP_TO = longPreferencesKey("victory_seen_up_to")
        val QUIET_NOTIFICATIONS_ASKED = booleanPreferencesKey("quiet_notifications_asked")
        val MY_TIME_OBSERVATION_COMPLETE = booleanPreferencesKey("my_time_observation_complete")
        val SLEEP_CARD_DISMISSED_ON = stringPreferencesKey("sleep_card_dismissed_on")
    }
}
