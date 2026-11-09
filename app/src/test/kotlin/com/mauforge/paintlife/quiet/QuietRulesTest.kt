package com.mauforge.paintlife.quiet

import com.mauforge.paintlife.core.database.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuietRulesTest {

    private val day = LocalDate.of(2026, 11, 9)
    private val target = QuietRules.TARGET_MILLIS

    private fun session(status: SessionStatus, active: Long, date: LocalDate = day) = QuietSessionSummary(date, status, active, target)

    @Test
    fun onlyACompletedSessionWithTheFullTargetCountsAsCompleted() {
        assertTrue(QuietRules.countsAsCompleted(SessionStatus.COMPLETED, target, target))
        assertFalse(QuietRules.countsAsCompleted(SessionStatus.COMPLETED, target - 1, target))
        SessionStatus.entries.filter { it != SessionStatus.COMPLETED }.forEach { status ->
            assertFalse(status.name, QuietRules.countsAsCompleted(status, target, target))
        }
    }

    @Test
    fun onlyCompletedAndEndedEarlySessionsAddMinutes() {
        assertEquals(
            setOf(SessionStatus.COMPLETED, SessionStatus.ENDED_EARLY),
            SessionStatus.entries.filter(QuietRules::countsMinutes).toSet(),
        )
    }

    @Test
    fun dayTotalsAddUpByStartDayAndIgnoreIncompleteSessions() {
        val totals = QuietRules.dayTotals(
            listOf(
                session(SessionStatus.COMPLETED, target),
                session(SessionStatus.ENDED_EARLY, 125_000),
                session(SessionStatus.INCOMPLETE, 290_000),
                session(SessionStatus.ENDED_EARLY, 60_000, day.plusDays(1)),
                session(SessionStatus.INCOMPLETE, 100_000, day.plusDays(2)),
            ),
        )

        assertEquals(QuietDayTotals(day, completedSessions = 1, quietMillis = target + 125_000), totals[day])
        assertEquals(QuietDayTotals(day.plusDays(1), completedSessions = 0, quietMillis = 60_000), totals[day.plusDays(1)])
        assertFalse(totals.containsKey(day.plusDays(2)))
    }

    @Test
    fun aDayFillsInProportionButIsFullOnlyWithACompletedSession() {
        assertEquals(0f, QuietDayTotals.empty(day).fill)
        assertEquals(0.6f, QuietDayTotals(day, 0, 180_000).fill, 0.0001f)
        // Two short sessions adding to more than 5 minutes still don't paint a full day.
        assertEquals(QuietRules.PARTIAL_FILL_MAX, QuietDayTotals(day, 0, 2 * 200_000L).fill)
        assertEquals(1f, QuietDayTotals(day, 1, target).fill)
    }

    @Test
    fun minutesAreWholeAndRoundedDown() {
        assertEquals(4, QuietRules.wholeMinutes(299_999))
        assertEquals(5, QuietRules.wholeMinutes(300_000))
        assertEquals(0, QuietRules.wholeMinutes(-5))
    }
}
