package com.mauforge.paintlife.mytime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/** Something another screen (Today) asks My Time to open. */
sealed interface LogRequest {
    /** "Log since last entry": from where the last entry ended to now. */
    data object SinceLastEntry : LogRequest

    /** Open the sheet with [draft]; [overlaps] set when it already needs the overlap choice. */
    data class Draft(val draft: EntryDraft, val overlaps: List<OverlapChange>? = null, val confirmingRunning: Boolean = false) : LogRequest
}

/** Hands a [LogRequest] from Today to My Time, which takes it once it is showing. */
@Singleton
class MyTimeLaunch @Inject constructor() {
    private val _request = MutableStateFlow<LogRequest?>(null)
    val request: StateFlow<LogRequest?> = _request.asStateFlow()

    fun open(request: LogRequest) {
        _request.value = request
    }

    /** Takes the pending request, if any; it is handled once. */
    fun take(): LogRequest? = _request.getAndUpdate { null }
}
