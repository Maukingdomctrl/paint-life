package com.mauforge.paintlife

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.mytime.ReminderNotifications
import com.mauforge.paintlife.mytime.ReminderStart
import com.mauforge.paintlife.mytime.StartAction
import com.mauforge.paintlife.mytime.StartDestination
import com.mauforge.paintlife.navigation.NavRequest
import com.mauforge.paintlife.navigation.PaintLifeAppShell
import com.mauforge.paintlife.exercise.ExerciseSessionController
import com.mauforge.paintlife.quiet.QuietNotifications
import com.mauforge.paintlife.quiet.QuietSessionController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var exerciseSessions: ExerciseSessionController

    @Inject lateinit var quietSessions: QuietSessionController

    @Inject lateinit var reminderStart: ReminderStart

    /** Counts taps on Quiet Time notifications; each new value opens the Quiet tab. */
    private var openQuietRequest by mutableIntStateOf(0)

    /** Where a tapped reminder leads; each new token is followed once. */
    private var navRequest by mutableStateOf<NavRequest?>(null)
    private var navToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark only, so system bar icons stay light even when the device is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // The bottom bar draws its own background behind the gesture/3-button area; no system scrim.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PaintLifeTheme {
                PaintLifeAppShell(openQuietRequest = openQuietRequest, navRequest = navRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(QuietNotifications.EXTRA_OPEN_QUIET, false) == true) openQuietRequest++
        if (intent != null) handleReminder(intent)
    }

    /** A tap on a reminder: its notification goes, and "Start now" starts what it names. */
    private fun handleReminder(intent: Intent) {
        if (!intent.hasExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID)) return
        // Reopened from the recent-apps list: the tap was dealt with the first time.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        ReminderNotifications.cancel(this, intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0))
        val start = intent.getStringExtra(ReminderNotifications.EXTRA_START)?.let { name -> StartAction.entries.firstOrNull { it.name == name } }
        val category = intent.getStringExtra(ReminderNotifications.EXTRA_CATEGORY)
        val note = intent.getStringExtra(ReminderNotifications.EXTRA_NOTE)
        val open = intent.getStringExtra(ReminderNotifications.EXTRA_OPEN)
        // Handled once: a later re-delivery of the same intent starts nothing.
        intent.removeExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID)
        if (start != null) {
            lifecycleScope.launch { navRequest = NavRequest(++navToken, reminderStart.start(start, category, note)) }
        } else if (open != null) {
            val destination = if (open == ReminderNotifications.OPEN_TODAY) StartDestination.Today else StartDestination.MyTime
            navRequest = NavRequest(++navToken, destination)
        }
    }

    /** Leaving the app saves running sessions' active time, in case Android closes the app later. */
    override fun onStop() {
        exerciseSessions.checkpoint()
        quietSessions.checkpoint()
        super.onStop()
    }
}
