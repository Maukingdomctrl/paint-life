package com.mauforge.paintlife

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.navigation.PaintLifeAppShell
import com.mauforge.paintlife.exercise.ExerciseSessionController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var exerciseSessions: ExerciseSessionController

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
        setContent {
            PaintLifeTheme {
                PaintLifeAppShell()
            }
        }
    }

    /** Leaving the app saves a running session's active time, in case Android closes the app later. */
    override fun onStop() {
        exerciseSessions.checkpoint()
        super.onStop()
    }
}
