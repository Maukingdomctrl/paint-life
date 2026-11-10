package com.mauforge.paintlife.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.mauforge.paintlife.exercise.CatDetailScreen
import com.mauforge.paintlife.exercise.ChallengeResultScreen
import com.mauforge.paintlife.exercise.ExerciseHistoryScreen
import com.mauforge.paintlife.exercise.ExerciseSafetyScreen
import com.mauforge.paintlife.exercise.ExerciseScreen
import com.mauforge.paintlife.exercise.ExerciseSessionScreen
import com.mauforge.paintlife.mytime.InsightsScreen
import com.mauforge.paintlife.mytime.MyTimeScreen
import com.mauforge.paintlife.mytime.ScheduleScreen
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
            TodayScreen(
                onStartExercise = { navController.openExerciseSession() },
                onOpenQuiet = { navController.navigateToTopLevel(TopLevelDestination.QUIET) },
                onOpenMyTime = { navController.navigateToTopLevel(TopLevelDestination.MY_TIME) },
            )
        }
        navigation<ExerciseGraph>(startDestination = ExerciseRoute) {
            composable<ExerciseRoute> {
                ExerciseScreen(
                    onOpenSession = { navController.navigate(ExerciseSessionRoute()) { launchSingleTop = true } },
                    onStartTimedChallenge = { level -> navController.navigate(ExerciseSessionRoute(level)) { launchSingleTop = true } },
                    onOpenCat = { level -> navController.navigate(ExerciseCatRoute(level)) { launchSingleTop = true } },
                    onOpenResult = { id -> navController.navigate(ExerciseResultRoute(id)) { launchSingleTop = true } },
                    onOpenSafety = { navController.navigate(ExerciseSafetyRoute) { launchSingleTop = true } },
                    onOpenHistory = { navController.navigate(ExerciseHistoryRoute) { launchSingleTop = true } },
                )
            }
            composable<ExerciseCatRoute> {
                CatDetailScreen(
                    onStartTimedChallenge = { level -> navController.navigate(ExerciseSessionRoute(level)) { launchSingleTop = true } },
                )
            }
            composable<ExerciseSessionRoute> {
                ExerciseSessionScreen(
                    onClose = { navController.popIfOn<ExerciseSessionRoute>() },
                    // The result replaces the counter, so Back from it returns to where the challenge started.
                    onShowResult = { id ->
                        if (navController.currentBackStackEntry?.destination?.hasRoute<ExerciseSessionRoute>() == true) {
                            navController.navigate(ExerciseResultRoute(id)) {
                                popUpTo<ExerciseSessionRoute> { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<ExerciseResultRoute> {
                ChallengeResultScreen(onContinue = { navController.popIfOn<ExerciseResultRoute>() })
            }
            composable<ExerciseSafetyRoute> {
                ExerciseSafetyScreen(onDone = { navController.popIfOn<ExerciseSafetyRoute>() })
            }
            composable<ExerciseHistoryRoute> { ExerciseHistoryScreen() }
        }
        composable<QuietRoute> { QuietScreen() }
        navigation<MyTimeGraph>(startDestination = MyTimeRoute) {
            composable<MyTimeRoute> {
                MyTimeScreen(
                    onOpenInsights = { navController.navigate(MyTimeInsightsRoute) { launchSingleTop = true } },
                    onSuggestSchedule = { navController.navigate(MyTimeScheduleRoute) { launchSingleTop = true } },
                )
            }
            composable<MyTimeInsightsRoute> {
                InsightsScreen(onSuggestSchedule = { navController.navigate(MyTimeScheduleRoute) { launchSingleTop = true } })
            }
            composable<MyTimeScheduleRoute> {
                ScheduleScreen(onDone = { navController.popIfOn<MyTimeScheduleRoute>() })
            }
        }
        composable<ProgressRoute> { ProgressScreen() }
    }
}

/** Today's quick action: the Exercise tab, then straight into the counter (resuming an open session). */
private fun NavHostController.openExerciseSession() {
    navigateToTopLevel(TopLevelDestination.EXERCISE)
    navigate(ExerciseSessionRoute()) { launchSingleTop = true }
}

/** Pops only if [T] is on top, so a late or repeated close never pops another screen. */
private inline fun <reified T : Any> NavHostController.popIfOn() {
    if (currentBackStackEntry?.destination?.hasRoute<T>() == true) popBackStack()
}
