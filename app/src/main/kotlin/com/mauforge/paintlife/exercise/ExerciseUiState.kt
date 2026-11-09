package com.mauforge.paintlife.exercise

import androidx.compose.runtime.Immutable
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import com.mauforge.paintlife.exercise.cats.CatProfile

enum class LevelStatus { Locked, Current, Beaten }

/** One rung of the Cat League ladder. */
@Immutable
data class LeagueEntry(
    val cat: CatProfile,
    val benchmarkMillis: Long,
    val status: LevelStatus,
    /** Fastest finished 1,000-rep timed challenge against this cat, if any. */
    val personalBestMillis: Long?,
) {
    val avatarState: CatAvatarState
        get() = when (status) {
            LevelStatus.Locked -> CatAvatarState.Locked
            LevelStatus.Current -> CatAvatarState.Current
            LevelStatus.Beaten -> CatAvatarState.Beaten
        }
}

/** A session that is open (running, paused, or restored as paused after the app was closed). */
@Immutable
data class ActiveSessionSummary(
    val type: ExerciseSessionType,
    val reps: Int,
    val activeMillis: Long,
    val isPaused: Boolean,
    val opponentLevel: Int?,
)

/** The Exercise tab. Built by ExerciseViewModel in Iteration 4. */
@Immutable
data class ExerciseUiState(
    /** All volume reps across every session, ever. Separate from timed results. */
    val lifetimeReps: Int,
    val league: List<LeagueEntry>,
    val activeSession: ActiveSessionSummary?,
    /** False until the user has read the safety guidance once; it is then shown collapsed. */
    val hasReadSafety: Boolean,
    val volumeGoal: Int = VOLUME_GOAL,
) {
    val currentOpponent: LeagueEntry? get() = league.firstOrNull { it.status == LevelStatus.Current }
    val levelsBeaten: Int get() = league.count { it.status == LevelStatus.Beaten }

    companion object {
        /** The first objective: 1,000 cumulative step-ups across sessions. */
        const val VOLUME_GOAL = 1_000
    }
}

/** The counter screen for a running session. */
@Immutable
data class ExerciseSessionUiState(
    val type: ExerciseSessionType,
    val reps: Int,
    /** Volume: active time without pauses. Timed: wall-clock time since start (pauses included). */
    val elapsedMillis: Long,
    val isPaused: Boolean,
    /** Set for a timed challenge. */
    val opponent: LeagueEntry?,
    val targetReps: Int = TIMED_TARGET_REPS,
) {
    val repsRemaining: Int get() = (targetReps - reps).coerceAtLeast(0)

    companion object {
        const val TIMED_TARGET_REPS = 1_000
    }
}

/** Shown once after a timed challenge that beat the cat's benchmark (strictly faster). */
@Immutable
data class VictoryUiState(
    val beaten: LeagueEntry,
    val yourMillis: Long,
    /** The cat this win unlocked; null after beating Champion. */
    val unlocked: CatProfile?,
)
