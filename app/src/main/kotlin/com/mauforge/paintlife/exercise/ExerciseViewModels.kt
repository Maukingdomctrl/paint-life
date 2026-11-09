package com.mauforge.paintlife.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.exercise.cats.CatRoster
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

/** The ladder, kept current from the stored levels and best times. */
internal fun ExerciseRepository.observeLeague(): Flow<List<LeagueEntry>> =
    combine(observeLevels(), observeTimedBests(), League::entries)

/**
 * The Exercise tab: lifetime volume, the open session (if any), the Cat League, and a win whose
 * victory moment has not been shown yet ([pendingVictory]; for example one applied at app start
 * after the app was closed during the undo window).
 */
@HiltViewModel
class ExerciseViewModel @Inject constructor(
    repository: ExerciseRepository,
    private val controller: ExerciseSessionController,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val session = combine(controller.live, controller.loaded, displayTicks(TAB_TICK_MS)) { live, loaded, _ ->
        loaded to live?.let {
            ActiveSessionSummary(
                type = it.session.type,
                reps = it.counter.reps,
                activeMillis = controller.activeMillis(it),
                isPaused = it.isPaused,
                opponentLevel = it.session.challengeLevel,
                startedOn = it.session.startDate.takeIf { date -> date != time.today() },
            )
        }
    }

    val state: StateFlow<ExerciseUiState?> = combine(
        repository.observeLifetimeReps(),
        repository.observeLeague(),
        session,
        preferences.hasReadExerciseSafety,
    ) { lifetime, league, (loaded, active), hasReadSafety ->
        if (!loaded) return@combine null
        ExerciseUiState(lifetimeReps = lifetime, league = league, activeSession = active, hasReadSafety = hasReadSafety)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Id of a winning session whose victory hasn't been shown; the tab opens it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingVictory: StateFlow<String?> = combine(controller.loaded, controller.finishWindow, ::Pair)
        .flatMapLatest { (loaded, window) ->
            // Not before results are reconciled, and not while a result can still be undone.
            if (!loaded || window != null) {
                flowOf(null)
            } else {
                preferences.victorySeenUpTo.flatMapLatest { seen -> repository.observeLatestWinAfter(seen) }.map { it?.id }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val TAB_TICK_MS = 1_000L
    }
}

/** One cat: its words, benchmark, best time, and Start / Replay when it can be challenged. */
@HiltViewModel
class CatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ExerciseRepository,
    controller: ExerciseSessionController,
) : ViewModel() {
    private val level: Int = savedStateHandle.get<Int>("level") ?: 0

    val state: StateFlow<CatDetailUi?> = combine(repository.observeLeague(), controller.live, controller.finishWindow) { league, live, window ->
        val entry = league.firstOrNull { it.cat.level == level } ?: return@combine null
        CatDetailUi(entry = entry, previous = CatRoster.forLevel(level - 1), canStart = live == null && window == null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
}

/**
 * The counter. Opening it continues the open session, or starts a step-up session ([level] 0)
 * or a timed challenge against [level]; the very first time, the safety guidance comes first.
 * Counting, pausing and resuming go straight to [ExerciseSessionController], which saves them;
 * nothing here times anything.
 */
@HiltViewModel
class ExerciseSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val controller: ExerciseSessionController,
    repository: ExerciseRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val level: Int = savedStateHandle.get<Int>("level") ?: 0

    private enum class Step { Checking, Safety, Starting, Active, Closed }

    private data class Local(
        val step: Step = Step.Checking,
        val sessionId: String? = null,
        val dialog: SessionDialog? = null,
        val correcting: Boolean = false,
        val busy: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    private val source = combine(controller.live, controller.finishWindow, controller.lastResult, ::Triple)

    val state: StateFlow<ExerciseSessionScreenState> = combine(
        local,
        source,
        repository.observeLeague(),
        displayTicks(CLOCK_TICK_MS),
    ) { local, (live, window, result), league, _ ->
        val phase = when (local.step) {
            Step.Checking, Step.Starting -> SessionPhase.Loading
            Step.Safety -> SessionPhase.SafetyFirst
            Step.Closed -> SessionPhase.Closed
            Step.Active -> when {
                window != null && window.finished.id == local.sessionId -> SessionPhase.Counting(
                    content(window.finished, window.finished.repetitions, window.finished.timer.activeDurationMillis, canUndo = false, league)
                        .copy(finishing = true, isPaused = false),
                )
                live != null -> SessionPhase.Counting(
                    content(live.session, live.counter.reps, controller.activeMillis(live), live.counter.canUndo, league),
                )
                result != null && result.sessionId == local.sessionId -> SessionPhase.Result(result.sessionId)
                else -> SessionPhase.Closed
            }
        }
        val counting = (phase as? SessionPhase.Counting)?.content
        ExerciseSessionScreenState(
            phase = phase,
            dialog = local.dialog.takeIf { counting != null && !counting.finishing },
            correcting = local.correcting && counting != null && !counting.finishing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExerciseSessionScreenState())

    private fun content(session: ExerciseSessionEntity, reps: Int, elapsed: Long, canUndo: Boolean, league: List<LeagueEntry>) =
        ExerciseSessionUiState(
            type = session.type,
            reps = reps,
            elapsedMillis = elapsed,
            isPaused = session.status == SessionStatus.PAUSED,
            opponent = session.challengeLevel?.let { level -> league.firstOrNull { it.cat.level == level } },
            canUndo = canUndo,
        )

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
        // An open session always wins; a locked level gives nothing and the screen closes.
        val live = if (level > 0) controller.startTimed(level) else controller.startVolume()
        local.update { if (live == null) it.copy(step = Step.Closed) else it.copy(step = Step.Active, sessionId = live.session.id) }
    }

    fun add(amount: Int) = controller.add(amount)

    fun undo() = controller.undo()

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    /** Undo in the 5-second window after a timed challenge reached 1,000. */
    fun undoFinish() {
        viewModelScope.launch { controller.undoFinish() }
    }

    /** Finish (step-ups) or Stop challenge (timed, which asks first). */
    fun finish() {
        val live = controller.live.value ?: return
        if (live.isTimed) {
            local.update { it.copy(dialog = SessionDialog.ConfirmStop) }
            return
        }
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                when (controller.finish()) {
                    is FinishOutcome.Finished, FinishOutcome.NothingOpen -> local.update { it.copy(step = Step.Closed) }
                    FinishOutcome.NothingCounted -> local.update { it.copy(dialog = SessionDialog.DiscardEmpty) }
                }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    fun confirmStop() {
        if (local.value.dialog != SessionDialog.ConfirmStop) return
        local.update { it.copy(dialog = null) }
        viewModelScope.launch {
            if (controller.stopChallenge()) local.update { it.copy(step = Step.Closed) }
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

/**
 * The result of one timed challenge: the victory moment for a win (strictly faster than the
 * cat), otherwise a calm "1,000 reps done". Opening a victory marks it as seen, so each win's
 * moment is shown once.
 */
@HiltViewModel
class ExerciseResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()

    private val _state = MutableStateFlow<ChallengeResultUi?>(null)
    val state: StateFlow<ChallengeResultUi?> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = load() }
    }

    private suspend fun load(): ChallengeResultUi {
        val session = repository.session(sessionId)
        if (session == null || session.type != ExerciseSessionType.TIMED_CHALLENGE || session.status != SessionStatus.COMPLETED) {
            return ChallengeResultUi.Missing
        }
        val level = repository.levels().firstOrNull { it.level == session.challengeLevel } ?: return ChallengeResultUi.Missing
        val cat = CatRoster.forLevel(level.level) ?: return ChallengeResultUi.Missing
        val yourMillis = session.timer.activeDurationMillis
        val bestMillis = repository.timedBest(level.level)?.value?.coerceAtMost(yourMillis) ?: yourMillis
        val entry = LeagueEntry(cat, level.benchmarkMillis, League.status(level), bestMillis)
        if (yourMillis >= level.benchmarkMillis) {
            return ChallengeResultUi.Complete(ChallengeCompleteUiState(entry, yourMillis, bestMillis))
        }
        val firstWin = level.beatenAt == session.endedAt
        session.endedAt?.let { preferences.markVictorySeen(it) }
        return ChallengeResultUi.Victory(
            VictoryUiState(
                beaten = entry,
                yourMillis = yourMillis,
                unlocked = if (firstWin) CatRoster.forLevel(level.level + 1) else null,
                isReplay = !firstWin,
                bestMillis = bestMillis,
            ),
        )
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
                            status = session.status,
                            opponent = session.challengeLevel?.let(CatRoster::forLevel),
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
