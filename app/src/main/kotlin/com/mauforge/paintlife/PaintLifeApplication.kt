package com.mauforge.paintlife

import android.app.Application
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.di.ApplicationScope
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.exercise.ExerciseSessionController
import com.mauforge.paintlife.mytime.ReminderEngine
import com.mauforge.paintlife.quiet.QuietSessionController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PaintLifeApplication : Application() {

    @Inject lateinit var userPreferences: UserPreferencesRepository

    @Inject lateinit var timeProvider: TimeProvider

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    /**
     * Created with the process, so a session left running when the app was closed or the phone
     * restarted is restored as paused straight away (see ExerciseSessionController).
     */
    @Inject lateinit var exerciseSessions: ExerciseSessionController

    /**
     * Created with the process, so a quiet session left open by a process that died (Android
     * closed the app, or the phone restarted) is saved as incomplete straight away.
     */
    @Inject lateinit var quietSessions: QuietSessionController

    /** Sets the reminder alarm again with every process start, and whenever a plan or reminder changes. */
    @Inject lateinit var reminders: ReminderEngine

    override fun onCreate() {
        super.onCreate()
        reminders.start()
        applicationScope.launch {
            userPreferences.recordFirstLaunchIfAbsent(timeProvider.today())
        }
    }
}
