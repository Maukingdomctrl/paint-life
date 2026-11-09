package com.mauforge.paintlife.exercise

import androidx.compose.runtime.Immutable
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import com.mauforge.paintlife.exercise.cats.CatProfile
import java.time.LocalDate
import java.time.LocalTime

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
    /** The day the session started, when that was not today. */
    val startedOn: LocalDate? = null,
)

/** The Exercise tab, built by ExerciseViewModel. */
@Immutable
data class ExerciseUiState(
    /** All volume reps across every session, ever. Separate from timed results. */
    val lifetimeReps: Int,
    val league: List<LeagueEntry>,
    val activeSession: ActiveSessionSummary?,
    /** False until the user has read the safety guidance once; it is then shown collapsed. */
    val hasReadSafety: Boolean,
    val volumeGoal: Int = VOLUME_GOAL,
    /** False until the Cat League is wired to real data; the tab then says so instead of showing it. */
    val leagueReady: Boolean = true,
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
    /** Whether Undo has an action to revert. */
    val canUndo: Boolean = reps > 0,
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

/** What the counter route is showing. */
sealed interface SessionPhase {
    data object Loading : SessionPhase

    /** First session ever: the safety guidance comes first, then the session starts. */
    data object SafetyFirst : SessionPhase

    data class Counting(val content: ExerciseSessionUiState) : SessionPhase

    /** The session was finished or discarded; the screen closes. */
    data object Closed : SessionPhase
}

enum class SessionDialog { ConfirmReset, DiscardEmpty }

@Immutable
data class ExerciseSessionScreenState(
    val phase: SessionPhase = SessionPhase.Loading,
    val dialog: SessionDialog? = null,
    val correcting: Boolean = false,
) {
    /** The screen stays on only while counting and not paused. */
    val keepScreenOn: Boolean get() = (phase as? SessionPhase.Counting)?.content?.isPaused == false
}

/** One finished session in history. */
@Immutable
data class HistoryRow(
    val id: String,
    val type: ExerciseSessionType,
    val startTime: LocalTime,
    val reps: Int,
    val activeMillis: Long,
    val note: String?,
)

/** Finished sessions that started on [date], newest first. */
@Immutable
data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>) {
    val reps: Int get() = rows.sumOf { it.reps }
}

@Immutable
data class ExerciseHistoryUiState(val today: LocalDate, val days: List<HistoryDay>)

/** The edit sheet for one finished session. */
@Immutable
data class HistoryEditorUi(
    val id: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val reps: Int,
    val note: String,
    /** Step-up sessions only; a timed result stays as achieved. */
    val canEditReps: Boolean,
    val canDelete: Boolean,
    val saving: Boolean = false,
)

@Immutable
data class ExerciseHistoryScreenState(
    val content: ExerciseHistoryUiState? = null,
    val editor: HistoryEditorUi? = null,
    val confirmingDelete: Boolean = false,
)
