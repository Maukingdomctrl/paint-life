package com.mauforge.paintlife.debug

import com.mauforge.paintlife.core.database.SeedData
import com.mauforge.paintlife.exercise.ActiveSessionSummary
import com.mauforge.paintlife.exercise.ChallengeCompleteUiState
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.exercise.ExerciseHistoryUiState
import com.mauforge.paintlife.exercise.HistoryDay
import com.mauforge.paintlife.exercise.HistoryEditorUi
import com.mauforge.paintlife.exercise.HistoryRow
import com.mauforge.paintlife.exercise.ExerciseSessionType
import com.mauforge.paintlife.exercise.ExerciseSessionUiState
import com.mauforge.paintlife.exercise.ExerciseUiState
import com.mauforge.paintlife.exercise.LeagueEntry
import com.mauforge.paintlife.exercise.LevelStatus
import com.mauforge.paintlife.exercise.VictoryUiState
import com.mauforge.paintlife.exercise.cats.CatRoster
import com.mauforge.paintlife.mytime.CategoryOption
import com.mauforge.paintlife.mytime.LogActivityUiState
import com.mauforge.paintlife.mytime.MinuteSpan
import com.mauforge.paintlife.mytime.MyTimeUiState
import com.mauforge.paintlife.mytime.NowDoingUi
import com.mauforge.paintlife.mytime.OverlapOutcome
import com.mauforge.paintlife.mytime.OverlapRow
import com.mauforge.paintlife.mytime.OverlapUiState
import com.mauforge.paintlife.mytime.QuickActivity
import com.mauforge.paintlife.mytime.TimeLayer
import com.mauforge.paintlife.mytime.TimelineBlock
import com.mauforge.paintlife.mytime.TimelineGeometry
import com.mauforge.paintlife.progress.CategoryTotal
import com.mauforge.paintlife.progress.DayDistribution
import com.mauforge.paintlife.progress.DistributionBlock
import com.mauforge.paintlife.progress.ExerciseReport
import com.mauforge.paintlife.progress.ProgressPeriod
import com.mauforge.paintlife.progress.ProgressUiState
import com.mauforge.paintlife.progress.QuietReport
import com.mauforge.paintlife.progress.QuietDayFill
import com.mauforge.paintlife.progress.QuietWeek
import com.mauforge.paintlife.progress.TimeReport
import com.mauforge.paintlife.progress.TimeSection
import com.mauforge.paintlife.quiet.QuietDay
import com.mauforge.paintlife.quiet.QuietPhase
import com.mauforge.paintlife.quiet.QuietUiState
import com.mauforge.paintlife.core.time.DayPart
import com.mauforge.paintlife.today.CarryOverUi
import com.mauforge.paintlife.today.CategoryMinutes
import com.mauforge.paintlife.today.ExerciseToday
import com.mauforge.paintlife.today.PriorityItem
import com.mauforge.paintlife.today.QuickAction
import com.mauforge.paintlife.today.QuietToday
import com.mauforge.paintlife.today.SleepCardUi
import com.mauforge.paintlife.today.TodayStripBlock
import com.mauforge.paintlife.today.TodayUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

// SAMPLE STATES FOR DESIGN REVIEW ONLY.
// These live in the debug source set, so release builds cannot contain them. They feed the
// design gallery, @Preview functions and layout tests. None of it is real data.

private val SampleZone: ZoneId = ZoneId.of("Europe/London")

/** A Thursday, so the sample week has days on both sides. */
private val SampleDay: LocalDate = LocalDate.of(2026, 11, 12)

private fun min(h: Int, m: Int = 0) = h * 60 + m
private fun clock(minuteOfDay: Int) = "%02d:%02d".format(Locale.ROOT, minuteOfDay / 60 % 24, minuteOfDay % 60)
private fun minutes(m: Int, s: Int = 0) = TimeUnit.MINUTES.toMillis(m.toLong()) + TimeUnit.SECONDS.toMillis(s.toLong())

object TodaySamples {
    private val AllQuickActions = QuickAction.entries.toList()

    val emptyMorning = TodayUiState(
        date = SampleDay,
        dayPart = DayPart.Morning,
        exercise = ExerciseToday(reps = 0, sessions = 0),
        quiet = QuietToday(completed = false, minutes = 0, fill = 0f),
        timeByCategory = emptyList(),
        priorities = emptyList(),
        strip = listOf(TodayStripBlock(0, min(7, 10), null)),
        quickActions = AllQuickActions,
        sleepCard = SleepCardUi("23:10", "06:55"),
    )

