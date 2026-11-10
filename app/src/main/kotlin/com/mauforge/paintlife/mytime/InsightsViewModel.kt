package com.mauforge.paintlife.mytime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.progress.CategoryTotal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One wall-clock hour: the category that filled it most often, and the whole mix. */
@Immutable
data class HourUi(
    val hour: Int,
    val topName: String?,
    val topDays: Int,
    val days: Int,
    /** (colour key, minutes) largest first; [capacityMinutes] is the hour over every day. */
    val mix: List<Pair<String, Long>>,
    val capacityMinutes: Long,
)

@Immutable
data class InsightsReadyUi(
    val period: InsightsPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val totalMinutes: Long,
    val recordedMinutes: Long,
    val unrecordedMinutes: Long,
    val categories: List<CategoryTotal>,
    val distractionMinutes: Long,
    val distractionCategories: List<CategoryTotal>,
    val distractionByDay: List<Pair<LocalDate, Long>>,
    /** At least one category is marked as a distraction. */
    val anyFlagged: Boolean,
    val plannedDays: Int,
    val planned: List<CategoryTotal>,
    val hours: List<HourUi>,
)

@Immutable
sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /** The observation week is not over: [recordedDays] of 7. */
    data class Locked(val recordedDays: Int) : InsightsUiState

    data class Ready(val report: InsightsReadyUi) : InsightsUiState
}

@Immutable
data class InsightsScreenState(
    val content: InsightsUiState = InsightsUiState.Loading,
    /** The categories sheet is open with these. */
    val categories: List<CategoryOption>? = null,
)

/**
 * Insights over complete days ending yesterday ([InsightsPeriod], remembered). Hidden until the
 * observation week is over. Totals follow category flags at once, past days included.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: TimeRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
    dayClock: DayClock,
) : ViewModel() {

    private val categoriesOpen = MutableStateFlow(false)

    private val today: Flow<LocalDate> = dayClock.moments.map { it.date }.distinctUntilChanged()

    private val period: Flow<InsightsPeriod> = preferences.myTimeInsightsPeriod
        .map { name -> InsightsPeriod.entries.firstOrNull { it.name == name } ?: InsightsPeriod.Week }
        .distinctUntilChanged()

    /** Null once the observation week is over, else the days recorded so far. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val observed: Flow<Int?> = preferences.myTimeObservationComplete.flatMapLatest { complete ->
        if (complete) {
            flowOf(null)
        } else {
            repository.observeObservedDays()
                .onEach { if (it >= TimeRules.OBSERVATION_DAYS) preferences.markMyTimeObservationComplete() }
                .map { it.takeIf { days -> days < TimeRules.OBSERVATION_DAYS } }
        }
    }.distinctUntilChanged()

    private val categories = repository.observeCategories()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<InsightsUiState> = combine(today, period, observed, ::Triple).flatMapLatest { (today, period, observed) ->
        if (observed != null) return@flatMapLatest flowOf(InsightsUiState.Locked(observed))
        val (from, to) = period.range(today)
        val readFrom = DayView.readWindow(from.minusDays(1)).first
        val readTo = DayView.readWindow(to).second
        combine(
            repository.observeEntries(TimeEntryKind.ACTUAL, readFrom, readTo),
            repository.observeEntries(TimeEntryKind.PLANNED, readFrom, readTo),
            repository.observeSchedules(),
            categories,
        ) { actual, planned, schedules, cats ->
            val report = Insights.report(from, to, today, time.zone(), actual.map { it.entry }, planned.map { it.entry }, schedules, cats)
            InsightsUiState.Ready(toUi(period, report, cats))
        }.flowOn(Dispatchers.Default)
    }

    val state: StateFlow<InsightsScreenState> = combine(content, categoriesOpen, categories) { content, open, cats ->
        InsightsScreenState(content, if (open) cats.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) } else null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InsightsScreenState())

    fun setPeriod(period: InsightsPeriod) {
        viewModelScope.launch { preferences.setMyTimeInsightsPeriod(period.name) }
    }

    fun openCategories() {
        categoriesOpen.value = true
    }

    fun closeCategories() {
        categoriesOpen.value = false
    }

    fun setDistraction(categoryId: String, isDistraction: Boolean) {
        viewModelScope.launch { repository.setDistraction(categoryId, isDistraction) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun toUi(period: InsightsPeriod, report: InsightsReport, cats: List<ActivityCategoryEntity>): InsightsReadyUi {
            val byId = cats.associateBy { it.id }
            val totals = report.categories.map { CategoryTotal(it.name, it.colorKey, it.minutes.toInt(), null, it.isDistraction) }
            return InsightsReadyUi(
                period = period,
                from = report.from,
                to = report.to,
                days = report.days,
                totalMinutes = report.totalMinutes,
                recordedMinutes = report.recordedMinutes,
                unrecordedMinutes = report.unrecordedMinutes,
                categories = totals,
                distractionMinutes = report.distractionMinutes,
                distractionCategories = totals.filter { it.isDistraction },
                distractionByDay = report.distractionByDay,
                anyFlagged = cats.any { it.isDistraction },
                plannedDays = report.plannedDays,
                planned = report.planned.map {
                    CategoryTotal(it.name, it.colorKey, it.actualMinutes.toInt(), it.plannedMinutes.toInt(), byId[it.categoryId]?.isDistraction == true)
                },
                hours = report.hours.map { h ->
                    HourUi(
                        hour = h.hour,
                        topName = h.topCategoryId?.let { byId[it]?.name ?: it },
                        topDays = h.topDays,
                        days = h.days,
                        mix = h.minutesByCategory.map { (id, minutes) -> (byId[id]?.colorKey ?: "stone") to minutes },
                        capacityMinutes = h.days * 60L,
                    )
                },
            )
        }
    }
}
