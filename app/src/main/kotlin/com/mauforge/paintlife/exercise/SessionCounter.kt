package com.mauforge.paintlife.exercise

/**
 * The rep count of one open session plus what Undo can put back. [undo] holds the count before
 * each change, newest last, so Undo reverts the last action whatever it was (+1, +5, +10 or a
 * correction). The count can never go below zero or above [SessionCounter.MAX_REPS].
 *
 * The undo history lives in memory only; after the app is closed the count is restored and the
 * history starts empty.
 */
data class CounterState(val reps: Int, val undo: List<Int> = emptyList()) {
    val canUndo: Boolean get() = undo.isNotEmpty()
}

object SessionCounter {
    /** Upper bound for one session's count; also the limit for a correction. */
    const val MAX_REPS = 10_000

    /** How many actions Undo can step back through. */
    const val UNDO_DEPTH = 100

    fun add(state: CounterState, amount: Int): CounterState {
        require(amount > 0) { "Only positive steps are added; use correct() to lower the count." }
        return changedTo(state, state.reps.toLong().plus(amount).coerceAtMost(MAX_REPS.toLong()).toInt())
    }

    /** Sets the count to [reps] (a manual correction). Out-of-range values are rejected. */
    fun correct(state: CounterState, reps: Int): CounterState {
        require(reps in 0..MAX_REPS) { "A count must be between 0 and $MAX_REPS." }
        return changedTo(state, reps)
    }

    /** Reverts the last action; does nothing when there is none. */
    fun undo(state: CounterState): CounterState =
        if (state.undo.isEmpty()) state else CounterState(state.undo.last(), state.undo.dropLast(1))

    /** Back to zero with nothing to undo (Reset, after confirmation). */
    fun reset(): CounterState = CounterState(0)

    private fun changedTo(state: CounterState, reps: Int): CounterState {
        if (reps == state.reps) return state
        return CounterState(reps, (state.undo + state.reps).takeLast(UNDO_DEPTH))
    }
}
