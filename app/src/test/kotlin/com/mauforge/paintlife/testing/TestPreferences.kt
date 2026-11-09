package com.mauforge.paintlife.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import java.io.File
import java.util.UUID

/** Preferences in a throwaway file, so every test starts with none. */
fun testPreferences(context: Context): UserPreferencesRepository = UserPreferencesRepository(
    PreferenceDataStoreFactory.create(produceFile = { File(context.cacheDir, "prefs_${UUID.randomUUID()}.preferences_pb") }),
)
