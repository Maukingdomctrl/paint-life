package com.mauforge.paintlife.progress

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.SessionStatus
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.exercise.ExerciseSessionEntity
import com.mauforge.paintlife.exercise.ExerciseSessionType
import com.mauforge.paintlife.mytime.DayView
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeRepository
import com.mauforge.paintlife.quiet.QuietRepository
import com.mauforge.paintlife.quiet.QuietSessionEntity
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Five years of history: each period reads only its own days (through the date and start-time
 * indexes), so Progress stays quick however long the app has been used.
 */
@RunWith(AndroidJUnit4::class)
class ProgressLargeHistoryTest {

    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val today: LocalDate = LocalDate.of(2026, 11, 10)
    private val firstDay: LocalDate = today.minusYears(5)
    private val time = TestTime(today.atTime(18, 0), zone)
    private lateinit var db: PaintLifeDatabase

    // Each day: 8 time entries, 3 exercise sessions of 100 reps, 2 quiet sessions (one full).
    private val dayBlocks = listOf(0 to 7, 7 to 8, 9 to 12, 13 to 17, 17 to 18, 18 to 20, 20 to 22, 22 to 24)
    private val dayCategories = listOf("sleep", "personal_care", "work", "work", "exercise", "family", "phone_internet", "entertainment")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java)
            .addCallback(SeedCallback)
            .build()
        runBlocking { db.withTransaction { fill() } }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun fill() {
        var date = firstDay
        var n = 0
        while (date < today) {
            dayBlocks.forEachIndexed { i, (from, to) ->
                val start = date.atTime(from, 0).atZone(zone).toInstant()
                val end = (if (to == 24) date.plusDays(1).atStartOfDay() else date.atTime(to, 0)).atZone(zone).toInstant()
                db.timeDao().insert(
                    TimeEntryEntity("t${n++}", TimeEntryKind.ACTUAL, dayCategories[i], start, end, zone, zone, null, start, zone, start, zone),
                )
            }
            repeat(3) { k ->
                val start = date.atTime(6 + k, 0).atZone(zone).toInstant()
                db.exerciseDao().insertSession(
                    ExerciseSessionEntity(
                        "x${n++}", ExerciseSessionType.VOLUME, SessionStatus.COMPLETED, null, start, zone, date, start.plusSeconds(600), zone,
                        100, TimerCheckpoint(600_000, 600_000, null, null), null, start, zone,
                    ),
                )
            }
            listOf(SessionStatus.COMPLETED to 300_000L, SessionStatus.ENDED_EARLY to 120_000L).forEachIndexed { k, (status, active) ->
                val start = date.atTime(21, 10 * k).atZone(zone).toInstant()
                db.quietDao().insert(
                    QuietSessionEntity(
                        "q${n++}", status, start, zone, date, start.plusMillis(active), zone, 300_000,
                        TimerCheckpoint(active, active, null, null), start, zone,
                    ),
                )
            }
            date = date.plusDays(1)
        }
    }

    private fun load(period: ProgressPeriod): Pair<Int, ProgressUiState> = runBlocking {
        val (from, to) = period.range(today)
        val times = TimeRepository(db, time.provider)
        val exercise = ExerciseRepository(db, time.provider)
        val readFrom = DayView.readWindow(from).first
        val readTo = DayView.readWindow(to).second
        val actual = times.observeEntries(TimeEntryKind.ACTUAL, readFrom, readTo).first()
        val planned = times.observeEntries(TimeEntryKind.PLANNED, readFrom, readTo).first()
        val report = ProgressUiState(
            period, from, to,
            ProgressRules.exercise(
                exercise.observeRangeTotals(from, to).first(), exercise.observeRangeStats(from, to).first(),
                exercise.observeFastestTimed(from, to).first(), exercise.observeRecords().first(), exercise.observeLevels().first(),
                from, to, zone,
            ),
            ProgressRules.quiet(QuietRepository(db, time.provider).observeDays(from, to).first(), from, to, today, DayOfWeek.MONDAY, true),
            TimeSection.Ready(
                ProgressRules.time(
                    from, to, today, time.provider.now(), zone, actual, planned.map { it.entry },
                    times.observeSchedules().first(), times.observeCategories().first(),
                ),
            ),
        )
        actual.size to report
    }

    @Test
    fun eachPeriodReadsOnlyItsOwnDaysAndStaysQuick() {
        ProgressPeriod.entries.forEach { period ->
            val started = System.nanoTime()
            val (rows, report) = load(period)
            val millis = (System.nanoTime() - started) / 1_000_000
            val days = report.end.toEpochDay() - report.start.toEpochDay() + 1
            // The day before and after can touch the period (other zones); nothing else is read.
            assertTrue("$period read $rows entries", rows <= (days + 2) * dayBlocks.size)
            assertTrue("$period took $millis ms", millis < 3_000)
        }
    }

    @Test
    fun totalsOverAFullMonthOfHistoryAreExact() {
        val (_, october) = load(ProgressPeriod.LastMonth)
        assertEquals(31 * 300, october.exercise.totalReps)
        assertEquals(31 * 3, october.exercise.sessions)
        assertEquals(600_000L, october.exercise.averageSessionMillis)
        assertEquals(31, october.quiet.completedSessions)
        assertEquals(31 * 7, october.quiet.totalMinutes)
        assertEquals(31, october.quiet.daysWithFull)
        val time = (october.time as TimeSection.Ready).report
        // 22:00–24:00 is entertainment and 8:00–9:00 and 12:00–13:00 are unrecorded every day;
        // 25 October has 25 hours, the extra one falling in the night's sleep.
        assertEquals(31 * 2 * 60, time.unrecordedMinutes)
        assertEquals(31 * 7 * 60 + 60, time.categories.first { it.name == "Sleep" }.actualMinutes)
    }

    private fun plan(sql: String, vararg args: Any): String {
        val cursor = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args)
        val details = buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail"))) }
        cursor.close()
        return details.joinToString(" | ")
    }

    private fun assertUsesIndex(plan: String, index: String) {
        assertTrue(plan, plan.contains("USING INDEX $index") || plan.contains("USING COVERING INDEX $index"))
    }

    @Test
    fun rangeQueriesUseTheirIndexes() {
        val from = today.withDayOfMonth(1).toString()
        val to = today.toString()
        assertUsesIndex(
            plan(
                "SELECT * FROM time_entry WHERE kind = ? AND startAt >= ? AND startAt < ? AND endAt > ?",
                "ACTUAL", 0L, Long.MAX_VALUE, 0L,
            ),
            "index_time_entry_kind_startAt",
        )
        assertUsesIndex(
            plan("SELECT SUM(repetitions), COUNT(*) FROM exercise_session WHERE startDate BETWEEN ? AND ? AND repetitions > 0", from, to),
            "index_exercise_session_startDate",
        )
        assertUsesIndex(
            plan(
                "SELECT startDate FROM quiet_session WHERE startDate BETWEEN ? AND ? AND +status IN ('COMPLETED', 'ENDED_EARLY')",
                from, to,
            ),
            "index_quiet_session_startDate",
        )
    }
}
