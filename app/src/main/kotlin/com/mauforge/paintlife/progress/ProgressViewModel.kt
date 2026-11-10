package com.mauforge.paintlife.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import com.mauforge.paintlife.core.time.DayMoment
import com.mauforge.paintlife.core.time.TimeProvider
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.mytime.DayView
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.mytime.observeObservation
import com.mauforge.paintlife.quiet.QuietRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * Progress for the chosen period (remembered). The period follows [DayClock], so at midnight, or
 * when the zone or clock changes, every report moves to the new day. Each read covers only the
 * period's own days, however much history there is.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val exercise: ExerciseRepository,
    private val quiet: QuietRepository,
    private val time: TimeRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: TimeProvider,
    dayClock: DayClock,
) : ViewModel() {

    private val moment: Flow<DayMoment> = dayClock.moments.distinctUntilChanged { a, b -> a.date == b.date && a.zone == b.zone }

    private val period: Flow<ProgressPeriod> = preferences.progressPeriod
        .map { name -> ProgressPeriod.entries.firstOrNull { it.name == name } ?: ProgressPeriod.Default }
        .distinctUntilChanged()

    private val observed: Flow<Int?> = observeObservation(time, preferences)

    /** Re-reads "now" each minute, for today's unrecorded time (display only). */
    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_MS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProgressUiState?> = combine(moment, period, ::Pair).flatMapLatest { (moment, period) ->
        val today = moment.date
        val zone = moment.zone
        val (from, to) = period.range(today)
        combine(exerciseReport(from, to, zone), quietReport(period, from, to, today), timeSection(period, from, to, today, zone)) { e, q, t ->
            ProgressUiState(period, from, to, e, q, t)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setPeriod(period: ProgressPeriod) {
        viewModelScope.launch { preferences.setProgressPeriod(period.name) }
    }

    private fun exerciseReport(from: LocalDate, to: LocalDate, zone: ZoneId): Flow<ExerciseReport> = combine(
        exercise.observeRangeTotals(from, to),
        exercise.observeRangeStats(from, to),
        exercise.observeFastestTimed(from, to),
        exercise.observeRecords(),
        exercise.observeLevels(),
    ) { totals, stats, fastest, records, levels ->
        ProgressRules.exercise(totals, stats, fastest, records, levels, from, to, zone)
    }

    private fun quietReport(period: ProgressPeriod, from: LocalDate, to: LocalDate, today: LocalDate): Flow<QuietReport> {
        val weekStart = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val months = period == ProgressPeriod.ThisMonth || period == ProgressPeriod.LastMonth
        return quiet.observeDays(from, to).map { days -> ProgressRules.quiet(days, from, to, today, weekStart, months) }
    }

    /** Today's time is always shown; longer periods wait for the observation week, as Insights do. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun timeSection(period: ProgressPeriod, from: LocalDate, to: LocalDate, today: LocalDate, zone: ZoneId): Flow<TimeSection> {
        val gate = if (period == ProgressPeriod.Today) flowOf(null) else observed
        return gate.flatMapLatest { recorded ->
            if (recorded != null) return@flatMapLatest flowOf(TimeSection.Locked(recorded))
            // Entries recorded in any zone may touch the period's days (see DayView.readWindow).
            val readFrom = DayView.readWindow(from).first
            val readTo = DayView.readWindow(to).second
            val now = if (to >= today) ticks else flowOf(clock.now())
            combine(
                time.observeEntries(TimeEntryKind.ACTUAL, readFrom, readTo),
                // Plans from the day before too: whether it has its own plan decides whether its
                // schedule blocks reach into the first day (as in My Time and Insights).
                time.observeEntries(TimeEntryKind.PLANNED, DayView.readWindow(from.minusDays(1)).first, readTo),
                time.observeSchedules(),
                time.observeCategories(),
                now,
            ) { actual, planned, schedules, categories, at ->
                TimeSection.Ready(
                    ProgressRules.time(from, to, today, at, zone, actual, planned.map { it.entry }, schedules, categories),
                ) as TimeSection
            }.flowOn(Dispatchers.Default)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 60_000L
    }
}
