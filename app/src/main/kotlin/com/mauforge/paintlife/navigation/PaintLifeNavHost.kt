package com.mauforge.paintlife.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mauforge.paintlife.exercise.ExerciseScreen
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
        composable<TodayRoute> { TodayScreen() }
        composable<ExerciseRoute> { ExerciseScreen() }
        composable<QuietRoute> { QuietScreen() }
        composable<MyTimeRoute> { MyTimeScreen() }
        composable<ProgressRoute> { ProgressScreen() }
    }
}
