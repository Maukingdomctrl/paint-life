package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Which group of days a suggested block's evidence counts. */
enum class EvidenceGroup { Weekdays, Weekend, Days }

@Immutable
data class ProposalBlockUi(
    val id: String,
    val categoryName: String,
    val colorKey: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    /** "Seen on at least [seenDays] of [groupDays] …"; null once added or changed by the user. */
    val seenDays: Int?,
    val groupDays: Int?,
    val group: EvidenceGroup,
)

/** Why a block can't be saved as it is. */
enum class BlockProblem { SameTimes, NoDays, Overlaps }

@Immutable
data class BlockEditorUi(
    val isNew: Boolean,
    val categories: List<CategoryOption>,
    val selectedCategoryId: String?,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val problem: BlockProblem?,
    /** The block it overlaps, as "Work 09:00–12:30". */
    val overlapsWith: String? = null,
) {
    val canSave: Boolean get() = selectedCategoryId != null && problem == null
}

@Immutable
sealed interface ScheduleContentUi {
    data object Loading : ScheduleContentUi

    /** Too few recorded days in the last 4 weeks to suggest anything. */
    data class NotEnough(val recordedDays: Int) : ScheduleContentUi

    data class Proposal(
        val basedOnFrom: LocalDate,
        val basedOnTo: LocalDate,
        val recordedDays: Int,
        val unrecordedPercent: Int,
        val split: Boolean,
        val name: String,
        val blocks: List<ProposalBlockUi>,
        /** An approved schedule is active today and would be replaced from today. */
        val replacesCurrent: Boolean,
        val approving: Boolean,
    ) : ScheduleContentUi {
        val canApprove: Boolean get() = blocks.isNotEmpty() && !approving
    }
}

@Immutable
data class ScheduleScreenState(
    val content: ScheduleContentUi = ScheduleContentUi.Loading,
    val editor: BlockEditorUi? = null,
    val confirmDiscard: Boolean = false,
    /** Set when a block was just removed: Undo puts it back. */
    val undoToken: Long? = null,
    /** Approved and saved: the screen closes. */
    val approved: Boolean = false,
    /** Approving failed validation (shown once). */
    val invalidToken: Long? = null,
)

