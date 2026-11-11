package com.mauforge.paintlife.mytime

import android.content.Context
import com.mauforge.paintlife.quiet.QuietSessionController
import com.mauforge.paintlife.quiet.QuietTimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The screen a reminder's "Start now" ends on. */
enum class StartDestination { Today, ExerciseSession, Quiet, MyTime }

/**
 * A reminder's "Start now", run by the app once it is open. Each one goes through the same path
 * as the button on its screen, so the same rules hold:
 * - Exercise opens the counter (a normal session; the safety guidance first if unread; an open
 *   session is simply shown). Never the timed challenge.
 * - Quiet Time starts the five minutes (an open session is simply shown).
 * - Sleep is the Sleep button. "Now doing" starts that activity. If the running activity needs
 *   the user first (over 16 hours, or it overlaps), My Time asks, as it does for the buttons.
 */
class ReminderStart @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeRepository,
    private val launch: MyTimeLaunch,
    private val quiet: QuietSessionController,
) {
    suspend fun start(action: StartAction, categoryId: String?, note: String?): StartDestination = when (action) {
        StartAction.NONE -> StartDestination.MyTime
        StartAction.EXERCISE -> StartDestination.ExerciseSession
        StartAction.QUIET -> {
            quiet.start()
            QuietTimerService.start(context)
            StartDestination.Quiet
        }
        StartAction.SLEEP -> when (val result = time.startSleep()) {
            is SleepResult.Done, SleepResult.Ignored -> StartDestination.Today
            is SleepResult.Pending -> askInMyTime(result.run, activity(TimeRules.SLEEP_ID, null))
        }
        StartAction.NOW_DOING -> {
            val running = time.observeCurrent().first()
            when {
                categoryId == null || (running?.categoryId == categoryId && running.note == note) -> StartDestination.MyTime
                else -> askInMyTime(time.startCurrent(categoryId, note), activity(categoryId, note))
            }
        }
    }

    private fun askInMyTime(result: RunResult, then: QuickActivity?): StartDestination {
        when (result) {
            is RunResult.Done -> Unit
            is RunResult.ConfirmEnd -> launch.open(LogRequest.Draft(result.draft, confirmingRunning = true, then = then))
            is RunResult.Overlaps -> launch.open(LogRequest.Draft(result.draft, result.fit, then = then))
        }
        return StartDestination.MyTime
    }

    private suspend fun activity(categoryId: String, note: String?): QuickActivity? =
        time.observeCategories().first().firstOrNull { it.id == categoryId }?.let { QuickActivity(it.id, it.name, it.colorKey, note) }
}
