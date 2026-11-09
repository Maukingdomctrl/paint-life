package com.mauforge.paintlife.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.DayMoment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    dayClock: DayClock,
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
        val notice: TodayNotice? = null,
    )

    private data class DayData(val date: LocalDate, val priorities: List<PriorityEntity>, val offer: CarryOverOffer?)

    private val local = MutableStateFlow(Local())
    private var deleted: PriorityEntity? = null
    private var nextToken = 0L

    private val moment = dayClock.moments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), dayClock.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayData = moment.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
        combine(repository.observePriorities(date), repository.observeCarryOver(date)) { priorities, offer ->
            DayData(date, priorities, offer)
        }
    }

    val state: StateFlow<TodayScreenState> = combine(moment, dayData, local, ::toScreenState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayScreenState())

    private fun toScreenState(moment: DayMoment, data: DayData, local: Local): TodayScreenState {
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
        )
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
            notice = local.notice,
        )
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
    }
}
