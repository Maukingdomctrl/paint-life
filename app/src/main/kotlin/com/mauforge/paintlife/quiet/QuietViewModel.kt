package com.mauforge.paintlife.quiet

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.DayClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * Quiet Time. The session itself lives in [QuietSessionController] (it outlives this screen);
 * this ViewModel turns it, the stored history and the current day into [QuietScreenState].
 *
 * The countdown is redrawn from the monotonic clock a few times a second while running; that is
 * display only, nothing is timed by it. Entry points run on the main thread and ignore repeats.
 */
@HiltViewModel
class QuietViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val controller: QuietSessionController,
    repository: QuietRepository,
    private val preferences: UserPreferencesRepository,
    dayClock: DayClock,
) : ViewModel() {

    private data class History(
        val week: List<QuietDay>,
        val monthCompletedSessions: Int,
        val monthMinutes: Int,
    )

    private data class Local(
        val busy: Boolean = false,
        val undo: QuietUndo? = null,
        val notice: QuietNotice? = null,
    )

    private val local = MutableStateFlow(Local())
    private var cancelled: CancelledSession? = null
    private var nextToken = 0L

    private val moment = dayClock.moments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), dayClock.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val history: Flow<History> = moment.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
        val week = weekOf(date, Locale.getDefault())
        val month = YearMonth.from(date)
        val from = minOf(week.first(), month.atDay(1))
        val to = maxOf(week.last(), month.atEndOfMonth())
        repository.observeDays(from, to).map { days ->
            val monthDays = days.values.filter { YearMonth.from(it.date) == month }
            History(
                week = week.map { day ->
                    val totals = days[day] ?: QuietDayTotals.empty(day)
                    QuietDay(day, totals.completed, totals.minutes, totals.fill)
                },
                monthCompletedSessions = monthDays.sumOf { it.completedSessions },
                monthMinutes = QuietRules.wholeMinutes(monthDays.sumOf { it.quietMillis }),
            )
        }
    }

    /** Time left of the open session, re-read from the clock while it runs; null when none is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val remaining: Flow<Long?> = controller.live.flatMapLatest { live ->
        when {
            live == null -> flowOf(null)
            live.isPaused -> flowOf(controller.remainingMillis(live))
            else -> flow {
                while (true) {
                    emit(controller.remainingMillis(live))
                    delay(TICK_MS)
                }
            }
        }
    }.distinctUntilChanged()

    val state: StateFlow<QuietScreenState> = combine(
        history,
        combine(controller.live, controller.completed, remaining, ::Triple),
        preferences.quietNotificationsAsked,
        local,
    ) { history, (live, completed, remaining), asked, local ->
        val phase = when {
            live != null -> if (live.isPaused) QuietPhase.Paused else QuietPhase.Running
            completed != null -> QuietPhase.Completed
            else -> QuietPhase.Ready
        }
        val total = live?.session?.targetDurationMillis ?: QuietUiState.FIVE_MINUTES
        QuietScreenState(
            content = QuietUiState(
                phase = phase,
                remainingMillis = when (phase) {
                    QuietPhase.Ready -> total
                    QuietPhase.Completed -> 0L
                    QuietPhase.Running, QuietPhase.Paused -> remaining ?: total
                },
                week = history.week,
                monthCompletedSessions = history.monthCompletedSessions,
                monthMinutes = history.monthMinutes,
                totalMillis = total,
            ),
            undo = local.undo,
            notice = local.notice,
            notificationsAsked = asked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuietScreenState())

    init {
        // Coming back to the screen after the phone slept: catch up on a target or pause limit
        // that passed while the CPU was asleep.
        viewModelScope.launch { controller.checkDeadlines() }
    }

    fun start() = whileNotBusy {
        controller.start()
        QuietTimerService.start(context)
    }

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun finish() = whileNotBusy {
        when (val result = controller.finish()) {
            is QuietFinish.EndedEarly -> notify(QuietNotice.Kind.EndedEarly, result.session.timer.activeDurationMillis)
            QuietFinish.NothingSaved -> notify(QuietNotice.Kind.NothingSaved)
            is QuietFinish.Completed, QuietFinish.NothingOpen -> Unit
        }
    }

    /** Cancels straight away (no dialog) and offers Undo. */
    fun cancel() = whileNotBusy {
        val removed = controller.cancel() ?: return@whileNotBusy
        cancelled = removed
        local.update { it.copy(undo = QuietUndo(++nextToken)) }
    }

    fun undoCancel(token: Long) {
        if (local.value.undo?.token != token) return
        val session = cancelled ?: return
        cancelled = null
        local.update { it.copy(undo = null) }
        whileNotBusy {
            if (controller.undoCancel(session)) QuietTimerService.start(context) else notify(QuietNotice.Kind.RestoreFailed)
        }
    }

    /** The Undo offer timed out or was dismissed: the cancellation stands. */
    fun undoExpired(token: Long) {
        if (local.value.undo?.token != token) return
        cancelled = null
        local.update { it.copy(undo = null) }
    }

    /** Done on the completion screen. */
    fun done() = controller.acknowledgeCompleted()

    /** The notification permission is about to be asked for; it is never asked for again. */
    fun notificationsAsked() {
        viewModelScope.launch { preferences.markQuietNotificationsAsked() }
    }

    fun noticeShown(token: Long) {
        local.update { if (it.notice?.token == token) it.copy(notice = null) else it }
    }

    private fun whileNotBusy(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun notify(kind: QuietNotice.Kind, endedEarlyMillis: Long = 0L) {
        local.update { it.copy(notice = QuietNotice(++nextToken, kind, endedEarlyMillis)) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 250L

        /** The seven days of [date]'s week, starting on [locale]'s first day of the week. */
        fun weekOf(date: LocalDate, locale: Locale): List<LocalDate> {
            val start = date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
            return (0L until 7L).map(start::plusDays)
        }
    }
}