    val partlyPainted = TodayUiState(
        date = SampleDay,
        dayPart = DayPart.Afternoon,
        exercise = ExerciseToday(reps = 120, sessions = 1),
        quiet = QuietToday(completed = false, minutes = 3, fill = 0.6f),
        timeByCategory = listOf(
            CategoryMinutes("Sleep", "night", 420),
            CategoryMinutes("Personal care", "sage", 45),
            CategoryMinutes("Exercise", "forest", 25),
            CategoryMinutes("Work", "slate", 210),
        ),
        priorities = listOf(
            PriorityItem("p1", "Call the bank", done = true),
            PriorityItem("p2", "Walk to the market", done = false),
            PriorityItem("p3", "Read one chapter", done = false),
        ),
        strip = listOf(
            TodayStripBlock(0, min(7), "night"),
            TodayStripBlock(min(7), min(7, 45), "sage"),
            TodayStripBlock(min(7, 45), min(8, 30), null),
            TodayStripBlock(min(8, 30), min(8, 55), "forest"),
            TodayStripBlock(min(8, 55), min(9), null),
            TodayStripBlock(min(9), min(12, 30), "slate"),
            TodayStripBlock(min(12, 30), min(13, 50), null),
        ),
        quickActions = AllQuickActions,
    )

    val twoPriorities = partlyPainted.copy(priorities = partlyPainted.priorities.take(2))

    val fullyPainted = TodayUiState(
        date = SampleDay,
        dayPart = DayPart.Evening,
        exercise = ExerciseToday(reps = 300, sessions = 2),
        quiet = QuietToday(completed = true, minutes = 5, fill = 1f),
        timeByCategory = listOf(
            CategoryMinutes("Sleep", "night", 420),
            CategoryMinutes("Personal care", "sage", 60),
            CategoryMinutes("Exercise", "forest", 50),
            CategoryMinutes("Work", "slate", 420),
            CategoryMinutes("Family", "rose", 120),
            CategoryMinutes("Phone/internet", "amber", 45),
            CategoryMinutes("Rest", "moss", 60),
        ),
        priorities = partlyPainted.priorities.map { it.copy(done = true) },
        strip = listOf(
            TodayStripBlock(0, min(7), "night"),
            TodayStripBlock(min(7), min(8), "sage"),
            TodayStripBlock(min(8), min(8, 50), "forest"),
            TodayStripBlock(min(8, 50), min(9), null),
            TodayStripBlock(min(9), min(16), "slate"),
            TodayStripBlock(min(16), min(18), "rose"),
            TodayStripBlock(min(18), min(18, 45), "amber"),
            TodayStripBlock(min(18, 45), min(19, 45), "moss"),
            TodayStripBlock(min(19, 45), min(20, 30), null),
        ),
        quickActions = AllQuickActions,
    )

    /** A new day with the offer to bring over yesterday's unfinished priorities. */
    val carryOverOffer = TodayUiState(
        date = SampleDay,
        dayPart = DayPart.Morning,
        priorities = listOf(PriorityItem("p4", "Water the plants", done = false)),
        carryOver = CarryOverUi(
            sourceDate = SampleDay.minusDays(1),
            sourceIsYesterday = true,
            items = listOf(
                PriorityItem("y2", "Walk to the market", done = false),
                PriorityItem("y3", "Read one chapter", done = false),
            ),
            freePlaces = 2,
        ),
    )
}

object ExerciseSamples {
    private val benchmarks = SeedData.levels.associate { it.level to TimeUnit.MINUTES.toMillis(it.benchmarkMinutes) }

    /** [beaten] levels beaten, the next one current, the rest locked. */
    fun league(beaten: Int, bests: Map<Int, Long> = emptyMap()): List<LeagueEntry> = CatRoster.cats.map { cat ->
        LeagueEntry(
            cat = cat,
            benchmarkMillis = benchmarks.getValue(cat.level),
            status = when {
                cat.level <= beaten -> LevelStatus.Beaten
                cat.level == beaten + 1 -> LevelStatus.Current
                else -> LevelStatus.Locked
            },
            personalBestMillis = bests[cat.level],
        )
    }

