package com.mauforge.paintlife.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/** The flags a backup carries: read as they are, and written back even when that moves them back. */
@RunWith(AndroidJUnit4::class)
class BackupFlagsTest {

    @Test
    fun theProgressFlagsOfABackupReplaceThePhonesEvenBackwards() = runBlocking<Unit> {
        val repository = testPreferences(ApplicationProvider.getApplicationContext())
        repository.recordFirstLaunchIfAbsent(LocalDate.of(2026, 11, 9))
        repository.markExerciseSafetyRead()
        repository.markMyTimeObservationComplete()
        repository.markVictorySeen(Instant.ofEpochMilli(9_000))
        repository.setProgressPeriod("ThisMonth")
        repository.markQuietNotificationsAsked()
        repository.dismissSleepCard(LocalDate.of(2026, 11, 9))
        val here = repository.backupFlags()
        assertEquals(BackupFlags(LocalDate.of(2026, 11, 9), true, Instant.ofEpochMilli(9_000), true, null, "ThisMonth"), here)

        // A backup from before any of that: the flags go back with the records they describe.
        val older = BackupFlags(LocalDate.of(2026, 10, 1), false, Instant.ofEpochMilli(4_000), false, "FourWeeks", null)
        repository.restoreBackupFlags(older)

        assertEquals(older, repository.backupFlags())
        assertEquals(false, repository.hasReadExerciseSafety.first())
        assertEquals(false, repository.myTimeObservationComplete.first())
        assertEquals(Instant.ofEpochMilli(4_000), repository.victorySeenUpTo.first())
        assertNull(repository.progressPeriod.first())
        // Phone-specific flags are not part of a backup and stay as they were.
        assertEquals(true, repository.quietNotificationsAsked.first())
        assertEquals(LocalDate.of(2026, 11, 9), repository.sleepCardDismissedOn.first())
        // A backup without a first-launch date keeps this phone's.
        repository.restoreBackupFlags(older.copy(firstLaunchDate = null))
        assertEquals(LocalDate.of(2026, 10, 1), repository.firstLaunchDate.first())
    }
}
