package com.mauforge.paintlife.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes for the five top-level destinations.

@Serializable
data object TodayRoute

@Serializable
data object ExerciseRoute

@Serializable
data object QuietRoute

@Serializable
data object MyTimeRoute

@Serializable
data object ProgressRoute