    private val midwayBests = mapOf(1 to minutes(28, 47), 2 to minutes(28, 12), 3 to minutes(28, 31))

    val firstDay = ExerciseUiState(lifetimeReps = 0, league = league(0), activeSession = null, hasReadSafety = false)

    val midway = ExerciseUiState(lifetimeReps = 640, league = league(2, midwayBests), activeSession = null, hasReadSafety = true)

    val restoredSession = midway.copy(
        activeSession = ActiveSessionSummary(ExerciseSessionType.VOLUME, reps = 85, activeMillis = minutes(6, 12), isPaused = true, opponentLevel = null),
    )

    val allBeaten = ExerciseUiState(
        lifetimeReps = 12_450,
        league = league(10, (1..10).associateWith { minutes(30 - it, 0) - minutes(0, 40) }),
        activeSession = null,
        hasReadSafety = true,
    )

    val volumeRunning = ExerciseSessionUiState(ExerciseSessionType.VOLUME, reps = 120, elapsedMillis = minutes(9, 41), isPaused = false, opponent = null)
    val volumePaused = volumeRunning.copy(isPaused = true)
    val timedRunning = ExerciseSessionUiState(
        ExerciseSessionType.TIMED_CHALLENGE,
        reps = 640,
        elapsedMillis = minutes(18, 22),
        isPaused = false,
        opponent = midway.league[2],
    )
    val timedPaused = timedRunning.copy(isPaused = true, elapsedMillis = minutes(19, 5))

    /** A session left open last night, restored as paused. */
    val restoredOvernight = midway.copy(
        activeSession = ActiveSessionSummary(
            ExerciseSessionType.VOLUME,
            reps = 40,
            activeMillis = minutes(3, 5),
            isPaused = true,
            opponentLevel = null,
            startedOn = LocalDate.of(2026, 11, 8),
        ),
    )

    private val historyToday = LocalDate.of(2026, 11, 9)

    val history = ExerciseHistoryUiState(
        today = historyToday,
        days = listOf(
            HistoryDay(
                historyToday,
                listOf(
                    HistoryRow("h1", ExerciseSessionType.VOLUME, SessionStatus.COMPLETED, null, LocalTime.of(18, 40), 120, minutes(11, 2), "Felt easy, longer next time."),
                    HistoryRow("t1", ExerciseSessionType.TIMED_CHALLENGE, SessionStatus.COMPLETED, CatRoster.forLevel(3), LocalTime.of(12, 5), 1_000, minutes(27, 48) + 300, null),
                    HistoryRow("h2", ExerciseSessionType.VOLUME, SessionStatus.COMPLETED, null, LocalTime.of(7, 15), 60, minutes(5, 30), null),
                ),
            ),
            HistoryDay(
                historyToday.minusDays(1),
                listOf(
                    HistoryRow("h3", ExerciseSessionType.VOLUME, SessionStatus.COMPLETED, null, LocalTime.of(23, 50), 85, minutes(9, 12), "Ran past midnight; counts on the day it started."),
                    HistoryRow("t2", ExerciseSessionType.TIMED_CHALLENGE, SessionStatus.CANCELLED, CatRoster.forLevel(3), LocalTime.of(19, 0), 640, minutes(18, 2), "Legs said no today."),
                ),
            ),
            HistoryDay(
                historyToday.minusDays(5),
                listOf(
                    HistoryRow("h4", ExerciseSessionType.VOLUME, SessionStatus.COMPLETED, null, LocalTime.of(17, 5), 1, minutes(0, 20), null),
                    HistoryRow("t3", ExerciseSessionType.TIMED_CHALLENGE, SessionStatus.INCOMPLETE, CatRoster.forLevel(3), LocalTime.of(6, 30), 410, minutes(11, 0), null),
                ),
            ),
        ),
    )

    val historyEmpty = ExerciseHistoryUiState(today = historyToday, days = emptyList())

    val historyEditor = HistoryEditorUi(
        id = "h1",
        date = historyToday,
        startTime = LocalTime.of(18, 40),
        reps = 120,
        note = "Felt easy, longer next time.",
        canEditReps = true,
        canDelete = true,
    )

    val catLocked = midway.league[3]
    val catCurrent = midway.league[2]
    val catBeaten = midway.league[0]

