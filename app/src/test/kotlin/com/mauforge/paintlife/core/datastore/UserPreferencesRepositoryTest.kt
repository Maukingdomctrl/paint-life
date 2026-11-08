package com.mauforge.paintlife.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class UserPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun firstLaunchDateIsEmptyBeforeAnythingIsRecorded() = runTest {
        val repository = repository(backgroundScope)
        assertNull(repository.firstLaunchDate.first())
    }

    @Test
    fun firstLaunchDateIsRecordedOnceAndNeverOverwritten() = runTest {
        val repository = repository(backgroundScope)
        val first = LocalDate.of(2026, 11, 9)

        assertEquals(first, repository.recordFirstLaunchIfAbsent(first))
        assertEquals(first, repository.recordFirstLaunchIfAbsent(first.plusDays(1)))
        assertEquals(first, repository.firstLaunchDate.first())
    }

    private fun repository(scope: kotlinx.coroutines.CoroutineScope) = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "test.preferences_pb") },
        ),
    )
}
