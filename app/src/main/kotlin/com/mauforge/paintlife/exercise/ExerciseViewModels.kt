package com.mauforge.paintlife.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Redraw ticks for a running clock. A tick carries no time: each one only makes the screen read
 * the monotonic clock again, so a late or skipped tick can never make a duration wrong.
 */
internal fun displayTicks(periodMillis: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMillis)
    }
}

/** The Exercise tab: lifetime volume, the open session (if any) and the way into a session. */
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    repository: ExerciseRepository,
    private val controller: ExerciseSessionController,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val state: StateFlow<ExerciseUiState?> = combine(
        repository.observeLifetimeReps(),
        controller.live,
        controller.loaded,
        preferences.hasReadExerciseSafety,
        displayTicks(TAB_TICK_MS),
    ) { lifetime, live, loaded, hasReadSafety, _ ->
        if (!loaded) return@combine null
        ExerciseUiState(
            lifetimeReps = lifetime,
            // The Cat League is wired in the second part of Iteration 4.
            league = emptyList(),
            activeSession = live?.let {
                ActiveSessionSummary(
                    type = it.session.type,
                    reps = it.counter.reps,
                    activeMillis = controller.activeMillis(it),
                    isPaused = it.isPaused,
                    opponentLevel = it.session.challengeLevel,
                    startedOn = it.session.startDate.takeIf { date -> date != time.today() },
                )
            },
            hasReadSafety = hasReadSafety,
            leagueReady = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val TAB_TICK_MS = 1_000L
    }
}

/**
 * The counter. Opening it continues the open session or starts a step-up session; the very first
 * time, the safety guidance is shown first. Counting, pausing and resuming go straight to
 * [ExerciseSessionController], which saves them; nothing here times anything.
 */