    val victory = VictoryUiState(beaten = midway.league[2], yourMillis = minutes(27, 48), unlocked = CatRoster.forLevel(4))
    val victoryChampion = VictoryUiState(beaten = allBeaten.league[9], yourMillis = minutes(20, 51), unlocked = null)
    val victoryReplay = VictoryUiState(beaten = midway.league[0], yourMillis = minutes(28, 30), unlocked = null, isReplay = true, bestMillis = minutes(28, 30))

    val completeSlower = ChallengeCompleteUiState(opponent = midway.league[2], yourMillis = minutes(28, 0) + 400, bestMillis = minutes(28, 0) + 400)
    val completeTie = ChallengeCompleteUiState(opponent = midway.league[2], yourMillis = minutes(28, 0), bestMillis = minutes(28, 0))
}

object QuietSamples {
    private val week: List<QuietDay> = (0L..6L).map { offset ->
        val date = SampleDay.minusDays(3).plusDays(offset)
        when (offset) {
            0L, 1L -> QuietDay(date, completed = true, minutes = 5, fill = 1f)
            2L -> QuietDay(date, completed = false, minutes = 3, fill = 0.6f)
            else -> QuietDay(date, completed = false)
        }
    }

    val ready = QuietUiState(QuietPhase.Ready, QuietUiState.FIVE_MINUTES, week, monthCompletedSessions = 7, monthMinutes = 35)
    val running = ready.copy(phase = QuietPhase.Running, remainingMillis = minutes(2, 13))
    val paused = ready.copy(phase = QuietPhase.Paused, remainingMillis = minutes(3, 40))
    val completed = ready.copy(
        phase = QuietPhase.Completed,
        remainingMillis = 0,
        week = week.map { if (it.date == SampleDay) it.copy(completed = true, minutes = 5, fill = 1f) else it },
        monthCompletedSessions = 8,
        monthMinutes = 40,
    )
}

object MyTimeSamples {
    private fun block(id: String, start: Int, end: Int, key: String, name: String, distraction: Boolean = false) =
        TimelineBlock(id, MinuteSpan(start, end), clock(start), clock(end), name, key, distraction)

    private val day3Actual = listOf(
        block("a1", 0, min(6, 50), "night", "Sleep"),
        block("a2", min(6, 50), min(7, 30), "sage", "Personal care"),
        block("a3", min(7, 30), min(7, 55), "amber", "Phone/internet", distraction = true),
        block("a4", min(8, 15), min(8, 40), "forest", "Exercise"),
        block("a5", min(9), min(12, 30), "slate", "Work"),
        block("a6", min(13, 15), min(14, 10), "teal", "Learning"),
    )

    private val planned = listOf(
        block("p1", min(8), min(8, 30), "forest", "Exercise"),
        block("p2", min(9), min(12), "slate", "Work"),
        block("p3", min(13), min(14), "teal", "Learning"),
        block("p4", min(18), min(19), "rose", "Family"),
    )

    private val marks = TimelineGeometry.hourMarks(SampleDay, SampleZone)

    val observationEmpty = MyTimeUiState(
        date = SampleDay,
        isToday = true,
        layer = TimeLayer.Actual,
        actual = emptyList(),
        planned = emptyList(),
        hourMarks = marks,
        dayLengthMinutes = 1440,
        nowMinute = min(9, 10),
        observedDays = 0,
        insightsReady = false,
        layersAvailable = false,
        quickActivities = quickActivities,
    )

    val observationDay3 = observationEmpty.copy(
        actual = day3Actual,
        nowMinute = min(14, 30),
        observedDays = 3,
        nowDoing = NowDoingUi("Learning", "teal", "Kotlin course", "13:15", 75),
    )

    val afterObservationBoth = observationDay3.copy(
        layer = TimeLayer.Both,
        planned = planned,
        observedDays = null,
        insightsReady = true,
        layersAvailable = true,
        nowDoing = null,
    )

    val pastDayPlanned = afterObservationBoth.copy(
        date = SampleDay.minusDays(1),
        isToday = false,
        layer = TimeLayer.Planned,
        nowMinute = null,
        insightsReady = false,
    )

