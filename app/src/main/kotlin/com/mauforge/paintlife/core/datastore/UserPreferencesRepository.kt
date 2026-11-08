package com.mauforge.paintlife.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
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

    private companion object {
        val FIRST_LAUNCH_DATE = stringPreferencesKey("first_launch_date")
    }
}