@HiltViewModel
class ExerciseSessionViewModel @Inject constructor(
    private val controller: ExerciseSessionController,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private enum class Step { Checking, Safety, Starting, Active, Closed }

    private data class Local(
        val step: Step = Step.Checking,
        val dialog: SessionDialog? = null,
        val correcting: Boolean = false,
        val finishing: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ExerciseSessionScreenState> = combine(local, controller.live, displayTicks(CLOCK_TICK_MS)) { local, live, _ ->
        val phase = when (local.step) {
            Step.Checking, Step.Starting -> SessionPhase.Loading
            Step.Safety -> SessionPhase.SafetyFirst
            Step.Closed -> SessionPhase.Closed
            Step.Active -> if (live == null) {
                SessionPhase.Closed
            } else {
                SessionPhase.Counting(
                    ExerciseSessionUiState(
                        type = live.session.type,
                        reps = live.counter.reps,
                        elapsedMillis = controller.activeMillis(live),
                        isPaused = live.isPaused,
                        opponent = null,
                        canUndo = live.counter.canUndo,
                    ),
                )
            }
        }
        val counting = phase is SessionPhase.Counting
        ExerciseSessionScreenState(
            phase = phase,
            dialog = local.dialog.takeIf { counting },
            correcting = local.correcting && counting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExerciseSessionScreenState())

    init {
        viewModelScope.launch {
            controller.loaded.first { it }
            val resuming = controller.live.value != null
            if (!resuming && !preferences.hasReadExerciseSafety.first()) {
                local.update { it.copy(step = Step.Safety) }
            } else {
                begin()
            }
        }
    }

    /** "I've read this" on the first-session guidance: remember it, then start. */
    fun safetyRead() {
        if (local.value.step != Step.Safety) return
        local.update { it.copy(step = Step.Starting) }
        viewModelScope.launch {
            preferences.markExerciseSafetyRead()
            begin()
        }
    }

    private suspend fun begin() {
        local.update { it.copy(step = Step.Starting) }
        controller.startVolume()
        local.update { it.copy(step = Step.Active) }
    }

    fun add(amount: Int) = controller.add(amount)

    fun undo() = controller.undo()

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun finish() {
        if (local.value.finishing) return
        local.update { it.copy(finishing = true) }
        viewModelScope.launch {
            try {
                when (controller.finish()) {
                    is FinishOutcome.Finished, FinishOutcome.NothingOpen -> local.update { it.copy(step = Step.Closed) }
                    FinishOutcome.NothingCounted -> local.update { it.copy(dialog = SessionDialog.DiscardEmpty) }
                }
            } finally {
                local.update { it.copy(finishing = false) }
            }
        }
    }

    fun discard() {
        if (local.value.dialog != SessionDialog.DiscardEmpty) return
        local.update { it.copy(dialog = null) }
        viewModelScope.launch {
            if (controller.discard()) local.update { it.copy(step = Step.Closed) }
        }
    }

    fun requestReset() {
        local.update { it.copy(dialog = SessionDialog.ConfirmReset) }
    }

    fun confirmReset() {
        if (local.value.dialog != SessionDialog.ConfirmReset) return
        local.update { it.copy(dialog = null) }
        controller.reset()
    }

    fun dismissDialog() {
        local.update { it.copy(dialog = null) }
    }

    fun openCorrection() {
        local.update { it.copy(correcting = true) }
    }

    fun closeCorrection() {
        local.update { it.copy(correcting = false) }
    }

    fun correct(reps: Int) {
        if (controller.correct(reps)) closeCorrection()
    }

    private companion object {
        /** Four redraws a second keep the seconds digit in step with the real time. */
        const val CLOCK_TICK_MS = 250L
    }
}

/** Finished sessions grouped by the day they started, with edit and delete. */
@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    private val repository: ExerciseRepository,
    dayClock: DayClock,
) : ViewModel() {

    private data class Local(val editingId: String? = null, val saving: Boolean = false, val confirmingDelete: Boolean = false)

    private val local = MutableStateFlow(Local())

    private val today = dayClock.moments.map { it.date }

    val state: StateFlow<ExerciseHistoryScreenState> = combine(repository.observeFinishedSessions(), today, local) { sessions, today, local ->
        val days = sessions
            .groupBy { it.startDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                HistoryDay(
                    date = date,
                    rows = list.map { session ->
                        HistoryRow(
                            id = session.id,
                            type = session.type,
                            startTime = session.startedAt.atZone(session.zoneId).toLocalTime(),
                            reps = session.repetitions,
                            activeMillis = session.timer.activeDurationMillis,
                            note = session.notes,
                        )
                    },
                )
            }
        // The edited session may disappear (deleted elsewhere); the sheet closes with it.
        val editing = local.editingId?.let { id -> sessions.firstOrNull { it.id == id } }
        ExerciseHistoryScreenState(
            content = ExerciseHistoryUiState(today, days),
            editor = editing?.let {
                HistoryEditorUi(
                    id = it.id,
                    date = it.startDate,
                    startTime = it.startedAt.atZone(it.zoneId).toLocalTime(),
                    reps = it.repetitions,
                    note = it.notes.orEmpty(),
                    canEditReps = it.type == ExerciseSessionType.VOLUME,
                    canDelete = it.type == ExerciseSessionType.VOLUME,
                    saving = local.saving,
                )
            },
            confirmingDelete = local.confirmingDelete && editing != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExerciseHistoryScreenState())

    fun open(id: String) {
        local.update { Local(editingId = id) }
    }

    fun close() {
        local.update { if (it.saving) it else Local() }
    }

    fun save(reps: Int, note: String) {
        val current = local.value
        val id = current.editingId ?: return
        if (current.saving) return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = repository.updateFinished(id, reps, note)
            local.update { if (saved) Local() else it.copy(saving = false) }
        }
    }

    fun requestDelete() {
        if (local.value.editingId != null) local.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDelete() {
        local.update { it.copy(confirmingDelete = false) }
    }

    fun confirmDelete() {
        val current = local.value
        val id = current.editingId ?: return
        if (!current.confirmingDelete || current.saving) return
        local.update { it.copy(saving = true, confirmingDelete = false) }
        viewModelScope.launch {
            repository.deleteFinished(id)
            local.update { Local() }
        }
    }
}

/** Marks the safety guidance as read, then lets the screen close. */
@HiltViewModel
class ExerciseSafetyViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private var saving = false

    fun markRead(onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            preferences.markExerciseSafetyRead()
            onSaved()
        }
    }
}
