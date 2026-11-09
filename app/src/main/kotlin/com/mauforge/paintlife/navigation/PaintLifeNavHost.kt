package com.mauforge.paintlife.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.mauforge.paintlife.exercise.ExerciseHistoryScreen
import com.mauforge.paintlife.exercise.ExerciseSafetyScreen
import com.mauforge.paintlife.exercise.ExerciseScreen
import com.mauforge.paintlife.exercise.ExerciseSessionScreen
import com.mauforge.paintlife.mytime.MyTimeScreen
import com.mauforge.paintlife.progress.ProgressScreen
import com.mauforge.paintlife.quiet.QuietScreen
import com.mauforge.paintlife.today.TodayScreen

@Composable
fun PaintLifeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        modifier = modifier,
    ) {
        composable<TodayRoute> {
            TodayScreen(onStartExercise = { navController.openExerciseSession() })
        }
        navigation<ExerciseGraph>(startDestination = ExerciseRoute) {
            composable<ExerciseRoute> {
                ExerciseScreen(
                    onOpenSession = { navController.navigate(ExerciseSessionRoute) { launchSingleTop = true } },
                    onOpenSafety = { navController.navigate(ExerciseSafetyRoute) { launchSingleTop = true } },
                    onOpenHistory = { navController.navigate(ExerciseHistoryRoute) { launchSingleTop = true } },
                )
            }
            composable<ExerciseSessionRoute> {
                ExerciseSessionScreen(onClose = { navController.popIfOn<ExerciseSessionRoute>() })
            }
            composable<ExerciseSafetyRoute> {
                ExerciseSafetyScreen(onDone = { navController.popIfOn<ExerciseSafetyRoute>() })
            }
            composable<ExerciseHistoryRoute> { ExerciseHistoryScreen() }
        }
        composable<QuietRoute> { QuietScreen() }
        composable<MyTimeRoute> { MyTimeScreen() }
        composable<ProgressRoute> { ProgressScreen() }
    }
}

/** Today's quick action: the Exercise tab, then straight into the counter (resuming an open session). */
private fun NavHostController.openExerciseSession() {
    navigateToTopLevel(TopLevelDestination.EXERCISE)
    navigate(ExerciseSessionRoute) { launchSingleTop = true }
}

/** Pops only if [T] is on top, so a late or repeated close never pops another screen. */
private inline fun <reified T : Any> NavHostController.popIfOn() {
    if (currentBackStackEntry?.destination?.hasRoute<T>() == true) popBackStack()
}
