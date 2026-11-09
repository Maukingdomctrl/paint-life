package com.mauforge.paintlife.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Part of the local day, used for the greeting. Morning runs from midnight to noon. */
enum class DayPart {
    Morning,
    Afternoon,
    Evening,
    ;

    companion object {
        val AfternoonStarts: LocalTime = LocalTime.NOON
        val EveningStarts: LocalTime = LocalTime.of(18, 0)

        fun at(time: LocalTime): DayPart = when {
            time < AfternoonStarts -> Morning
            time < EveningStarts -> Afternoon
            else -> Evening
        }
    }
}

/** The local day as the user sees it now: its date, the zone it was read in, and the part of day. */
data class DayMoment(val date: LocalDate, val zone: ZoneId, val part: DayPart) {
    companion object {
        fun at(now: Instant, zone: ZoneId): DayMoment {
            val local = now.atZone(zone)
            return DayMoment(local.toLocalDate(), zone, DayPart.at(local.toLocalTime()))
        }

        /**
         * The next instant at which [at] can give a different answer in [zone]: noon, 18:00 or the
         * next local midnight. Each is resolved by the zone's rules, so a DST gap moves it to the
         * first valid instant and a day without a midnight still has a start.
         */
        fun nextChange(now: Instant, zone: ZoneId): Instant {
            val local = now.atZone(zone)
            val date = local.toLocalDate()
            return listOf(
                date.atTime(DayPart.AfternoonStarts).atZone(zone).toInstant(),
                date.atTime(DayPart.EveningStarts).atZone(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant(),
            ).first { it > now }
        }
    }
}

/**
 * Decides "today". [moments] emits the current [DayMoment] at once, then again whenever it
 * changes: at local noon, 18:00 and midnight, and straight after the system reports a time,
 * date or zone change. It also re-reads the clock at least every [RECHECK] while collected,
 * because timers can run late while the phone sleeps. Collect it only while a screen is visible
 * (collectAsStateWithLifecycle), which also re-reads the clock whenever the app returns.
 *
 * Nothing is timed with this; it only decides which day the screen shows.
 */
class DayClock(
    private val time: TimeProvider,
    private val systemTimeChanges: Flow<Unit>,
) {
    val moments: Flow<DayMoment> = channelFlow {
        val wake = Channel<Unit>(Channel.CONFLATED)
        launch { systemTimeChanges.collect { wake.trySend(Unit) } }
        while (true) {
            val now = time.now()
            val zone = time.zone()
            send(DayMoment.at(now, zone))
            val untilChange = Duration.between(now, DayMoment.nextChange(now, zone))
            val wait = untilChange.coerceAtMost(RECHECK).toMillis().coerceAtLeast(1)
            withTimeoutOrNull(wait) { wake.receive() }
        }
    }.distinctUntilChanged()

    fun now(): DayMoment = DayMoment.at(time.now(), time.zone())

    companion object {
        val RECHECK: Duration = Duration.ofMinutes(1)

        /** Time, date and zone changes reported by the system (protected broadcasts, no permission). */
        fun systemTimeChanges(context: Context): Flow<Unit> = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    trySend(Unit)
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            awaitClose { context.unregisterReceiver(receiver) }
        }
    }
}
