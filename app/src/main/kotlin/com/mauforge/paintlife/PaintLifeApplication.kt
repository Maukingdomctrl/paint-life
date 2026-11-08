package com.mauforge.paintlife

import android.app.Application
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PaintLifeApplication : Application() {

    @Inject lateinit var userPreferences: UserPreferencesRepository

    @Inject lateinit var timeProvider: TimeProvider

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            userPreferences.recordFirstLaunchIfAbsent(timeProvider.today())
        }
    }
}
