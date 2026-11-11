package com.mauforge.paintlife.today

import com.mauforge.paintlife.core.backup.DataReplaced
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.mytime.EntryDraft
import com.mauforge.paintlife.mytime.Resolution
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRules
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.mytime.LogRequest
import com.mauforge.paintlife.mytime.MyTimeLaunch
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.testing.testPreferences
import com.mauforge.paintlife.core.time.DayPart
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The ViewModel on a real (in-memory) database and a hand-driven clock. Main is a real single
 * thread, so database work and state updates run as they do in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {

    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(9, 0), ZoneId.of("Asia/Kolkata"))
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TodayRepository
    private lateinit var viewModel: TodayViewModel
    private lateinit var collector: Job
    private val launch = MyTimeLaunch()
    private val replaced = DataReplaced()

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = TodayRepository(db, time.provider)
        viewModel = TodayViewModel(
            repository,
            ExerciseRepository(db, time.provider),
            QuietRepository(db, time.provider),
            TimeRepository(db, time.provider),
            launch,
            testPreferences(ApplicationProvider.getApplicationContext()),
            time.provider,
            time.dayClock,
            replaced,
        )
        // The screen collects state while visible; do the same here.
        collector = CoroutineScope(main).launch { viewModel.state.collect { } }
    }

    @After
    fun tearDown() {
        // Stop everything running on Main before Main is reset.
        runBlocking {
            collector.cancelAndJoin()
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun onMain(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun awaitState(predicate: (TodayScreenState) -> Boolean): TodayScreenState = runBlocking {
        withTimeout(5_000) { viewModel.state.first(predicate) }
    }

    private fun stored(date: LocalDate) = runBlocking { repository.observePriorities(date).first() }

    @Test
    fun showsTodayWithTheGreetingForTheTimeOfDay() {
        val state = awaitState { it.content != null }
        assertEquals(day, state.content?.date)
        assertEquals(DayPart.Morning, state.content?.dayPart)
    }

    @Test
    fun everyAreaIsTrackedAndEveryActionIsOffered() {
        val content = awaitState { it.content?.strip != null }.content!!
        assertEquals(ExerciseToday(reps = 0, sessions = 0), content.exercise)
        assertEquals(QuietToday(completed = false, minutes = 0, fill = 0f), content.quiet)
        assertEquals(emptyList<CategoryMinutes>(), content.timeByCategory)
        // 09:00 with nothing recorded: the whole morning is one unrecorded stretch.
        assertEquals(listOf(TodayStripBlock(0, 540, null)), content.strip)
        assertEquals(listOf(QuickAction.StartExercise, QuickAction.QuietTime, QuickAction.LogTime, QuickAction.Sleep), content.quickActions)
    }

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun moveTo(dateTime: LocalDateTime) {
        time.setLocal(dateTime)
        time.announceChange()
    }

    /**
     * 23:14 on [day]. The screen is showing first, so the clock is already listening for the
     * "time changed" broadcast and cannot miss it.
     */
    private fun moveToEvening() {
        awaitState { it.content != null }
        moveTo(day.atTime(23, 14))
        awaitState { it.content?.dayPart == DayPart.Evening }
    }

    private fun runningActivity() = runBlocking { TimeRepository(db, time.provider).observeCurrent().first() }

    @Test
    fun sleepShowsSleepingSinceWithOnlyWakeUpAndRepeatedTapsStartItOnce() {
        moveToEvening()

        onMain {
            viewModel.startSleep()
            viewModel.startSleep()
            viewModel.startSleep()
        }

        val state = awaitState { it.content?.sleeping != null && it.sleepUndo != null }
        val content = state.content!!
        assertEquals(SleepingUi("23:14", 0, askWakeTime = false), content.sleeping)
        assertEquals(listOf(QuickAction.StartExercise, QuickAction.QuietTime, QuickAction.LogTime), content.quickActions)
        assertNull(content.nowDoing)
        assertNull(content.sleepCard)
        assertEquals(SleepUndoUi.Kind.Asleep, state.sleepUndo!!.kind)
        assertEquals(day.atTime(23, 14).atZone(zone).toInstant(), runningActivity()?.startAt)
    }

    @Test
    fun undoAfterSleepPutsTheButtonBackAndASecondUndoDoesNothing() {
        awaitState { it.content != null }
        onMain { viewModel.startSleep() }
        val undo = awaitState { it.content?.sleeping != null && it.sleepUndo != null }.sleepUndo!!

        onMain {
            viewModel.undoSleep(undo.token)
            viewModel.undoSleep(undo.token)
        }

        val content = awaitState { it.sleepUndo == null && it.content?.sleeping == null }.content!!
        assertEquals(true, QuickAction.Sleep in content.quickActions)
        assertNull(runningActivity())
    }

    @Test
    fun wakeUpSavesTheNightAndTheMorningCardStaysHidden() = runBlocking<Unit> {
        moveToEvening()
        onMain { viewModel.startSleep() }
        awaitState { it.content?.sleeping != null }

        // 07:10 the next morning: still asleep as far as the app knows, and no card to log sleep.
        val next = day.plusDays(1)
        moveTo(next.atTime(7, 10))
        val morning = awaitState { it.content?.date == next && it.content.sleeping != null }.content!!
        assertEquals(SleepingUi("23:14", 476, askWakeTime = false), morning.sleeping)
        assertNull(morning.sleepCard)

        onMain {
            viewModel.wakeUp()
            viewModel.wakeUp()
        }

        val awake = awaitState { it.sleepUndo?.kind == SleepUndoUi.Kind.Awake && it.content?.sleeping == null && it.content?.sleepCard == null }
        assertEquals(true, QuickAction.Sleep in awake.content!!.quickActions)
        val saved = TimeRepository(db, time.provider).observeDayEntries(next).first().single().entry
        assertEquals("sleep", saved.categoryId)
        assertEquals("23:14" to "07:10", TimeRules.wallTime(saved.startAt, zone).text to TimeRules.wallTime(saved.endAt, zone).text)

        // Undo: asleep again since 23:14, nothing saved.
        onMain { viewModel.undoSleep(awake.sleepUndo!!.token) }
        awaitState { it.content?.sleeping?.sinceLabel == "23:14" }
        assertEquals(emptyList<Any>(), TimeRepository(db, time.provider).observeDayEntries(next).first())
    }

    @Test
    fun aForgottenWakeUpAsksForTheWakeTimeInMyTimesSheet() {
        moveToEvening()
        onMain { viewModel.startSleep() }
        awaitState { it.content?.sleeping != null }

        // 10:30 the next morning, well past the usual 07:00.
        val next = day.plusDays(1)
        moveTo(next.atTime(10, 30))
        awaitState { it.content?.date == next && it.content.sleeping?.askWakeTime == true }

        onMain { viewModel.setWakeTime() }

        awaitState { it.openMyTime != null }
        val request = launch.request.value as LogRequest.Draft
        assertEquals(true, request.confirmingRunning)
        assertEquals(
            EntryDraft(null, TimeEntryKind.ACTUAL, "sleep", null, day.atTime(23, 14).atZone(zone).toInstant(), next.atTime(7, 0).atZone(zone).toInstant(), fromCurrent = true),
            request.draft,
        )
        // Nothing is saved until the sheet is: the sleep is still running.
        assertEquals("sleep", runningActivity()?.categoryId)
    }

    @Test
    fun sleepOverAVeryLongActivityAsksAboutItFirstAndThenStartsSleep() = runBlocking<Unit> {
        TimeRepository(db, time.provider).startCurrent("work", null)
        awaitState { it.content?.nowDoing != null }
        moveTo(day.plusDays(1).atTime(2, 0))
        awaitState { it.content?.date == day.plusDays(1) && it.content.nowDoing != null }

        onMain { viewModel.startSleep() }

        awaitState { it.openMyTime != null }
        val request = launch.request.value as LogRequest.Draft
        assertEquals(true, request.confirmingRunning)
        assertEquals("work", request.draft.categoryId)
        assertEquals("sleep", request.then?.categoryId)
        assertEquals("work", runningActivity()?.categoryId)
    }

    @Test
    fun anImportedBackupClearsASleepUndoFromBefore() {
        awaitState { it.content != null }
        onMain { viewModel.startSleep() }
        val token = awaitState { it.sleepUndo != null }.sleepUndo!!.token

        replaced.signal()
        awaitState { it.sleepUndo == null }
        onMain { viewModel.undoSleep(token) }

        // The old Undo is gone: whatever is running now is left alone.
        assertEquals("sleep", runningActivity()?.categoryId)
    }

    @Test
    fun aZoneChangeOnTheSameDateRedrawsTodaysTimeInTheNewZone() = runBlocking<Unit> {
        // 09:00 in Kolkata, with an hour of work since 08:00.
        val zone = ZoneId.of("Asia/Kolkata")
        TimeRepository(db, time.provider).save(
            EntryDraft(null, TimeEntryKind.ACTUAL, "work", null, day.atTime(8, 0).atZone(zone).toInstant(), day.atTime(9, 0).atZone(zone).toInstant()),
            Resolution.None,
        )
        awaitState { it.content?.strip == listOf(TodayStripBlock(0, 480, null), TodayStripBlock(480, 540, "slate")) }

        // The phone moves to Tokyo, three and a half hours ahead: still 9 November, now 12:30.
        time.zone = ZoneId.of("Asia/Tokyo")
        time.announceChange()

        val content = awaitState { it.content?.strip?.lastOrNull()?.endMinute == 750 }.content!!
        assertEquals(day, content.date)
        assertEquals(listOf(TodayStripBlock(0, 690, null), TodayStripBlock(690, 750, "slate")), content.strip)
        assertEquals(DayPart.Afternoon, content.dayPart)
    }

    @Test
    fun theSleepCardNeverOffersSleepThatHasNotHappenedYet() = runBlocking<Unit> {
        // Up at 06:30, before the usual 07:00: the card offers the night up to now.
        time.setLocal(day.atTime(6, 30))
        time.announceChange()
        val card = awaitState { it.content?.sleepCard?.endLabel == "06:30" }.content!!.sleepCard!!
        assertEquals(SleepCardUi("23:00", "06:30"), card)

        onMain { viewModel.logSleep() }

        awaitState { it.notice?.kind == TodayNotice.Kind.SleepLogged }
        val zone = ZoneId.of("Asia/Kolkata")
        val saved = TimeRepository(db, time.provider).observeDayEntries(day).first().single().entry
        assertEquals("sleep", saved.categoryId)
        assertEquals("23:00" to "06:30", TimeRules.wallTime(saved.startAt, zone).text to TimeRules.wallTime(saved.endAt, zone).text)
    }

    @Test
    fun anImportedBackupClearsAnUndoFromBefore() = runBlocking<Unit> {
        awaitState { it.content != null }
        onMain { viewModel.openNewPriority() }
        onMain { viewModel.save("From before") }
        val id = awaitState { it.content?.priorities?.size == 1 }.content!!.priorities.single().id
        onMain { viewModel.delete(id) }
        val token = awaitState { it.undo != null }.undo!!.token

        // Every record is replaced by an import (here: the same data). The old Undo is gone.
        replaced.signal()
        awaitState { it.undo == null }
        onMain { viewModel.undoDelete(token) }

        assertEquals(emptyList<PriorityEntity>(), stored(day))
    }

    @Test
    fun atMidnightTheScreenMovesToTheNewDayAndOffersWhatIsStillOpen() {
        time.setLocal(day.atTime(23, 59))
        time.announceChange()
        awaitState { it.content?.dayPart == DayPart.Evening }
        onMain { viewModel.openNewPriority() }
        onMain { viewModel.save("Late idea") }
        awaitState { it.content?.priorities?.map(PriorityItem::title) == listOf("Late idea") }

        time.setLocal(LocalDateTime.of(2026, 11, 10, 0, 1))
        time.announceChange()

        val next = awaitState { it.content?.date == day.plusDays(1) }.content!!
        assertEquals(emptyList<PriorityItem>(), next.priorities)
        val offered = awaitState { it.content?.carryOver != null }.content!!.carryOver!!
        assertEquals(listOf("Late idea"), offered.items.map { it.title })
        assertEquals(true, offered.sourceIsYesterday)
        assertEquals(listOf("Late idea"), stored(day).map { it.title })
    }

    @Test
    fun crossingTheDateLineChangesTheDayWithoutTouchingTheOldOne() = runBlocking<Unit> {
        time.zone = ZoneId.of("Pacific/Honolulu")
        time.setLocal(LocalDateTime.of(2026, 11, 9, 1, 0))
        time.announceChange()
        awaitState { it.content?.date == day }
        repository.add(day, "Before the flight")

        time.zone = ZoneId.of("Pacific/Kiritimati")
        time.announceChange()

        awaitState { it.content?.date == day.plusDays(1) }
        assertEquals(listOf("Before the flight"), stored(day).map { it.title })
    }

    @Test
    fun repeatedSaveTapsAddOnePriority() {
        awaitState { it.content != null }
        onMain {
            viewModel.openNewPriority()
            viewModel.save("Call the bank")
            viewModel.save("Call the bank")
            viewModel.save("Call the bank")
        }
        awaitState { it.editor == null && it.content?.priorities?.isNotEmpty() == true }
        assertEquals(listOf("Call the bank"), stored(day).map { it.title })
    }

    @Test
    fun theAddSheetIsNotOfferedWhenTheDayIsFull() = runBlocking<Unit> {
        repeat(3) { repository.add(day, "P$it") }
        awaitState { it.content?.priorities?.size == 3 }
        onMain { viewModel.openNewPriority() }
        assertNull(awaitState { it.content?.priorities?.size == 3 }.editor)
    }

    @Test
    fun deleteOffersUndoAndASecondUndoTapDoesNothing() = runBlocking<Unit> {
        val priority = (repository.add(day, "Walk") as AddResult.Added).priority
        awaitState { it.content?.priorities?.size == 1 }

        onMain { viewModel.delete(priority.id) }
        val undo = awaitState { it.undo != null }.undo!!
        assertEquals("Walk", undo.title)
        assertEquals(emptyList<PriorityEntity>(), stored(day))

        onMain {
            viewModel.undoDelete(undo.token)
            viewModel.undoDelete(undo.token)
        }
        awaitState { it.undo == null && it.content?.priorities?.size == 1 }
        assertEquals(listOf(priority), stored(day))
    }

    @Test
    fun anExpiredUndoLeavesTheDeletionInPlace() = runBlocking<Unit> {
        val priority = (repository.add(day, "Walk") as AddResult.Added).priority
        awaitState { it.content?.priorities?.size == 1 }
        onMain { viewModel.delete(priority.id) }
        val undo = awaitState { it.undo != null }.undo!!

        onMain {
            viewModel.undoExpired(undo.token)
            viewModel.undoDelete(undo.token)
        }
        awaitState { it.undo == null }
        assertEquals(emptyList<PriorityEntity>(), stored(day))
    }

    @Test
    fun theEditSheetKnowsWhereThePriorityCanMove() = runBlocking<Unit> {
        val first = (repository.add(day, "A") as AddResult.Added).priority
        val second = (repository.add(day, "B") as AddResult.Added).priority
        awaitState { it.content?.priorities?.size == 2 }

        onMain { viewModel.openPriority(first.id) }
        val editor = awaitState { it.editor != null }.editor!!
        assertEquals(PriorityEditorUi(first.id, "A", canMoveUp = false, canMoveDown = true, saving = false), editor)

        onMain { viewModel.move(first.id, MoveDirection.Down) }
        val moved = awaitState { it.editor?.canMoveUp == true }
        assertEquals(listOf(second.id, first.id), moved.content!!.priorities.map { it.id })
    }

    @Test
    fun carryOverCopiesTheChosenOnesOnlyOnce() = runBlocking<Unit> {
        val walk = (repository.add(day.minusDays(1), "Walk") as AddResult.Added).priority
        repository.add(day.minusDays(1), "Read")
        awaitState { it.content?.carryOver?.items?.size == 2 }

        onMain {
            viewModel.openCarryOver()
            viewModel.carryOver(listOf(walk.id))
            viewModel.carryOver(listOf(walk.id))
        }
        awaitState {
            it.content?.priorities?.size == 1 && !it.carryOverPickerOpen &&
                it.content.carryOver?.items?.map(PriorityItem::title) == listOf("Read")
        }
        assertEquals(listOf("Walk"), stored(day).map { it.title })
    }

    @Test
    fun notNowHidesTheOffer() = runBlocking<Unit> {
        repository.add(day.minusDays(1), "Walk")
        awaitState { it.content?.carryOver != null }
        onMain { viewModel.dismissCarryOver() }
        awaitState { it.content != null && it.content.carryOver == null }
    }

    @Test
    fun exerciseSwatchShowsTheSessionsThatStartedTodayAndMovesOnAtMidnight() {
        val exercise = ExerciseRepository(db, time.provider)
        runBlocking {
            val session = exercise.startVolumeSession(SessionTimer.started(0, 1))
            exercise.finish(session.id, 40, SessionTimer.paused(session.timer, 300_000, 1))
            exercise.startVolumeSession(SessionTimer.started(400_000, 1))
        }

        val state = awaitState { it.content?.exercise?.reps == 40 }
        // The open session has no reps yet, so it doesn't paint the day.
        assertEquals(ExerciseToday(reps = 40, sessions = 1), state.content!!.exercise)
        assertEquals(listOf(QuickAction.StartExercise, QuickAction.QuietTime, QuickAction.LogTime, QuickAction.Sleep), state.content.quickActions)

        time.setLocal(day.plusDays(1).atTime(0, 1))
        time.announceChange()

        val tomorrow = awaitState { it.content?.date == day.plusDays(1) }
        assertEquals(ExerciseToday(reps = 0, sessions = 0), tomorrow.content!!.exercise)
    }
}
