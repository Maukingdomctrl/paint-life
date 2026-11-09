package com.mauforge.paintlife.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCounterTest {

    private fun CounterState.add(vararg amounts: Int) = amounts.fold(this) { state, n -> SessionCounter.add(state, n) }

    @Test
    fun plusOneFiveAndTenAddUp() {
        assertEquals(16, CounterState(0).add(1, 5, 10).reps)
    }

    @Test
    fun everyRapidTapCounts() {
        assertEquals(500, CounterState(0).add(*IntArray(500) { 1 }).reps)
    }

    @Test
    fun undoRevertsTheLastActionWhateverItWas() {
        var state = CounterState(0).add(1, 10, 5)
        state = SessionCounter.undo(state)
        assertEquals(11, state.reps)
        state = SessionCounter.undo(state)
        assertEquals(1, state.reps)
    }

    @Test
    fun undoCanNeverGoBelowZero() {
        var state = CounterState(0).add(1)
        repeat(5) { state = SessionCounter.undo(state) }
        assertEquals(0, state.reps)
        assertFalse(state.canUndo)
    }

    @Test
    fun undoWithNothingToUndoIsANoOp() {
        val restored = CounterState(85)
        assertEquals(restored, SessionCounter.undo(restored))
    }

    @Test
    fun aCorrectionCanBeUndone() {
        var state = SessionCounter.correct(CounterState(0).add(10, 10), 18)
        assertEquals(18, state.reps)
        state = SessionCounter.undo(state)
        assertEquals(20, state.reps)
    }

    @Test
    fun correctionsOutsideTheRangeAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SessionCounter.correct(CounterState(3), -1) }
        assertThrows(IllegalArgumentException::class.java) { SessionCounter.correct(CounterState(3), SessionCounter.MAX_REPS + 1) }
    }

    @Test
    fun theCountStopsAtTheSessionMaximum() {
        val state = SessionCounter.add(CounterState(SessionCounter.MAX_REPS - 3), 10)
        assertEquals(SessionCounter.MAX_REPS, state.reps)
        val full = SessionCounter.add(state, 1)
        assertEquals(state, full)
    }

    @Test
    fun resetClearsTheCountAndTheUndoHistory() {
        val state = SessionCounter.reset()
        assertEquals(0, state.reps)
        assertFalse(state.canUndo)
        assertTrue(CounterState(0).add(1).canUndo)
    }

    @Test
    fun undoHistoryIsBounded() {
        val state = CounterState(0).add(*IntArray(250) { 1 })
        assertEquals(SessionCounter.UNDO_DEPTH, state.undo.size)
        var undone = state
        repeat(SessionCounter.UNDO_DEPTH + 10) { undone = SessionCounter.undo(undone) }
        assertEquals(150, undone.reps)
    }
}
