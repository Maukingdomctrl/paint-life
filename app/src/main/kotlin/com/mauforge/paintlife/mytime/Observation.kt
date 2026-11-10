package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The observation week, shared by My Time, Insights and Progress: null once it is over (7 days
 * each with an hour of actual time, remembered for good), else the days recorded so far.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeObservation(repository: TimeRepository, preferences: UserPreferencesRepository): Flow<Int?> =
    preferences.myTimeObservationComplete.flatMapLatest { complete ->
        if (complete) {
            flowOf(null)
        } else {
            repository.observeObservedDays()
                .onEach { if (it >= TimeRules.OBSERVATION_DAYS) preferences.markMyTimeObservationComplete() }
                .map { it.takeIf { days -> days < TimeRules.OBSERVATION_DAYS } }
        }
    }.distinctUntilChanged()
