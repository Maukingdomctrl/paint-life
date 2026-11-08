package com.mauforge.paintlife.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mauforge.paintlife.R

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    TODAY(TodayRoute, R.string.nav_today, R.drawable.ic_nav_today),
    EXERCISE(ExerciseRoute, R.string.nav_exercise, R.drawable.ic_nav_exercise),
    QUIET(QuietRoute, R.string.nav_quiet, R.drawable.ic_nav_quiet),
    MY_TIME(MyTimeRoute, R.string.nav_my_time, R.drawable.ic_nav_my_time),
    PROGRESS(ProgressRoute, R.string.nav_progress, R.drawable.ic_nav_progress),
}
