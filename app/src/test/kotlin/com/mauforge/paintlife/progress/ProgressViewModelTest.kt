package com.mauforge.paintlife.progress

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.mytime.EntryDraft
import com.mauforge.paintlife.mytime.MyTimeLaunch
import com.mauforge.paintlife.mytime.Resolution
import com.mauforge.paintlife.mytime.SaveResult
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.quiet.QuietScreenState
import com.mauforge.paintlife.quiet.QuietSessionController
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.quiet.QuietViewModel
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import com.mauforge.paintlife.today.TodayRepository
import com.mauforge.paintlife.today.TodayScreenState
import com.mauforge.paintlife.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Progress on a real (in-memory) database with a hand-driven clock, checked against the screens
 * whose numbers it repeats. Main is a real single thread, as in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProgressViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(9, 0), kolkata)
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var exercise: ExerciseRepository
    private lateinit var quiet: QuietRepository
    private lateinit var timeRepository: TimeRepository
    private val viewModels = mutableListOf<ViewModel>()
    private val collectors = mutableListOf<Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        preferences = testPreferences(context)
        exercise = ExerciseRepository(db, time.provider)
        quiet = QuietRepository(db, time.provider)
        timeRepository = TimeRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        runBlocking {
            collectors.forEach { it.cancelAndJoin() }
            viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        }
        scope.cancel()
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun <T : ViewModel> collected(viewModel: T, state: StateFlow<*>): T {
        viewModels += viewModel
        collectors += CoroutineScope(main).launch { state.collect { } }
        return viewModel
    }

    private fun progress(prefs: UserPreferencesRepository = preferences): ProgressViewModel =
        ProgressViewModel(exercise, quiet, timeRepository, prefs, time.provider, time.dayClock).let { collected(it, it.state) }

    private fun onMain(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun ProgressViewModel.await(predicate: (ProgressUiState) -> Boolean): ProgressUiState = runBlocking {
        withTimeout(10_000) { state.first { it != null && predicate(it) }!! }
    }

    private fun ProgressViewModel.select(period: ProgressPeriod): ProgressUiState {
        onMain { setPeriod(period) }
        return await { it.period == period }
    }

    // Records

    private fun finishedVolume(reps: Int, activeMillis: Long) = runBlocking {
        val started = exercise.startVolumeSession(SessionTimer.started(0, 1))
        exercise.finish(started.id, reps, SessionTimer.paused(started.timer, activeMillis, 1))!!
    }

    private fun quietSession(start: LocalDateTime, status: SessionStatus, activeMillis: Long, zone: ZoneId = kolkata) = runBlocking {
        val at = start.atZone(zone).toInstant()
        db.quietDao().insert(
            QuietSessionEntity(
                id = UUID.randomUUID().toString(),
                status = status,
                startedAt = at,
                zoneId = zone,
                startDate = start.toLocalDate(),
                endedAt = at.plusMillis(activeMillis),
                endedZoneId = zone,
                targetDurationMillis = 300_000,
                timer = TimerCheckpoint(activeMillis, activeMillis, null, null),
                updatedAt = at,
                updatedZoneId = zone,
            ),
        )
    }

    private fun actual(category: String, start: LocalDateTime, end: LocalDateTime, zone: ZoneId = kolkata) = runBlocking {
        val draft = EntryDraft(null, TimeEntryKind.ACTUAL, category, null, start.atZone(zone).toInstant(), end.atZone(zone).toInstant())
        assertTrue(timeRepository.save(draft, Resolution.None) is SaveResult.Saved)
    }

    // Agreement with the source screens

    @Test
    fun todayMatchesTheTodayScreen() {
        finishedVolume(reps = 90, activeMillis = 600_000)
        // An open session counts on Today, so it counts here too.
        runBlocking { exercise.startVolumeSession(SessionTimer.started(0, 1)).let { exercise.saveProgress(it.copy(repetitions = 10)) } }
        quietSession(day.atTime(7, 0), SessionStatus.COMPLETED, 300_000)
        quietSession(day.atTime(8, 0), SessionStatus.ENDED_EARLY, 150_000)
        quietSession(day.atTime(8, 30), SessionStatus.INCOMPLETE, 200_000)
        actual("work", day.atTime(7, 0), day.atTime(8, 0))

        val today = TodayViewModel(
            TodayRepository(db, time.provider), exercise, quiet, timeRepository, MyTimeLaunch(), preferences, time.provider, time.dayClock,
        ).let { collected(it, it.state) }
        val todayContent = runBlocking {
            withTimeout(10_000) { today.state.first { s: TodayScreenState -> s.content?.exercise?.reps == 100 && s.content?.timeByCategory?.isNotEmpty() == true } }
        }.content!!
        val report = progress().select(ProgressPeriod.Today)

        assertEquals(todayContent.exercise!!.reps, report.exercise.totalReps)
        assertEquals(todayContent.exercise!!.sessions, report.exercise.sessions)
        assertEquals(todayContent.quiet!!.minutes, report.quiet.totalMinutes)
        assertEquals(todayContent.quiet!!.completed, report.quiet.completedSessions > 0)
        assertEquals(todayContent.quiet!!.fill, report.quiet.days.single().fill)
        val ready = (report.time as TimeSection.Ready).report
        assertEquals(todayContent.timeByCategory!!.map { it.categoryName to it.minutes }, ready.categories.map { it.name to it.actualMinutes })
        val todayUnrecorded = todayContent.strip!!.filter { it.colorKey == null }.sumOf { it.endMinute - it.startMinute }
        assertEquals(todayUnrecorded, ready.unrecordedMinutes)
        assertEquals(8 * 60, ready.unrecordedMinutes)
        // The average leaves out the open session.
        assertEquals(600_000L, report.exercise.averageSessionMillis)
    }

    @Test
    fun thisMonthMatchesTheQuietScreen() {
        quietSession(LocalDate.of(2026, 10, 31).atTime(21, 0), SessionStatus.COMPLETED, 300_000)
        quietSession(LocalDate.of(2026, 11, 1).atTime(7, 0), SessionStatus.COMPLETED, 300_000)
        quietSession(LocalDate.of(2026, 11, 5).atTime(7, 0), SessionStatus.ENDED_EARLY, 270_000)
        quietSession(day.atTime(7, 0), SessionStatus.ENDED_EARLY, 270_000)
        quietSession(day.atTime(8, 0), SessionStatus.CANCELLED, 100_000)

        val controller = QuietSessionController(quiet, FakeSessionClock(now = 1_000, boot = 1).clock, scope)
        val quietScreen = QuietViewModel(context, controller, quiet, preferences, time.dayClock).let { collected(it, it.state) }
        val month = runBlocking {
            withTimeout(10_000) { quietScreen.state.first { s: QuietScreenState -> (s.content?.monthCompletedSessions ?: 0) > 0 } }
        }.content!!
        val report = progress().select(ProgressPeriod.ThisMonth).quiet

        assertEquals(month.monthCompletedSessions, report.completedSessions)
        assertEquals(month.monthMinutes, report.totalMinutes)
        assertEquals(1, report.completedSessions)
        assertEquals(14, report.totalMinutes)
        // 1 to 8 November are over; today (the 9th) has no full session yet.
        assertEquals(1, report.daysWithFull)
        assertEquals(8, report.countedDays)
    }

    // Periods and days

    @Test
    fun theChosenPeriodIsRemembered() {
        val first = progress()
        assertEquals(ProgressPeriod.Last7Days, first.await { true }.period)
        val month = first.select(ProgressPeriod.LastMonth)
        assertEquals(LocalDate.of(2026, 10, 1), month.start)
        assertEquals(LocalDate.of(2026, 10, 31), month.end)

        assertEquals(ProgressPeriod.LastMonth, progress().await { true }.period)
    }

    @Test
    fun atMidnightThePeriodMovesAndEarlierDaysKeepTheirRecords() {
        time.setLocal(day.atTime(23, 50))
        finishedVolume(reps = 40, activeMillis = 300_000)
        val viewModel = progress()
        assertEquals(40, viewModel.select(ProgressPeriod.Today).exercise.totalReps)

        time.setLocal(day.plusDays(1).atTime(0, 5))
        time.announceChange()
        val next = viewModel.await { it.start == day.plusDays(1) }
        assertTrue(next.exercise.isEmpty)
        assertEquals(40, viewModel.select(ProgressPeriod.Last7Days).exercise.totalReps)
    }

    @Test
    fun aZoneChangeMovesTodayButNeverLosesASession() {
        // 00:30 on 10 November in Kolkata is 19:00 on 9 November in London.
        time.setLocal(day.plusDays(1).atTime(0, 30))
        finishedVolume(reps = 50, activeMillis = 300_000)
        val viewModel = progress()
        assertEquals(50, viewModel.select(ProgressPeriod.Today).exercise.totalReps)

        time.zone = ZoneId.of("Europe/London")
        time.announceChange()
        val london = viewModel.await { it.start == day }
        // The session counts on the day it started (10 November), wherever the phone is now.
        assertEquals(0, london.exercise.totalReps)

        time.zone = kolkata
        time.announceChange()
        assertEquals(50, viewModel.await { it.start == day.plusDays(1) }.exercise.totalReps)
    }

    // The observation week

    @Test
    fun timeOverSeveralDaysWaitsForTheObservationWeekButTodayDoesNot() {
        actual("work", day.atTime(6, 0), day.atTime(8, 0))
        val viewModel = progress()
        assertEquals(TimeSection.Locked(1), viewModel.select(ProgressPeriod.Last7Days).time)
        assertEquals(TimeSection.Locked(1), viewModel.select(ProgressPeriod.LastMonth).time)
        val today = viewModel.select(ProgressPeriod.Today).time as TimeSection.Ready
        assertEquals(120, today.report.categories.single().actualMinutes)

        (1L..6L).forEach { back -> actual("work", day.minusDays(back).atTime(9, 0), day.minusDays(back).atTime(10, 0)) }
        viewModel.select(ProgressPeriod.Last7Days)
        val week = (viewModel.await { it.time is TimeSection.Ready }.time as TimeSection.Ready).report
        assertEquals(8 * 60, week.categories.single().actualMinutes)
        assertTrue(runBlocking { preferences.myTimeObservationComplete.first() })
    }

    @Test
    fun anEmptyDatabaseShowsEmptySectionsNotZerosDressedAsResults() {
        val report = progress().select(ProgressPeriod.Today)
        assertTrue(report.exercise.isEmpty)
        assertTrue(report.quiet.isEmpty)
        val time = (report.time as TimeSection.Ready).report
        assertTrue(time.isEmpty)
        assertEquals(9 * 60, time.unrecordedMinutes)
        // The first cat is unlocked from the start; nothing is beaten.
        assertEquals(1, report.exercise.unlockedCats.size)
        assertEquals(0, report.exercise.levelsBeaten)
    }
}
