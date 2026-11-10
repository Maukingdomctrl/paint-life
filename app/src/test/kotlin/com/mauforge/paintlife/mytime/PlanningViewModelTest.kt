package com.mauforge.paintlife.mytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Insights and the schedule proposal through their ViewModels, on a real (in-memory) database
 * and a hand-driven clock. Main is a real single thread, as in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlanningViewModelTest {

    private val zone = ZoneId.of("Europe/London")
    private val today: LocalDate = LocalDate.of(2026, 11, 10)
    private val time = TestTime(today.atTime(9, 0), zone)
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TimeRepository
    private val running = mutableListOf<Pair<ViewModel, Job>>()

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = TimeRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        runBlocking {
            running.forEach { (vm, job) ->
                job.cancelAndJoin()
                vm.viewModelScope.coroutineContext.job.cancelAndJoin()
            }
        }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun <S> collect(vm: ViewModel, state: StateFlow<S>) {
        running += vm to CoroutineScope(main).launch { state.collect { } }
    }

    private fun insights(): InsightsViewModel = InsightsViewModel(
        repository,
        testPreferences(ApplicationProvider.getApplicationContext()),
        time.provider,
        time.dayClock,
    ).also { collect(it, it.state) }

    private fun scheduleVm(): ScheduleViewModel = ScheduleViewModel(repository, ScheduleRepository(db, time.provider), time.provider)
        .also { collect(it, it.state) }

    private fun <S> await(state: StateFlow<S>, predicate: (S) -> Boolean): S = runBlocking { withTimeout(10_000) { state.first(predicate) } }

    private fun onMain(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    /** An hour of [category] at 20:00 on each of the [days] days before today. */
    private fun record(days: IntRange, category: String = "phone_internet", startHour: Int = 20, hours: Long = 1) = runBlocking {
        days.forEach { back ->
            val d = today.minusDays(back.toLong())
            val start = d.atTime(startHour, 0).atZone(zone).toInstant()
            repository.save(EntryDraft(null, TimeEntryKind.ACTUAL, category, null, start, start.plusSeconds(hours * 3600)), Resolution.None)
        }
    }

    private fun scheduleCount() = runBlocking { db.timeDao().scheduleCount() }

    @Test
    fun insightsStayHiddenUntilSevenRecordedDays() {
        record(1..6)
        val vm = insights()
        assertEquals(InsightsUiState.Locked(6), await(vm.state) { it.content is InsightsUiState.Locked }.content)
        record(7..7)
        val ready = await(vm.state) { it.content is InsightsUiState.Ready }.content as InsightsUiState.Ready
        assertEquals(7 * 60L, ready.report.recordedMinutes)
    }

    @Test
    fun aDistractionFlagChangeUpdatesInsightsAtOnce() {
        record(1..7)
        val vm = insights()
        val before = await(vm.state) { it.content is InsightsUiState.Ready }.content as InsightsUiState.Ready
        assertEquals(7 * 60L, before.report.distractionMinutes)
        onMain { vm.setDistraction("phone_internet", false) }
        val after = await(vm.state) { (it.content as? InsightsUiState.Ready)?.report?.distractionMinutes == 0L }.content as InsightsUiState.Ready
        assertTrue(after.report.distractionCategories.isEmpty())
        assertEquals(7 * 60L, after.report.recordedMinutes)
    }

    @Test
    fun theInsightsPeriodIsRemembered() {
        record(1..10)
        val vm = insights()
        await(vm.state) { (it.content as? InsightsUiState.Ready)?.report?.period == InsightsPeriod.Week }
        onMain { vm.setPeriod(InsightsPeriod.FourWeeks) }
        val four = await(vm.state) { (it.content as? InsightsUiState.Ready)?.report?.period == InsightsPeriod.FourWeeks }
        assertEquals(10 * 60L, (four.content as InsightsUiState.Ready).report.recordedMinutes)
    }

    @Test
    fun aSuggestionIsSavedOnlyWhenApproved() {
        record(1..10, category = "work", startHour = 9, hours = 3)
        val vm = scheduleVm()
        val proposal = await(vm.state) { it.content is ScheduleContentUi.Proposal }.content as ScheduleContentUi.Proposal
        assertTrue(proposal.blocks.isNotEmpty())
        assertEquals(0, scheduleCount())

        // Editing changes nothing stored.
        onMain {
            vm.setName("Weekdays")
            vm.openBlock(proposal.blocks.first().id)
            vm.deleteBlock()
        }
        await(vm.state) { (it.content as ScheduleContentUi.Proposal).blocks.size == proposal.blocks.size - 1 }
        assertEquals(0, scheduleCount())
        onMain { vm.undoDelete(vm.state.value.undoToken!!) }
        await(vm.state) { (it.content as ScheduleContentUi.Proposal).blocks.size == proposal.blocks.size }
        assertEquals(0, scheduleCount())

        onMain { vm.approve() }
        await(vm.state) { it.approved }
        assertEquals(1, scheduleCount())
        val stored = runBlocking { repository.observeSchedules().first() }.single()
        assertEquals("Weekdays", stored.schedule.name)
        assertEquals(today, stored.schedule.activeFrom)
        assertEquals(proposal.blocks.size, stored.blocks.size)
    }

    @Test
    fun leavingAnEditedSuggestionAsksFirstAndSavesNothing() {
        record(1..10, category = "work", startHour = 9, hours = 3)
        val vm = scheduleVm()
        await(vm.state) { it.content is ScheduleContentUi.Proposal }
        // Unedited: leaving is immediate.
        var leave = false
        onMain { leave = vm.requestLeave() }
        assertTrue(leave)
        onMain {
            vm.setName("Changed")
            leave = vm.requestLeave()
        }
        assertFalse(leave)
        assertTrue(await(vm.state) { it.confirmDiscard }.confirmDiscard)
        assertEquals(0, scheduleCount())
    }

    @Test
    fun tooFewRecordedDaysSuggestNothing() {
        record(1..5, category = "work", startHour = 9, hours = 3)
        val vm = scheduleVm()
        assertEquals(ScheduleContentUi.NotEnough(5), await(vm.state) { it.content is ScheduleContentUi.NotEnough }.content)
        assertEquals(0, scheduleCount())
    }
}