    /** 25 October 2026, when UK clocks go back: a 25-hour day with 01:00 shown twice. */
    val dstFallBack: MyTimeUiState = run {
        val date = LocalDate.of(2026, 10, 25)
        fun at(day: LocalDate, h: Int, m: Int) = ZonedDateTime.of(day, LocalTime.of(h, m), SampleZone).toInstant()
        val sleep = TimelineGeometry.clipToDay(at(date.minusDays(1), 23, 0), at(date, 7, 30), date, SampleZone)!!
        val reading = TimelineGeometry.clipToDay(at(date, 9, 0), at(date, 10, 15), date, SampleZone)!!
        MyTimeUiState(
            date = date,
            isToday = false,
            layer = TimeLayer.Actual,
            actual = listOf(
                TimelineBlock("d1", sleep, "23:00", "07:30", "Sleep", "night", false, continuesBefore = true),
                TimelineBlock("d2", reading, "09:00", "10:15", "Learning", "teal", false),
            ),
            planned = emptyList(),
            hourMarks = TimelineGeometry.hourMarks(date, SampleZone),
            dayLengthMinutes = TimelineGeometry.dayLengthMinutes(date, SampleZone),
            nowMinute = null,
            observedDays = null,
            insightsReady = false,
            layersAvailable = false,
        )
    }

    private val categories = SeedData.categories.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) }

    private val quickActivities: List<QuickActivity>
        get() = listOf(
            QuickActivity("work", "Work", "slate", "emails"),
            QuickActivity("learning", "Learning", "teal", "Kotlin course"),
        ) + SeedData.categories.map { QuickActivity(it.id, it.name, it.colorKey, null) }

    val logNew = LogActivityUiState(
        isEditing = false,
        layer = TimeLayer.Actual,
        categories = categories,
        selectedCategoryId = null,
        startLabel = "09:00",
        endLabel = "10:30",
        endsNextDay = false,
        durationMinutes = 90,
        recent = listOf(QuickActivity("work", "Work", "slate", "emails"), QuickActivity("learning", "Learning", "teal", "Kotlin course")),
        layersAvailable = false,
    )

    val logEditOvernight = logNew.copy(
        isEditing = true,
        selectedCategoryId = "sleep",
        startLabel = "22:30",
        endLabel = "07:00",
        endsNextDay = true,
        durationMinutes = 510,
    )

    val overlap = OverlapUiState(
        newCategoryName = "Phone/internet",
        newRange = "12:15–13:00",
        neighbours = listOf(
            OverlapRow("Work", "09:00–12:30", OverlapOutcome.Trimmed, listOf("09:00–12:15")),
            OverlapRow("Personal care", "12:30–12:45", OverlapOutcome.Removed, emptyList()),
        ),
    )

    /** A short call in the middle of a work block: the block is split around it. */
    val overlapSplit = OverlapUiState(
        newCategoryName = "Family",
        newRange = "10:00–10:20",
        neighbours = listOf(OverlapRow("Work", "09:00–12:30", OverlapOutcome.Split, listOf("09:00–10:00", "10:20–12:30"))),
    )

    /** Confirming a "now doing" activity left running overnight. */
    val confirmRunning = logNew.copy(
        selectedCategoryId = "work",
        note = "emails",
        startLabel = "09:10",
        endLabel = "03:10",
        endsNextDay = true,
        durationMinutes = 18 * 60,
        confirmingRunning = true,
    )
}

object ProgressSamples {
    private val cats = CatRoster.cats

    private fun distribution(date: LocalDate, variant: Int): DayDistribution {
        val workEnd = min(16 + variant % 3)
        return DayDistribution(
            date = date,
            blocks = listOf(
                DistributionBlock(0, min(7), "night"),
                DistributionBlock(min(7), min(7, 45), "sage"),
                DistributionBlock(min(7, 45), min(9), if (variant % 2 == 0) null else "forest"),
                DistributionBlock(min(9), workEnd, "slate"),
                DistributionBlock(workEnd, min(19), if (variant % 3 == 0) null else "rose"),
                DistributionBlock(min(19), min(21), "plum"),
                DistributionBlock(min(21), min(22), "amber"),
                DistributionBlock(min(22), min(23), null),
                DistributionBlock(min(23), min(24), "night"),
            ),
        )
    }

    private val weekDays = (6L downTo 0L).map { SampleDay.minusDays(it) }

