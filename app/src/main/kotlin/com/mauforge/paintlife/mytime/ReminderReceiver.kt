package com.mauforge.paintlife.mytime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mauforge.paintlife.core.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wakes [ReminderEngine]: the reminder alarm, a tap on Snooze, and the system events after which
 * alarms must be set again (restart, time-zone or clock change, app update, exact-alarm access
 * granted). Every one of them just re-plans from what is stored.
 */
class ReminderReceiver : BroadcastReceiver() {

    /** How a receiver reaches the app's singletons (Hilt cannot inject a Kotlin receiver's fields directly). */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun engine(): ReminderEngine

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val engine = dependencies.engine()
        val pending = goAsync()
        dependencies.scope().launch {
            try {
                if (intent.action == ReminderNotifications.ACTION_SNOOZE) engine.snooze(intent) else engine.sync()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mauforge.paintlife.reminder.FIRE"
    }
}
