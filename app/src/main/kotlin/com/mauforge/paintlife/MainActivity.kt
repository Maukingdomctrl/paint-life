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
import androidx.compose.runtime.setValue
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.navigation.PaintLifeAppShell
import com.mauforge.paintlife.exercise.ExerciseSessionController
import com.mauforge.paintlife.quiet.QuietNotifications
import com.mauforge.paintlife.quiet.QuietSessionController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var exerciseSessions: ExerciseSessionController

    @Inject lateinit var quietSessions: QuietSessionController

    /** Counts taps on Quiet Time notifications; each new value opens the Quiet tab. */
    private var openQuietRequest by mutableIntStateOf(0)

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
                PaintLifeAppShell(openQuietRequest = openQuietRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(QuietNotifications.EXTRA_OPEN_QUIET, false) == true) openQuietRequest++
    }

    /** Leaving the app saves running sessions' active time, in case Android closes the app later. */
    override fun onStop() {
        exerciseSessions.checkpoint()
        quietSessions.checkpoint()
        super.onStop()
    }
}