    private val weekCategories = listOf(
        CategoryTotal("Sleep", "night", 3360, 3360, false),
        CategoryTotal("Work", "slate", 2100, 2400, false),
        CategoryTotal("Entertainment", "plum", 840, null, true),
        CategoryTotal("Family", "rose", 600, 840, false),
        CategoryTotal("Phone/internet", "amber", 420, 210, true),
        CategoryTotal("Personal care", "sage", 315, null, false),
        CategoryTotal("Exercise", "forest", 225, 210, false),
    )

    private fun quietDays(days: List<LocalDate>, done: (Int) -> Boolean) = days.mapIndexed { i, d ->
        when {
            done(i) -> QuietDayFill(d, completed = true, fill = 1f, minutes = 5)
            i % 5 == 1 -> QuietDayFill(d, completed = false, fill = 0.6f, minutes = 3)
            else -> QuietDayFill(d, completed = false, fill = 0f, minutes = 0)
        }
    }

    private fun quietReport(days: List<QuietDayFill>, weeks: List<QuietWeek> = emptyList()) = QuietReport(
        completedSessions = days.count { it.completed },
        totalMinutes = days.sumOf { it.minutes },
        days = days,
        daysWithFull = days.count { it.completed },
        countedDays = days.size,
        weeks = weeks,
    )

    private val weekTime = TimeReport(
        categories = weekCategories.map { it.copy(plannedMinutes = null) },
        unrecordedMinutes = 610,
        days = weekDays.mapIndexed { i, d -> distribution(d, i) },
        planned = weekCategories.filter { it.plannedMinutes != null },
        plannedDays = 5,
    )

    private fun exercise(reps: Int, sessions: Int, unlocked: Int, beaten: Int, beatenNow: Int?) = ExerciseReport(
        totalReps = reps,
        sessions = sessions,
        averageSessionMillis = minutes(14, 20),
        mostReps = 220,
        mostRepsIsRecord = true,
        bestTimedMillis = minutes(28, 31),
        bestTimedAgainst = cats[beaten.coerceAtMost(cats.lastIndex)],
        bestTimedIsRecord = false,
        unlockedCats = cats.take(unlocked),
        levelsBeaten = beaten,
        beatenInPeriod = listOfNotNull(beatenNow?.let { cats[it] }),
    )

    val todayEmpty = ProgressUiState(
        period = ProgressPeriod.Today,
        start = SampleDay,
        end = SampleDay,
        exercise = ExerciseReport(0, 0, null, null, false, null, null, false, unlockedCats = cats.take(1), levelsBeaten = 0, beatenInPeriod = emptyList()),
        quiet = quietReport(emptyList()),
        time = TimeSection.Ready(TimeReport(emptyList(), 0, emptyList())),
    )

    val lastSevenDays = ProgressUiState(
        period = ProgressPeriod.Last7Days,
        start = weekDays.first(),
        end = weekDays.last(),
        exercise = exercise(540, 5, unlocked = 3, beaten = 2, beatenNow = 1),
        quiet = quietReport(quietDays(weekDays) { it % 2 == 0 }),
        time = TimeSection.Ready(weekTime),
    )

    /** A multi-day period before the observation week is over. */
    val lastSevenDaysObserving = lastSevenDays.copy(time = TimeSection.Locked(recordedDays = 4))

    val thisMonth: ProgressUiState = run {
        val days = (1..12).map { LocalDate.of(2026, 11, it) }
        val fills = quietDays(days) { it % 3 != 2 }
        lastSevenDays.copy(
            period = ProgressPeriod.ThisMonth,
            start = days.first(),
            end = days.last(),
            quiet = quietReport(
                fills,
                listOf(
                    QuietWeek(days[0], days[0], 1, 1),
                    QuietWeek(days[1], days[7], 5, 7),
                    QuietWeek(days[8], days[11], 3, 4),
                ),
            ),
            time = TimeSection.Ready(weekTime.copy(days = days.mapIndexed { i, d -> distribution(d, i) })),
        )
    }

    val lastMonth: ProgressUiState = run {
        val days = (1..31).map { LocalDate.of(2026, 10, it) }
        lastSevenDays.copy(
            period = ProgressPeriod.LastMonth,
            start = days.first(),
            end = days.last(),
            exercise = exercise(2_310, 19, unlocked = 2, beaten = 1, beatenNow = null),
            quiet = quietReport(quietDays(days) { it % 4 != 1 }),
            time = TimeSection.Ready(weekTime.copy(days = days.mapIndexed { i, d -> distribution(d, i) })),
        )
    }
}
