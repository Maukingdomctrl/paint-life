package com.mauforge.paintlife.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.DayMoment
import com.mauforge.paintlife.exercise.ExerciseDayTotals
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.core.backup.DataReplaced
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.CurrentActivityEntity
import com.mauforge.paintlife.mytime.DayView
import com.mauforge.paintlife.mytime.EntryDraft
import com.mauforge.paintlife.mytime.EntryWithCategory
import com.mauforge.paintlife.mytime.LogRequest
import com.mauforge.paintlife.mytime.MyTimeLaunch
import com.mauforge.paintlife.mytime.QuickActivity
import com.mauforge.paintlife.mytime.RunResult
import com.mauforge.paintlife.mytime.SaveResult
import com.mauforge.paintlife.mytime.SleepResult
import com.mauforge.paintlife.mytime.SleepTap
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.mytime.TimeRules
import com.mauforge.paintlife.mytime.TimelineGeometry
import com.mauforge.paintlife.mytime.Resolution
import com.mauforge.paintlife.mytime.activityLabel
import com.mauforge.paintlife.quiet.QuietDayTotals
import com.mauforge.paintlife.quiet.QuietRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Today. The day shown follows [DayClock]: at midnight, or when the zone or clock changes,
 * the screen moves to the new day's priorities, and earlier days are left untouched.
 *
 * All entry points run on the main thread. Each guards against repeats (a second Save while the
 * first is saving, a second Undo for the same deletion), so fast repeated taps act once.
 */
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: TodayRepository,
    private val exercise: ExerciseRepository,
    private val quiet: QuietRepository,
    private val time: TimeRepository,
    private val launch: MyTimeLaunch,
    private val preferences: UserPreferencesRepository,
    private val clock: TimeProvider,
    dayClock: DayClock,
    replaced: DataReplaced,
) : ViewModel() {

    private sealed interface Editor {
        data object New : Editor
        data class Existing(val id: String) : Editor
    }

    private data class Local(
        val editor: Editor? = null,
        val saving: Boolean = false,
        val carryOverPickerOpen: Boolean = false,
        val carryingOver: Boolean = false,
        val undo: PendingUndo? = null,
        val sleepUndo: SleepUndoUi? = null,
        val notice: TodayNotice? = null,
        val openMyTime: Long? = null,
        val sleepBusy: Boolean = false,
    )

    /** Today's recorded time, read in the current zone (re-read when the zone changes, even on the same date). */
    private data class TimeData(
        val date: LocalDate,
        val zone: ZoneId,
        val entries: List<EntryWithCategory>,
        val current: CurrentActivityEntity?,
        val categories: List<ActivityCategoryEntity>,
        val sleepLogged: Boolean,
        val sleepDismissed: Boolean,
        val sleep: Pair<Instant, Instant>,
    )

    private data class DayData(
        val date: LocalDate,
        val priorities: List<PriorityEntity>,
        val offer: CarryOverOffer?,
        val exercise: ExerciseDayTotals,
        val quiet: QuietDayTotals,
    )

    private val local = MutableStateFlow(Local())
    private var deleted: PriorityEntity? = null
    private var sleepTap: SleepTap? = null
    private var nextToken = 0L

    private val moment = dayClock.moments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), dayClock.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayData = moment.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
        combine(
            repository.observePriorities(date),
            repository.observeCarryOver(date),
            exercise.observeDayTotals(date),
            quiet.observeDay(date),
        ) { priorities, offer, exerciseTotals, quietTotals ->
            DayData(date, priorities, offer, exerciseTotals, quietTotals)
        }
    }

    /**
     * Twice a minute, for the strip and the running activity (display only). The value is not
     * used: every recomputation reads the clock again, so the screen is also right straight after
     * the clock, date or zone changes.
     */
    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timeData: Flow<TimeData> = moment.map { it.date to it.zone }.distinctUntilChanged().flatMapLatest { (date, zone) ->
        combine(
            time.observeDayEntries(date),
            combine(time.observeCurrent(), time.observeCategories(), ::Pair),
            time.observeSleepLogged(date, zone),
            preferences.sleepCardDismissedOn,
            flow { emit(time.sleepPrefill(date, zone)) },
        ) { entries, (current, categories), sleepLogged, dismissedOn, sleep ->
            TimeData(date, zone, entries, current, categories, sleepLogged, dismissedOn == date, sleep)
        }
    }

    val state: StateFlow<TodayScreenState> = combine(moment, dayData, timeData, ticks, local) { moment, data, time, _, local ->
        toScreenState(moment, data, time, clock.now(), local)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayScreenState())

    init {
        // A backup was imported: a priority deleted before it must not be put back into the new data.
        viewModelScope.launch {
            replaced.count.drop(1).collect {
                deleted = null
                sleepTap = null
                local.update { it.copy(undo = null, sleepUndo = null, editor = null, carryOverPickerOpen = false) }
            }
        }
    }

    private fun toScreenState(moment: DayMoment, data: DayData, time: TimeData, now: Instant, local: Local): TodayScreenState {
        val items = data.priorities.map { PriorityItem(it.id, it.title, it.isCompleted) }
        val freePlaces = TodayRepository.MAX_PRIORITIES_PER_DAY - items.size
        val content = TodayUiState(
            date = data.date,
            dayPart = moment.part,
            priorities = items,
            carryOver = data.offer?.takeIf { freePlaces > 0 }?.let { offer ->
                CarryOverUi(
                    sourceDate = offer.sourceDate,
                    sourceIsYesterday = offer.sourceDate == data.date.minusDays(1),
                    items = offer.candidates.map { PriorityItem(it.id, it.title, it.isCompleted) },
                    freePlaces = freePlaces,
                )
            },
            // A session counts on the day it started (open sessions included).
            exercise = ExerciseToday(reps = data.exercise.reps, sessions = data.exercise.sessions),
            // Quiet sessions count on the day they started; only finished ones add minutes.
            quiet = QuietToday(completed = data.quiet.completed, minutes = data.quiet.minutes, fill = data.quiet.fill),
            quickActions = listOf(QuickAction.StartExercise, QuickAction.QuietTime, QuickAction.LogTime, QuickAction.Sleep),
        ).withTime(time, now)
        val editor = when (val editor = local.editor) {
            null -> null
            Editor.New -> if (content.canAddPriority || local.saving) PriorityEditorUi(null, "", false, false, local.saving) else null
            is Editor.Existing -> {
                val index = items.indexOfFirst { it.id == editor.id }
                // The priority left the screen (the day changed): the sheet closes with it.
                if (index < 0) null else PriorityEditorUi(editor.id, items[index].title, index > 0, index < items.lastIndex, local.saving)
            }
        }
        return TodayScreenState(
            content = content,
            editor = editor,
            carryOverPickerOpen = local.carryOverPickerOpen && content.carryOver != null,
            undo = local.undo,
            sleepUndo = local.sleepUndo,
            notice = local.notice,
            openMyTime = local.openMyTime,
        )
    }

    /** Adds today's time: swatch, strip, running activity and the sleep card. Split at local midnight. */
    private fun TodayUiState.withTime(time: TimeData, now: Instant): TodayUiState {
        if (time.date != date) return this
        val onDay = DayView.onDay(time.entries, time.date, time.zone)
        val nowMinute = DayView.minuteOfDay(now, time.date, time.zone)
        val morning = now.atZone(time.zone).toLocalTime() < TimeRules.MORNING_ENDS
        // Offered only up to now: sleep that has not happened yet is never logged.
        val sleep = TimeRules.sleepSoFar(time.sleep, now)
        val current = time.current
        val category = current?.let { running -> time.categories.firstOrNull { it.id == running.categoryId } }
        // Sleep started with the Sleep button: its own card with Wake up, and nothing else about sleep.
        val asleep = current?.takeIf { it.categoryId == TimeRules.SLEEP_ID }
        return copy(
            quickActions = if (asleep != null) quickActions - QuickAction.Sleep else quickActions,
            sleeping = asleep?.let {
                SleepingUi(
                    // In the zone it was tapped in: the time the user saw on going to bed.
                    sinceLabel = TimeRules.wallTime(it.startAt, it.zoneId).text,
                    elapsedMinutes = Duration.between(it.startAt, now).toMinutes().coerceAtLeast(0),
                    askWakeTime = TimeRules.wakePromptDue(it.startAt, time.sleep.second, now),
                )
            },
            timeByCategory = DayView.categoryTotals(onDay, time.date, time.zone).map { CategoryMinutes(it.categoryName, it.colorKey, it.minutes.toInt()) },
            strip = DayView.stretches(onDay, time.date, time.zone, nowMinute).map { TodayStripBlock(it.startMinute, it.endMinute, it.colorKey) },
            dayLengthMinutes = TimelineGeometry.dayLengthMinutes(time.date, time.zone),
            sleepCard = if (asleep == null && morning && !time.sleepLogged && !time.sleepDismissed && sleep != null) {
                SleepCardUi(TimeRules.wallTime(sleep.first, time.zone).text, TimeRules.wallTime(sleep.second, time.zone).text)
            } else {
                null
            },
            nowDoing = if (asleep == null && current != null && category != null) {
                NowDoingToday(activityLabel(category.name, current.note), category.colorKey, Duration.between(current.startAt, now).toMinutes().coerceAtLeast(0))
            } else {
                null
            },
        )
    }

    // Time

    /** "Log time": My Time opens its sheet from where the last entry ended to now. */
    fun logTime() {
        launch.open(LogRequest.SinceLastEntry)
        openMyTime()
    }

    /** Logs last night's sleep as offered. If it overlaps something, My Time asks what to do. */
    fun logSleep() = withSleep { draft ->
        when (val result = time.save(draft, Resolution.None)) {
            is SaveResult.Saved -> notify(TodayNotice.Kind.SleepLogged)
            is SaveResult.Overlaps -> {
                launch.open(LogRequest.Draft(draft, result.fit))
                openMyTime()
            }
            SaveResult.Invalid, SaveResult.Missing, SaveResult.Future -> {
                launch.open(LogRequest.Draft(draft))
                openMyTime()
            }
        }
    }

    /** Opens last night's sleep in My Time's sheet to change the times. */
    fun adjustSleep() = withSleep { draft ->
        launch.open(LogRequest.Draft(draft))
        openMyTime()
    }

    fun dismissSleep() {
        val date = state.value.content?.date ?: return
        viewModelScope.launch { preferences.dismissSleepCard(date) }
    }

    private fun withSleep(block: suspend (EntryDraft) -> Unit) {
        val date = state.value.content?.date ?: return
        if (local.value.sleepBusy) return
        local.update { it.copy(sleepBusy = true) }
        viewModelScope.launch {
            try {
                val (start, end) = TimeRules.sleepSoFar(time.sleepPrefill(date, clock.zone()), clock.now()) ?: return@launch
                block(EntryDraft(null, TimeEntryKind.ACTUAL, TimeRules.SLEEP_ID, null, start, end))
            } finally {
                local.update { it.copy(sleepBusy = false) }
            }
        }
    }

    /** Stops the running activity. If it needs the user (very long, or overlapping), My Time asks. */
    fun stopNow() {
        viewModelScope.launch { askInMyTime(time.stopCurrent(), then = null) }
    }

    /** The running activity could not end on its own: My Time's sheet asks, then starts [then]. */
    private fun askInMyTime(result: RunResult, then: QuickActivity?) {
        when (result) {
            is RunResult.Done -> Unit
            is RunResult.ConfirmEnd -> {
                launch.open(LogRequest.Draft(result.draft, confirmingRunning = true, then = then))
                openMyTime()
            }
            is RunResult.Overlaps -> {
                launch.open(LogRequest.Draft(result.draft, result.fit, then = then))
                openMyTime()
            }
        }
    }

    // Sleep and Wake up

    /** Sleep: sleep starts running at this minute; a running activity ends and is saved first. */
    fun startSleep() = tapSleep(asleep = true)

    /** Wake up: the running sleep ends at this minute and is saved. */
    fun wakeUp() = tapSleep(asleep = false)

    private fun tapSleep(asleep: Boolean) {
        if (local.value.sleepBusy) return
        local.update { it.copy(sleepBusy = true) }
        viewModelScope.launch {
            try {
                when (val result = if (asleep) time.startSleep() else time.wakeUp()) {
                    is SleepResult.Done -> {
                        sleepTap = result.tap
                        val kind = when {
                            asleep -> SleepUndoUi.Kind.Asleep
                            result.tap.saved != null -> SleepUndoUi.Kind.Awake
                            else -> SleepUndoUi.Kind.TooShort
                        }
                        local.update { it.copy(sleepUndo = SleepUndoUi(++nextToken, kind)) }
                    }
                    SleepResult.Ignored -> Unit
                    is SleepResult.Pending -> askInMyTime(result.run, then = if (asleep) sleepActivity() else null)
                }
            } finally {
                local.update { it.copy(sleepBusy = false) }
            }
        }
    }

    private suspend fun sleepActivity(): QuickActivity? =
        time.observeCategories().first().firstOrNull { it.id == TimeRules.SLEEP_ID }?.let { QuickActivity(it.id, it.name, it.colorKey, null) }

    fun undoSleep(token: Long) {
        if (local.value.sleepUndo?.token != token) return
        val tap = sleepTap ?: return
        sleepTap = null
        local.update { it.copy(sleepUndo = null) }
        viewModelScope.launch {
            if (!time.undoSleepTap(tap)) notify(TodayNotice.Kind.SleepUndoFailed)
        }
    }

    /** The Undo offer timed out or was dismissed: the tap stands. */
    fun sleepUndoExpired(token: Long) {
        if (local.value.sleepUndo?.token != token) return
        sleepTap = null
        local.update { it.copy(sleepUndo = null) }
    }

    /**
     * Wake up was forgotten: My Time's sheet opens on the running sleep, ending at the usual wake
     * time (never after now), for the user to set when they really woke.
     */
    fun setWakeTime() {
        val date = state.value.content?.date ?: return
        if (local.value.sleepBusy) return
        local.update { it.copy(sleepBusy = true) }
        viewModelScope.launch {
            try {
                val sleep = time.observeCurrent().first()?.takeIf { it.categoryId == TimeRules.SLEEP_ID } ?: return@launch
                val latest = TimeRules.latestActualEnd(clock.now())
                val usual = minOf(time.sleepPrefill(date, clock.zone()).second, latest)
                val end = if (TimeRules.isValidSpan(sleep.startAt, usual)) usual else minOf(latest, sleep.startAt.plus(TimeRules.MAX_DURATION))
                val draft = EntryDraft(null, TimeEntryKind.ACTUAL, sleep.categoryId, sleep.note, sleep.startAt, end, fromCurrent = true)
                launch.open(LogRequest.Draft(draft, confirmingRunning = true))
                openMyTime()
            } finally {
                local.update { it.copy(sleepBusy = false) }
            }
        }
    }

    fun myTimeOpened(token: Long) {
        local.update { if (it.openMyTime == token) it.copy(openMyTime = null) else it }
    }

    private fun openMyTime() {
        local.update { it.copy(openMyTime = ++nextToken) }
    }

    fun openNewPriority() {
        local.update { it.copy(editor = Editor.New) }
    }

    fun openPriority(id: String) {
        local.update { it.copy(editor = Editor.Existing(id)) }
    }

    fun closeEditor() {
        local.update { if (it.saving) it else it.copy(editor = null) }
    }

    fun save(title: String) {
        val current = local.value
        val editor = current.editor ?: return
        if (current.saving) return
        val date = state.value.content?.date ?: return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                when (editor) {
                    Editor.New -> when (repository.add(date, title)) {
                        is AddResult.Added -> close()
                        AddResult.LimitReached -> {
                            close()
                            notify(TodayNotice.Kind.LimitReached)
                        }
                        AddResult.InvalidTitle -> Unit
                    }
                    is Editor.Existing -> if (repository.rename(editor.id, title)) close()
                }
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }

    fun setCompleted(id: String, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(id, completed) }
    }

    fun move(id: String, direction: MoveDirection) {
        viewModelScope.launch { repository.move(id, direction) }
    }

    /** Deletes straight away (no dialog) and offers Undo. */
    fun delete(id: String) {
        local.update { it.copy(editor = null) }
        viewModelScope.launch {
            val removed = repository.delete(id) ?: return@launch
            deleted = removed
            local.update { it.copy(undo = PendingUndo(++nextToken, removed.title)) }
        }
    }

    fun undoDelete(token: Long) {
        val pending = local.value.undo ?: return
        if (pending.token != token) return
        val priority = deleted ?: return
        deleted = null
        local.update { it.copy(undo = null) }
        viewModelScope.launch {
            if (!repository.restore(priority)) notify(TodayNotice.Kind.RestoreFailed)
        }
    }

    /** The Undo offer timed out or was dismissed: the deletion stands. */
    fun undoExpired(token: Long) {
        if (local.value.undo?.token != token) return
        deleted = null
        local.update { it.copy(undo = null) }
    }

    fun noticeShown(token: Long) {
        local.update { if (it.notice?.token == token) it.copy(notice = null) else it }
    }

    fun openCarryOver() {
        local.update { it.copy(carryOverPickerOpen = true) }
    }

    fun closeCarryOver() {
        local.update { if (it.carryingOver) it else it.copy(carryOverPickerOpen = false) }
    }

    fun carryOver(ids: Collection<String>) {
        if (local.value.carryingOver || ids.isEmpty()) return
        val date = state.value.content?.date ?: return
        local.update { it.copy(carryingOver = true) }
        viewModelScope.launch {
            try {
                repository.carryOver(date, ids)
            } finally {
                local.update { it.copy(carryingOver = false, carryOverPickerOpen = false) }
            }
        }
    }

    fun dismissCarryOver() {
        val date = state.value.content?.date ?: return
        viewModelScope.launch { repository.dismissCarryOver(date) }
    }

    private fun close() {
        local.update { it.copy(editor = null) }
    }

    private fun notify(kind: TodayNotice.Kind) {
        local.update { it.copy(notice = TodayNotice(++nextToken, kind)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 30_000L
    }
}
