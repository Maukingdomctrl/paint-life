package com.mauforge.paintlife.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes: the five top-level destinations and their inner screens.

@Serializable
data object TodayRoute

/** The Exercise tab's own back stack: the tab, the counter, safety guidance and history. */
@Serializable
data object ExerciseGraph

@Serializable
data object ExerciseRoute

@Serializable
data object ExerciseSessionRoute

@Serializable
data object ExerciseSafetyRoute

@Serializable
data object ExerciseHistoryRoute

@Serializable
data object QuietRoute

@Serializable
data object MyTimeRoute

@Serializable
data object ProgressRoute