/**
 * "Suggest a schedule". The proposal is built once from observed days ([ScheduleSuggester]) and
 * kept only here, in memory, while the user edits it. Nothing is written until [approve];
 * leaving an edited proposal asks first ([requestLeave]).
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val timeRepository: TimeRepository,
    private val scheduleRepository: ScheduleRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private data class Draft(
        val base: SuggestionResult.Ready,
        val name: String,
        val blocks: List<ProposalBlock>,
        val edited: Boolean,
        val replacesCurrent: Boolean,
    )

    private data class Editor(val id: String?, val categoryId: String?, val start: Int, val end: Int, val mask: Int)

    private data class Local(
        val loading: Boolean = true,
        val notEnough: Int? = null,
        val draft: Draft? = null,
        val editor: Editor? = null,
        val confirmDiscard: Boolean = false,
        val approving: Boolean = false,
        val approved: Boolean = false,
        val removed: Pair<Int, ProposalBlock>? = null,
        val undoToken: Long? = null,
        val invalidToken: Long? = null,
    )

    private val local = MutableStateFlow(Local())
    private var nextToken = 0L
    private val categories = timeRepository.observeCategories()

    val state: StateFlow<ScheduleScreenState> = combine(local, categories) { local, cats ->
        val byId = cats.associateBy { it.id }
        val options = cats.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) }
        ScheduleScreenState(
            content = when {
                local.loading -> ScheduleContentUi.Loading
                local.notEnough != null -> ScheduleContentUi.NotEnough(local.notEnough)
                else -> local.draft!!.let { d ->
                    ScheduleContentUi.Proposal(
                        basedOnFrom = d.base.basedOnFrom,
                        basedOnTo = d.base.basedOnTo,
                        recordedDays = d.base.recordedDays,
                        unrecordedPercent = d.base.unrecordedPercent,
                        split = d.base.split,
                        name = d.name,
                        blocks = d.blocks.map { b ->
                            ProposalBlockUi(
                                id = b.id,
                                categoryName = byId[b.categoryId]?.name ?: b.categoryId,
                                colorKey = byId[b.categoryId]?.colorKey ?: "stone",
                                startMinute = b.startMinute,
                                endMinute = b.endMinute,
                                daysMask = b.daysMask,
                                seenDays = b.seenDays,
                                groupDays = b.groupDays,
                                group = when (b.daysMask) {
                                    ScheduleRules.WEEKDAYS -> EvidenceGroup.Weekdays
                                    ScheduleRules.WEEKEND -> EvidenceGroup.Weekend
                                    else -> EvidenceGroup.Days
                                },
                            )
                        },
                        replacesCurrent = d.replacesCurrent,
                        approving = local.approving,
                    )
                }
            },
            editor = local.editor?.let { e -> editorUi(e, local.draft?.blocks.orEmpty(), options, byId) },
            confirmDiscard = local.confirmDiscard,
            undoToken = local.undoToken,
            approved = local.approved,
            invalidToken = local.invalidToken,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScheduleScreenState())

    init {
        viewModelScope.launch {
            val today = time.today()
            val zone = time.zone()
            val from = DayView.readWindow(today.minusDays(ScheduleSuggester.WINDOW_DAYS.toLong())).first
            val to = DayView.readWindow(today.minusDays(1)).second
            val entries = timeRepository.observeEntries(TimeEntryKind.ACTUAL, from, to).first().map { it.entry }
            val replaces = PlannedLayer.activeOn(scheduleRepository.observeSchedules().first(), today) != null
            val result = withContext(Dispatchers.Default) { ScheduleSuggester.suggest(today, zone, entries) { UUID.randomUUID().toString() } }
            local.update {
                when (result) {
                    is SuggestionResult.NotEnough -> it.copy(loading = false, notEnough = result.recordedDays)
                    is SuggestionResult.Ready -> it.copy(
                        loading = false,
                        draft = Draft(result, ScheduleRepository.DEFAULT_NAME, result.blocks, edited = false, replacesCurrent = replaces),
                    )
                }
            }
        }
    }

    private fun editorUi(e: Editor, blocks: List<ProposalBlock>, options: List<CategoryOption>, byId: Map<String, ActivityCategoryEntity>): BlockEditorUi {
        val clash = blocks.firstOrNull { it.id != e.id && ScheduleRules.overlaps(e.start, e.end, e.mask, it.startMinute, it.endMinute, it.daysMask) }
        val problem = when {
            e.start == e.end -> BlockProblem.SameTimes
            e.mask == 0 -> BlockProblem.NoDays
            clash != null -> BlockProblem.Overlaps
            else -> null
        }
        return BlockEditorUi(
            isNew = e.id == null,
            categories = options,
            selectedCategoryId = e.categoryId,
            startMinute = e.start,
            endMinute = e.end,
            daysMask = e.mask,
            problem = problem,
            overlapsWith = clash?.let { "${byId[it.categoryId]?.name ?: it.categoryId} ${clock(it.startMinute)}–${clock(it.endMinute)}" },
        )
    }

    // Name

    fun setName(name: String) = updateDraft { it.copy(name = name.take(ScheduleRepository.NAME_MAX), edited = true) }

    // Blocks

    fun addBlock() {
        local.update { it.copy(editor = Editor(null, null, DEFAULT_START, DEFAULT_START + 60, ScheduleRules.ALL_DAYS)) }
    }

    fun openBlock(id: String) {
        val block = local.value.draft?.blocks?.firstOrNull { it.id == id } ?: return
        local.update { it.copy(editor = Editor(block.id, block.categoryId, block.startMinute, block.endMinute, block.daysMask)) }
    }

    fun closeBlock() {
        local.update { it.copy(editor = null) }
    }

    fun selectCategory(id: String) = updateEditor { it.copy(categoryId = id) }

    fun nudgeStart(minutes: Long) = updateEditor { it.copy(start = wrap(it.start + minutes.toInt())) }

    fun nudgeEnd(minutes: Long) = updateEditor { it.copy(end = wrap(it.end + minutes.toInt())) }

    fun toggleDay(index: Int) = updateEditor { it.copy(mask = it.mask xor (1 shl index)) }

    fun saveBlock() {
        val ui = state.value.editor ?: return
        if (!ui.canSave) return
        val e = local.value.editor ?: return
        updateDraft { d ->
            val block = ProposalBlock(e.id ?: UUID.randomUUID().toString(), e.categoryId!!, e.start, e.end, e.mask)
            val unchanged = d.blocks.firstOrNull { it.id == e.id }?.let {
                it.categoryId == block.categoryId && it.startMinute == block.startMinute && it.endMinute == block.endMinute && it.daysMask == block.daysMask
            } == true
            if (unchanged) return@updateDraft d
            val blocks = if (e.id == null) d.blocks + block else d.blocks.map { if (it.id == e.id) block else it }
            d.copy(blocks = blocks.sortedBy { it.startMinute }, edited = true)
        }
        local.update { it.copy(editor = null) }
    }

    /** Removes the open block at once; Undo puts it back. */
    fun deleteBlock() {
        val id = local.value.editor?.id ?: return
        local.update { current ->
            val d = current.draft ?: return@update current
            val index = d.blocks.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return@update current
            current.copy(
                draft = d.copy(blocks = d.blocks.filterNot { it.id == id }, edited = true),
                editor = null,
                removed = index to d.blocks[index],
                undoToken = ++nextToken,
            )
        }
    }

    fun undoDelete(token: Long) {
        local.update { current ->
            val (index, block) = current.removed ?: return@update current
            val d = current.draft ?: return@update current
            if (current.undoToken != token || d.blocks.any { ScheduleSuggester.overlap(it, block) }) {
                return@update current.copy(removed = null, undoToken = null)
            }
            val blocks = d.blocks.toMutableList().apply { add(index.coerceAtMost(size), block) }
            current.copy(draft = d.copy(blocks = blocks), removed = null, undoToken = null)
        }
    }

    fun undoExpired(token: Long) {
        local.update { if (it.undoToken == token) it.copy(removed = null, undoToken = null) else it }
    }

    // Approving and leaving

    /** Writes the schedule (active from today). Until this, nothing is saved. */
    fun approve() {
        val current = local.value
        val d = current.draft ?: return
        if (current.approving || current.approved || d.blocks.isEmpty()) return
        local.update { it.copy(approving = true) }
        viewModelScope.launch {
            val result = scheduleRepository.approve(ApprovedProposal(d.name, d.blocks, d.base.basedOnFrom, d.base.basedOnTo))
            local.update {
                when (result) {
                    is ApproveResult.Approved -> it.copy(approving = false, approved = true)
                    ApproveResult.Invalid -> it.copy(approving = false, invalidToken = ++nextToken)
                }
            }
        }
    }

    /** Back: true when the screen may close now; an edited proposal asks first. */
    fun requestLeave(): Boolean {
        val current = local.value
        if (current.approved || current.draft?.edited != true) return true
        local.update { it.copy(confirmDiscard = true) }
        return false
    }

    fun keepEditing() {
        local.update { it.copy(confirmDiscard = false) }
    }

    fun invalidShown(token: Long) {
        local.update { if (it.invalidToken == token) it.copy(invalidToken = null) else it }
    }

    private fun updateDraft(change: (Draft) -> Draft) {
        local.update { current -> current.draft?.let { current.copy(draft = change(it)) } ?: current }
    }

    private fun updateEditor(change: (Editor) -> Editor) {
        local.update { current -> current.editor?.let { current.copy(editor = change(it)) } ?: current }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val DEFAULT_START = 9 * 60

        fun wrap(minute: Int): Int = Math.floorMod(minute, TimeRules.MINUTES_PER_DAY)

        fun clock(minute: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
    }
}
