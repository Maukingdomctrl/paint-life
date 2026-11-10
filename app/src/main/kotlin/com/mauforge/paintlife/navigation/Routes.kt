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

/** The counter. [level] 0 continues the open session or starts step-ups; 1-10 starts that cat's challenge. */
@Serializable
data class ExerciseSessionRoute(val level: Int = 0)

@Serializable
data class ExerciseCatRoute(val level: Int)

/** The result of one timed challenge (victory or "1,000 reps done"). */
@Serializable
data class ExerciseResultRoute(val sessionId: String)

@Serializable
data object ExerciseSafetyRoute

@Serializable
data object ExerciseHistoryRoute

@Serializable
data object QuietRoute

/** The My Time tab's own back stack: the timeline, insights and the schedule proposal. */
@Serializable
data object MyTimeGraph

@Serializable
data object MyTimeRoute

@Serializable
data object MyTimeInsightsRoute

/** "Suggest a schedule": a proposal that is saved only when approved. */
@Serializable
data object MyTimeScheduleRoute

@Serializable
data object ProgressRoute
