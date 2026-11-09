package com.mauforge.paintlife.quiet

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps a quiet session alive while it is open: a `specialUse` foreground service with the
 * ongoing notification. It owns no timing; [QuietSessionController] does. It:
 * - shows and updates the ongoing notification (countdown, Pause/Resume);
 * - holds a partial wake lock only while the timer runs, so the 5:00 completion and alert happen
 *   on time with the screen off. The lock times out a few seconds after the session's target, and
 *   is released on pause, finish, cancel and completion;
 * - posts the 5:00 alert, then stops itself once no session is open.
 *
 * Not sticky: if Android ends the process, the session is saved as incomplete at the next start
 * and nothing is restarted behind the user's back.
 */
@AndroidEntryPoint
class QuietTimerService : Service() {

    @Inject lateinit var controller: QuietSessionController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertedId: String? = null
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        QuietNotifications.ensureChannels(this)
        // A session finished before this service existed was already reported in the app.
        alertedId = controller.completed.value?.id
        scope.launch {
            combine(controller.live, controller.completed) { live, completed -> live to completed }.collect { (live, completed) ->
                if (completed != null && completed.id != alertedId) {
                    alertedId = completed.id
                    QuietNotifications.showFinished(this@QuietTimerService)
                }
                render(live)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must promptly become foreground, even if the session has just ended.
        goForeground(controller.live.value)
        when (intent?.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
        }
        if (controller.live.value == null) stop()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun render(live: QuietSessionController.Live?) {
        if (live == null) {
            stop()
            return
        }
        goForeground(live)
        if (live.isPaused) releaseWakeLock() else holdWakeLock(controller.remainingMillis(live) + WAKE_LOCK_MARGIN_MS)
    }

    private fun goForeground(live: QuietSessionController.Live?) {
        val remaining = live?.let(controller::remainingMillis) ?: 0L
        val notification = QuietNotifications.ongoing(this, remaining, paused = live?.isPaused ?: true)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, QuietNotifications.ONGOING_ID, notification, type)
        inForeground = true
    }

    private fun stop() {
        releaseWakeLock()
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    private fun holdWakeLock(timeoutMillis: Long) {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply { setReferenceCounted(false) }
            ?.also { wakeLock = it }
            ?: return
        // Re-acquiring a non-counted lock just moves its timeout.
        lock.acquire(timeoutMillis.coerceAtLeast(WAKE_LOCK_MARGIN_MS))
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        const val ACTION_PAUSE = "com.mauforge.paintlife.quiet.PAUSE"
        const val ACTION_RESUME = "com.mauforge.paintlife.quiet.RESUME"
        private const val WAKE_LOCK_TAG = "PaintLife:QuietTimer"
        private const val WAKE_LOCK_MARGIN_MS = 5_000L

        /** Starts (or refreshes) the service. Call while the app is in the foreground. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, QuietTimerService::class.java))
        }
    }
}
