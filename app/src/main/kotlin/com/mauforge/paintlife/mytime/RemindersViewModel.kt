package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.backup.DataReplaced
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** A one-off reminder in the list. [date] and [time] are in the current zone. */
@Immutable
data class OneOffUi(val id: String, val title: String, val date: LocalDate, val time: String, val action: StartAction)

/** The "Add reminder" form. */
@Immutable
data class ReminderDraftUi(
    val title: String,
    val date: LocalDate,
    val time: String,
    val isToday: Boolean,
    val action: StartAction,
    /** The chosen time is not ahead of now. */
    val notAhead: Boolean,
) {
    val canSave: Boolean get() = title.isNotBlank() && !notAhead
}

@Immutable
data class RemindersUiState(
    val oneOffs: List<OneOffUi> = emptyList(),
    /** The bedtime reminder's time ("22:30"), or null when it is off. */
    val bedtime: String? = null,
    val draft: ReminderDraftUi? = null,
    /** A deleted reminder can still be put back; the token names the offer. */
    val undoToken: Long? = null,
    /** Undo could not put the reminder back (its time had passed). */
    val undoFailedToken: Long? = null,
)

/**
 * The Reminders sheet in My Time: one-off reminders (add, delete with Undo) and the bedtime
 * reminder. Everything here is off or empty until the user sets it.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val time: TimeProvider,
    replaced: DataReplaced,
) : ViewModel() {

    private data class Draft(val title: String, val at: Instant, val action: StartAction)

    private data class Local(val draft: Draft? = null, val undoToken: Long? = null, val undoFailedToken: Long? = null, val refresh: Int = 0)

    private val local = MutableStateFlow(Local())
    private var deleted: ReminderEntity? = null
    private var nextToken = 0L

    val state: StateFlow<RemindersUiState> = combine(repository.observeOneOffs(), repository.bedtimeMinute, local) { oneOffs, bedtime, local ->
        val zone = time.zone()
        val now = time.now()
        RemindersUiState(
            oneOffs = oneOffs.map { OneOffUi(it.id, it.title.orEmpty(), it.fireAt.atZone(zone).toLocalDate(), TimeRules.wallTime(it.fireAt, zone).text, it.action) },
            bedtime = bedtime?.let(::clock),
            draft = local.draft?.let { draftUi(it, now, zone) },
            undoToken = local.undoToken,
            undoFailedToken = local.undoFailedToken,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemindersUiState())

    init {
        // A backup was imported: an Undo from before it must not put an old reminder into the new data.
        viewModelScope.launch {
            replaced.count.drop(1).collect {
                deleted = null
                local.update { it.copy(undoToken = null, draft = null) }
            }
        }
    }

    private fun draftUi(draft: Draft, now: Instant, zone: ZoneId): ReminderDraftUi {
        val date = draft.at.atZone(zone).toLocalDate()
        return ReminderDraftUi(
            title = draft.title,
            date = date,
            time = TimeRules.wallTime(draft.at, zone).text,
            isToday = date == now.atZone(zone).toLocalDate(),
            action = draft.action,
            notAhead = draft.at <= now,
        )
    }

    // Add reminder

    /** Opens the form: an hour from now, on the 5-minute grid. */
    fun openDraft() {
        local.update { it.copy(draft = Draft("", TimeRules.roundToStep(time.now().plus(Duration.ofHours(1))), StartAction.NONE)) }
    }

    fun closeDraft() {
        local.update { it.copy(draft = null) }
    }

    fun setTitle(title: String) = updateDraft { it.copy(title = title.replace('\n', ' ').take(ReminderRules.TITLE_MAX)) }

    fun setAction(action: StartAction) = updateDraft { it.copy(action = action) }

    /** Moves the time by [minutes] (to the 5-minute grid): never into the past, at most a year ahead. */
    fun nudge(minutes: Long) = updateDraft { draft ->
        val now = time.now()
        val earliest = TimeRules.roundToStep(now).let { if (it > now) it else it.plus(Duration.ofMinutes(TimeRules.STEP_MINUTES)) }
        val moved = TimeRules.roundToStep(draft.at.plus(Duration.ofMinutes(minutes)))
        draft.copy(at = moved.coerceIn(earliest, now.plus(MAX_AHEAD)))
    }

    private fun updateDraft(change: (Draft) -> Draft) {
        local.update { current -> current.draft?.let { current.copy(draft = change(it)) } ?: current }
    }

    fun saveDraft() {
        val draft = local.value.draft ?: return
        viewModelScope.launch {
            when (repository.addOneOff(draft.title, draft.at, draft.action)) {
                is AddReminderResult.Added -> local.update { it.copy(draft = null) }
                // The form shows why (no title, or a time that is no longer ahead).
                AddReminderResult.NoTitle, AddReminderResult.NotAhead -> local.update { it.copy(refresh = it.refresh + 1) }
            }
        }
    }

    // Delete with Undo

    fun delete(id: String) {
        viewModelScope.launch {
            val removed = repository.delete(id) ?: return@launch
            deleted = removed
            local.update { it.copy(undoToken = ++nextToken, undoFailedToken = null) }
        }
    }

    fun undo(token: Long) {
        if (local.value.undoToken != token) return
        val reminder = deleted ?: return
        deleted = null
        local.update { it.copy(undoToken = null) }
        viewModelScope.launch {
            if (!repository.restore(reminder)) local.update { it.copy(undoFailedToken = ++nextToken) }
        }
    }

    fun undoExpired(token: Long) {
        if (local.value.undoToken != token) return
        deleted = null
        local.update { it.copy(undoToken = null) }
    }

    fun undoFailedShown(token: Long) {
        local.update { if (it.undoFailedToken == token) it.copy(undoFailedToken = null) else it }
    }

    // Bedtime

    /** Switches the bedtime reminder on (at 22:30 to begin with) or off. */
    fun setBedtimeOn(on: Boolean) {
        viewModelScope.launch { repository.setBedtime(if (on) DEFAULT_BEDTIME_MINUTE else null) }
    }

    /** Moves the bedtime by [minutes], round the clock. */
    fun nudgeBedtime(minutes: Long) {
        viewModelScope.launch {
            val current = repository.bedtimeMinute.first() ?: return@launch
            repository.setBedtime(Math.floorMod(current + minutes, TimeRules.MINUTES_PER_DAY.toLong()).toInt())
        }
    }

    private fun clock(minute: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_BEDTIME_MINUTE = 22 * 60 + 30
        val MAX_AHEAD: Duration = Duration.ofDays(365)
    }
}
