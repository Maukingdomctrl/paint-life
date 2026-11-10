package com.mauforge.paintlife.mytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * My Time. Shows one day (past, today or up to [MAX_DAYS_AHEAD] ahead for planning), follows
 * [DayClock] while on today, and runs the log sheet for actual and planned entries, the overlap
 * choice, Undo, the running "now doing" activity, "Change for this day" on schedule blocks and
 * the categories sheet.
 *
 * Entry points run on the main thread and ignore repeats while a save is running.
 */
@HiltViewModel
class MyTimeViewModel @Inject constructor(
    private val repository: TimeRepository,
    private val preferences: UserPreferencesRepository,
    private val launch: MyTimeLaunch,
    private val time: TimeProvider,
    dayClock: DayClock,
) : ViewModel() {

    /** The sheet being edited. Times are instants; labels are made in [zone]. */
    private data class Editor(
        val id: String?,
        val categoryId: String?,
        val note: String,
        val start: Instant,
        val end: Instant,
        val zone: ZoneId,
        val fromCurrent: Boolean = false,
        val confirmingRunning: Boolean = false,
        val kind: TimeEntryKind = TimeEntryKind.ACTUAL,
        /** A future day: only planned entries can be made. */
        val plannedOnly: Boolean = false,
        /** Started once this is saved (switching from a running activity that needed the user). */
        val then: QuickActivity? = null,
    )

    private data class Local(
        val editor: Editor? = null,
        val overlap: List<OverlapChange>? = null,
        val saving: Boolean = false,
        val undo: MyTimeUndo? = null,
        val notice: MyTimeNotice? = null,
        /** A tapped schedule block: its id and the day it belongs to. */
        val schedulePrompt: Pair<String, LocalDate>? = null,
        val categoriesOpen: Boolean = false,
    )

    private data class Day(
        val date: LocalDate,
        val today: LocalDate,
        val entries: List<EntryWithCategory>,
        val planned: List<EntryWithCategory>,
        val schedules: List<ScheduleWithBlocks>,
    )

    private val local = MutableStateFlow(Local())
    private val selected = MutableStateFlow<LocalDate?>(null)
    private val layer = MutableStateFlow(TimeLayer.Actual)
    private var undoChange: EntryChange? = null
    private var nextToken = 0L

    private val moment = dayClock.moments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), dayClock.now())

    /** Re-reads "now" twice a minute for the now line and the running activity (display only). */
    private val now: Flow<Instant> = flow {
        while (true) {
            emit(time.now())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val day: Flow<Day> = combine(selected, moment) { picked, m -> (picked ?: m.date) to m.date }
        .distinctUntilChanged()
        .flatMapLatest { (date, today) ->
            // Planned entries from the day before too: whether it has its own plan decides
            // whether its schedule blocks reach into this day.
            val from = DayView.readWindow(date.minusDays(1)).first
            val to = DayView.readWindow(date).second
            combine(
                repository.observeDayEntries(date),
                repository.observeEntries(TimeEntryKind.PLANNED, from, to),
                repository.observeSchedules(),
            ) { actual, planned, schedules -> Day(date, today, actual, planned, schedules) }
        }

    private val categories = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val observed: Flow<Int?> = observeObservation(repository, preferences)

    private val quick: Flow<List<QuickActivity>> = combine(repository.observeRecent(), categories) { recent, cats ->
        val byId = cats.associateBy { it.id }
        val recentActivities = recent.mapNotNull { row ->
            byId[row.categoryId]?.let { QuickActivity(it.id, it.name, it.colorKey, row.note) }
        }
        (recentActivities + cats.map { QuickActivity(it.id, it.name, it.colorKey, null) })
            .distinctBy { it.categoryId to it.note }
    }

    private val content: Flow<MyTimeUiState> = combine(
        day,
        combine(repository.observeCurrent(), categories, layer, ::Triple),
        observed,
        quick,
        now,
    ) { day, (current, cats, layer), observed, quick, now -> buildContent(day, current, cats, layer, observed, quick, now) }

    val state: StateFlow<MyTimeScreenState> = combine(content, local, categories, quick) { content, local, cats, quick ->
        MyTimeScreenState(
            content = content,
            editor = local.editor?.let { editorUi(it, cats, quick, local.saving) },
            overlap = local.overlap?.let { fit -> local.editor?.let { overlapUi(it, fit, cats) } },
            undo = local.undo,
            notice = local.notice,
            schedulePrompt = local.schedulePrompt?.let { (id, _) ->
                content.planned.firstOrNull { it.id == id }?.let { SchedulePromptUi(it.categoryName, "${it.startLabel}–${it.endLabel}") }
            },
            categories = if (local.categoriesOpen) cats.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) } else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyTimeScreenState())

    init {
        viewModelScope.launch {
            launch.request.filterNotNull().collect { launch.take()?.let(::handle) }
        }
    }

    private fun buildContent(
        day: Day,
        current: CurrentActivityEntity?,
        cats: List<ActivityCategoryEntity>,
        layer: TimeLayer,
        observed: Int?,
        quick: List<QuickActivity>,
        now: Instant,
    ): MyTimeUiState {
        val currentZone = time.zone()
        val zone = DayView.zoneFor(day.date, day.today, currentZone, day.entries)
        val isToday = day.date == day.today
        val isFuture = day.date > day.today
        val onDay = DayView.onDay(day.entries, day.date, zone)
        val byId = cats.associateBy { it.id }
        val planned = PlannedLayer.forDay(day.date, zone, day.planned.map { it.entry }, day.schedules)
            .mapNotNull { DayView.plannedBlock(it, byId[it.categoryId], day.date, zone) }
        return MyTimeUiState(
            date = day.date,
            isToday = isToday,
            // A future day has nothing actual yet: it shows (and takes) plans only.
            layer = if (isFuture) TimeLayer.Planned else layer,
            actual = if (isFuture) emptyList() else DayView.blocks(onDay, day.date, zone),
            planned = planned,
            hourMarks = TimelineGeometry.hourMarks(day.date, zone),
            dayLengthMinutes = TimelineGeometry.dayLengthMinutes(day.date, zone),
            nowMinute = if (isToday) DayView.minuteOfDay(now, day.date, zone) else null,
            observedDays = observed,
            insightsReady = observed == null,
            layersAvailable = !isFuture,
            isFuture = isFuture,
            canGoForward = day.date < day.today.plusDays(MAX_DAYS_AHEAD),
            shownZone = zone.id.takeIf { zone != currentZone || DayView.hasOtherZones(day.entries, day.date, zone) },
            nowDoing = current?.let { running ->
                val category = cats.firstOrNull { it.id == running.categoryId }
                NowDoingUi(
                    categoryName = category?.name.orEmpty(),
                    colorKey = category?.colorKey ?: "stone",
                    note = running.note,
                    startLabel = TimeRules.wallTime(running.startAt, currentZone).text,
                    elapsedMinutes = Duration.between(running.startAt, now).toMinutes().coerceAtLeast(0),
                )
            },
            quickActivities = quick,
        )
    }

    private fun editorUi(editor: Editor, cats: List<ActivityCategoryEntity>, quick: List<QuickActivity>, saving: Boolean): LogActivityUiState {
        val start = TimeRules.wallTime(editor.start, editor.zone)
        val end = TimeRules.wallTime(editor.end, editor.zone)
        val minutes = Duration.between(editor.start, editor.end).toMinutes()
        return LogActivityUiState(
            isEditing = editor.id != null,
            layer = if (editor.kind == TimeEntryKind.PLANNED) TimeLayer.Planned else TimeLayer.Actual,
            categories = cats.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) },
            selectedCategoryId = editor.categoryId,
            startLabel = start.text,
            endLabel = end.text,
            endsNextDay = editor.end.atZone(editor.zone).toLocalDate() > editor.start.atZone(editor.zone).toLocalDate(),
            durationMinutes = minutes.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
            note = editor.note,
            recent = quick.filter { it.note != null }.take(TimeRules.RECENT_LIMIT),
            startOccurrence = start.occurrence,
            endOccurrence = end.occurrence,
            confirmingRunning = editor.confirmingRunning,
            layersAvailable = editor.id == null && !editor.fromCurrent && !editor.plannedOnly,
            saving = saving,
        )
    }

    private fun overlapUi(editor: Editor, fit: List<OverlapChange>, cats: List<ActivityCategoryEntity>): OverlapUiState {
        val names = cats.associate { it.id to it.name }
        fun range(start: Instant, end: Instant) = "${TimeRules.wallTime(start, editor.zone).text}–${TimeRules.wallTime(end, editor.zone).text}"
        return OverlapUiState(
            planned = editor.kind == TimeEntryKind.PLANNED,
            newCategoryName = names[editor.categoryId].orEmpty(),
            newRange = range(editor.start, editor.end),
            neighbours = fit.map { change ->
                val o = change.original
                when (change) {
                    is OverlapChange.Trim -> OverlapRow(names[o.categoryId].orEmpty(), range(o.startAt, o.endAt), OverlapOutcome.Trimmed, listOf(range(change.start, change.end)))
                    is OverlapChange.Split -> OverlapRow(
                        names[o.categoryId].orEmpty(),
                        range(o.startAt, o.endAt),
                        OverlapOutcome.Split,
                        listOf(range(o.startAt, change.firstEnd), range(change.secondStart, o.endAt)),
                    )
                    is OverlapChange.Remove -> OverlapRow(names[o.categoryId].orEmpty(), range(o.startAt, o.endAt), OverlapOutcome.Removed, emptyList())
                }
            },
        )
    }

    // Day navigation

    fun previousDay() {
        val shown = state.value.content?.date ?: return
        selected.value = shown.minusDays(1)
    }

    fun nextDay() {
        val shown = state.value.content?.date ?: return
        val today = moment.value.date
        val next = shown.plusDays(1)
        if (next > today.plusDays(MAX_DAYS_AHEAD)) return
        selected.value = next.takeIf { it != today }
    }

    fun setLayer(value: TimeLayer) {
        layer.value = value
    }

    // Opening the sheet

    /** Log activity: since the last entry on today; the first gap on a past day. */
    fun openNew() {
        val content = state.value.content ?: return
        if (content.layer == TimeLayer.Planned) {
            openPlanned(content)
            return
        }
        if (content.isToday) {
            logSinceLastEntry()
            return
        }
        val zone = zoneOfShownDay()
        val gap = TimelineGeometry.unrecordedGaps(content.actual.map { it.span }, content.dayLengthMinutes).firstOrNull()
        val startMinute = gap?.start ?: (12 * 60)
        val endMinute = gap?.let { minOf(it.end, it.start + 60) } ?: (13 * 60)
        openEditor(DayView.instantAt(content.date, zone, startMinute), DayView.instantAt(content.date, zone, endMinute), zone)
    }

    /** A new planned entry: after the day's last plan, else now (today) or 09:00. An hour long. */
    private fun openPlanned(content: MyTimeUiState) {
        val zone = zoneOfShownDay()
        val lastEnd = content.planned.maxOfOrNull { it.span.end }?.takeIf { it <= content.dayLengthMinutes - TimeRules.STEP_MINUTES }
        val start = when {
            lastEnd != null -> TimeRules.roundToStep(DayView.instantAt(content.date, zone, lastEnd))
            content.isToday -> TimeRules.roundToStep(time.now())
            else -> DayView.instantAt(content.date, zone, PLAN_DEFAULT_START)
        }
        local.update {
            it.copy(
                editor = Editor(null, null, "", start, start.plus(Duration.ofHours(1)), zone, kind = TimeEntryKind.PLANNED, plannedOnly = content.isFuture),
                overlap = null,
            )
        }
    }

    /** The sheet's Actual / Planned switch (new entries only; future days stay planned). */
    fun setEditorLayer(value: TimeLayer) = updateEditor { editor ->
        if (editor.id != null || editor.fromCurrent || editor.plannedOnly) {
            editor
        } else {
            editor.copy(kind = if (value == TimeLayer.Planned) TimeEntryKind.PLANNED else TimeEntryKind.ACTUAL)
        }
    }

    /** From where the last entry ended to now (rounded to 5 minutes). */
    fun logSinceLastEntry() {
        selected.value = null
        viewModelScope.launch {
            val now = TimeRules.roundToStep(time.now())
            val last = repository.lastActualEnd(time.now())
            var start = last?.takeIf { it < now && Duration.between(it, now) <= TimeRules.MAX_DURATION } ?: now.minus(Duration.ofHours(1))
            if (start >= now) start = now.minus(Duration.ofMinutes(30))
            openEditor(start, now, time.zone())
        }
    }

    /** Tap on an unrecorded stretch of the shown day: the sheet opens with exactly that stretch. */
    fun fillGap(startMinute: Int, endMinute: Int) {
        val content = state.value.content ?: return
        val zone = zoneOfShownDay()
        val start = DayView.instantAt(content.date, zone, startMinute)
        val end = minOf(DayView.instantAt(content.date, zone, endMinute), start.plus(TimeRules.MAX_DURATION))
        openEditor(start, end, zone)
    }

    fun openEntry(id: String) {
        if (id.startsWith(PlannedLayer.SCHEDULE_PREFIX)) {
            val day = runCatching { LocalDate.parse(id.substringAfterLast(':')) }.getOrNull() ?: return
            local.update { it.copy(schedulePrompt = id to day) }
            return
        }
        val zone = zoneOfShownDay()
        viewModelScope.launch {
            val entry = repository.entry(id) ?: return@launch notify(MyTimeNotice.Kind.Missing)
            local.update {
                it.copy(
                    editor = Editor(entry.id, entry.categoryId, entry.note.orEmpty(), entry.startAt, entry.endAt, zone, kind = entry.kind),
                    overlap = null,
                )
            }
        }
    }

    // Schedule blocks

    /** "Change for this day": the day's schedule blocks become its own planned entries (Undo). */
    fun changeScheduleDay() {
        val (_, day) = local.value.schedulePrompt ?: return
        val zone = zoneOfShownDay()
        local.update { it.copy(schedulePrompt = null) }
        viewModelScope.launch {
            repository.copyScheduleDay(day, zone)?.let { offerUndo(it, MyTimeUndo.Kind.Copied) }
        }
    }

    fun closeSchedulePrompt() {
        local.update { it.copy(schedulePrompt = null) }
    }

    // Categories

    fun openCategories() {
        local.update { it.copy(categoriesOpen = true) }
    }

    fun closeCategories() {
        local.update { it.copy(categoriesOpen = false) }
    }

    fun setDistraction(categoryId: String, isDistraction: Boolean) {
        viewModelScope.launch { repository.setDistraction(categoryId, isDistraction) }
    }

    private fun openEditor(start: Instant, end: Instant, zone: ZoneId) {
        local.update { it.copy(editor = Editor(null, null, "", start, end, zone), overlap = null) }
    }

    private fun zoneOfShownDay(): ZoneId {
        val content = state.value.content
        return content?.shownZone?.let(ZoneId::of) ?: time.zone()
    }

    private fun handle(request: LogRequest) {
        when (request) {
            LogRequest.SinceLastEntry -> logSinceLastEntry()
            is LogRequest.Draft -> {
                val draft = request.draft
                val zone = time.zone()
                val endDate = draft.end.atZone(zone).toLocalDate()
                selected.value = endDate.takeIf { it < moment.value.date }
                local.update {
                    it.copy(
                        editor = Editor(draft.id, draft.categoryId, draft.note.orEmpty(), draft.start, draft.end, zone, draft.fromCurrent, request.confirmingRunning),
                        overlap = request.overlaps,
                    )
                }
            }
        }
    }

    // Editing

    fun closeEditor() {
        local.update { if (it.saving) it else it.copy(editor = null, overlap = null) }
    }

    fun selectCategory(id: String) = updateEditor { it.copy(categoryId = id) }

    fun setNote(note: String) = updateEditor { it.copy(note = note.take(TimeRepository.NOTE_MAX)) }

    fun useActivity(activity: QuickActivity) = updateEditor { it.copy(categoryId = activity.categoryId, note = activity.note.orEmpty()) }

    /** Moves the start by [minutes] (to the 5-minute grid); the end follows if they would cross. */
    fun nudgeStart(minutes: Long) = updateEditor { editor ->
        val start = TimeRules.roundToStep(editor.start.plus(Duration.ofMinutes(minutes)))
        val end = if (start < editor.end) editor.end else start.plus(Duration.ofMinutes(TimeRules.STEP_MINUTES))
        editor.copy(start = start, end = end)
    }

    /** Moves the end by [minutes] (to the 5-minute grid), never before the start. */
    fun nudgeEnd(minutes: Long) = updateEditor { editor ->
        val end = TimeRules.roundToStep(editor.end.plus(Duration.ofMinutes(minutes)))
        editor.copy(end = if (end > editor.start) end else editor.start.plus(Duration.ofMinutes(TimeRules.STEP_MINUTES)))
    }

    private fun updateEditor(change: (Editor) -> Editor) {
        local.update { current -> current.editor?.takeIf { !current.saving }?.let { current.copy(editor = change(it), overlap = null) } ?: current }
    }

    fun save() = saveWith(Resolution.None)

    /** The overlap choice: fit it in (trim, split, replace covered) or replace every overlapping entry. */
    fun resolve(resolution: Resolution) = saveWith(resolution)

    /** Back from the overlap choice to the sheet. */
    fun backToEditor() {
        local.update { it.copy(overlap = null) }
    }

    private fun saveWith(resolution: Resolution) {
        val current = local.value
        val editor = current.editor ?: return
        val categoryId = editor.categoryId ?: return
        if (current.saving) return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val draft = EntryDraft(editor.id, editor.kind, categoryId, editor.note, editor.start, editor.end, editor.fromCurrent, zone = editor.zone)
                when (val result = repository.save(draft, resolution)) {
                    is SaveResult.Saved -> {
                        local.update { it.copy(editor = null, overlap = null) }
                        result.change?.let { offerUndo(it, MyTimeUndo.Kind.Fitted) }
                        editor.then?.let { startNow(it) }
                    }
                    is SaveResult.Overlaps -> local.update { it.copy(overlap = result.fit) }
                    SaveResult.Invalid -> notify(MyTimeNotice.Kind.Invalid)
                    SaveResult.Missing -> {
                        local.update { it.copy(editor = null, overlap = null) }
                        notify(MyTimeNotice.Kind.Missing)
                    }
                }
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }

    /** Delete from the sheet: at once, with Undo. Confirming a running activity: discard it. */
    fun delete() {
        val editor = local.value.editor ?: return
        if (local.value.saving) return
        local.update { it.copy(editor = null, overlap = null) }
        viewModelScope.launch {
            if (editor.confirmingRunning) {
                repository.discardCurrent()
                editor.then?.let { startNow(it) }
                return@launch
            }
            val id = editor.id ?: return@launch
            repository.delete(id)?.let { offerUndo(it, MyTimeUndo.Kind.Deleted) }
        }
    }

    // Undo and notices

    private fun offerUndo(change: EntryChange, kind: MyTimeUndo.Kind) {
        undoChange = change
        local.update { it.copy(undo = MyTimeUndo(++nextToken, kind)) }
    }

    fun undo(token: Long) {
        if (local.value.undo?.token != token) return
        val change = undoChange ?: return
        undoChange = null
        local.update { it.copy(undo = null) }
        viewModelScope.launch { if (!repository.undo(change)) notify(MyTimeNotice.Kind.UndoFailed) }
    }

    fun undoExpired(token: Long) {
        if (local.value.undo?.token != token) return
        undoChange = null
        local.update { it.copy(undo = null) }
    }

    fun noticeShown(token: Long) {
        local.update { if (it.notice?.token == token) it.copy(notice = null) else it }
    }

    private fun notify(kind: MyTimeNotice.Kind) {
        local.update { it.copy(notice = MyTimeNotice(++nextToken, kind)) }
    }

    // Now doing

    /** Starts [activity] now; a running one ends at this moment (switching). */
    fun startNow(activity: QuickActivity) {
        viewModelScope.launch { handleRun(repository.startCurrent(activity.categoryId, activity.note), then = activity) }
    }

    fun stopNow() {
        viewModelScope.launch { handleRun(repository.stopCurrent(), then = null) }
    }

    private fun handleRun(result: RunResult, then: QuickActivity?) {
        val zone = time.zone()
        when (result) {
            is RunResult.Done -> Unit
            is RunResult.ConfirmEnd -> local.update {
                val d = result.draft
                it.copy(editor = Editor(null, d.categoryId, d.note.orEmpty(), d.start, d.end, zone, fromCurrent = true, confirmingRunning = true, then = then), overlap = null)
            }
            is RunResult.Overlaps -> local.update {
                val d = result.draft
                it.copy(editor = Editor(null, d.categoryId, d.note.orEmpty(), d.start, d.end, zone, fromCurrent = true, then = then), overlap = result.fit)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 30_000L

        /** Days ahead that can be planned. */
        const val MAX_DAYS_AHEAD = 7L
        private const val PLAN_DEFAULT_START = 9 * 60
    }
}
