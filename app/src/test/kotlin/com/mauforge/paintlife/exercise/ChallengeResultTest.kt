package com.mauforge.paintlife.exercise

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.datastore.UserPreferencesRepository
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.core.time.TimerCheckpoint
import com.mauforge.paintlife.exercise.cats.CatRoster
import com.mauforge.paintlife.testing.FakeSessionClock
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * What the result of a timed challenge shows, and that each win's victory moment is offered once.
 * Main is a real single thread, so ViewModels run as they do in the app.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChallengeResultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val time = TestTime(LocalDate.of(2026, 11, 9).atTime(7, 0), ZoneId.of("Asia/Kolkata"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var preferences: UserPreferencesRepository
    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = ExerciseRepository(db, time.provider)
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "result_prefs_${UUID.randomUUID()}.preferences_pb") },
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
            scope.coroutineContext.job.cancelAndJoin()
        }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun <T> blocking(block: suspend () -> T): T = runBlocking { withTimeout(5_000) { block() } }

    private fun minutes(m: Long) = TimeUnit.MINUTES.toMillis(m)

    private fun run(level: Int, duration: Long): String = blocking {
        val started = repository.startTimedSession(level, SessionTimer.started(0, 1))!!
        time.clock.instant = time.clock.instant.plusSeconds(3_600)
        repository.finish(started.id, 1_000, TimerCheckpoint(duration, duration, null, 1))
        repository.applyTimedResults()
        started.id
    }

    private fun result(sessionId: String): ChallengeResultUi {
        val viewModel = ExerciseResultViewModel(SavedStateHandle(mapOf("sessionId" to sessionId)), repository, preferences)
        viewModels += viewModel
        return blocking { viewModel.state.first { it != null } }!!
    }

    private fun tab(): ExerciseViewModel {
        val controller = ExerciseSessionController(repository, FakeSessionClock().clock, scope)
        return ExerciseViewModel(repository, controller, preferences, time.provider).also { viewModels += it }
    }

    @Test
    fun aFirstWinShowsTheVictoryAndTheCatItUnlocked() {
        val id = run(level = 1, duration = minutes(29))

        val victory = (result(id) as ChallengeResultUi.Victory).state

        assertEquals(1, victory.beaten.cat.level)
        assertEquals(minutes(29), victory.yourMillis)
        assertEquals(CatRoster.forLevel(2), victory.unlocked)
        assertFalse(victory.isReplay)
    }

    @Test
    fun aReplayWinCelebratesWithoutUnlockingAnything() {
        run(level = 1, duration = minutes(29))
        val replay = run(level = 1, duration = minutes(28))

        val victory = (result(replay) as ChallengeResultUi.Victory).state

        assertTrue(victory.isReplay)
        assertNull(victory.unlocked)
        assertEquals(minutes(28), victory.bestMillis)
    }

    @Test
    fun aSlowerRunOrATieIsACalmCompletionNotAVictory() {
        val slower = (result(run(level = 1, duration = minutes(30) + 400)) as ChallengeResultUi.Complete).state
        assertFalse(slower.isTie)

        val tie = (result(run(level = 1, duration = minutes(30))) as ChallengeResultUi.Complete).state
        assertTrue(tie.isTie)
        assertEquals(minutes(30), tie.bestMillis)
    }

    @Test
    fun anUnknownSessionHasNoResult() {
        assertEquals(ChallengeResultUi.Missing, result("nope"))
    }

    @Test
    fun eachWinsVictoryIsOfferedOnce() {
        val id = run(level = 1, duration = minutes(29))
        val tab = tab()
        assertEquals(id, blocking { tab.pendingVictory.first { it != null } })

        // Showing the victory marks it seen.
        result(id)

        assertNull(blocking { tab.pendingVictory.first { it == null } })
        // A later, slower run is not a win and offers nothing; a new win is offered again.
        run(level = 1, duration = minutes(31))
        val next = run(level = 2, duration = minutes(28))
        assertEquals(next, blocking { tab.pendingVictory.first { it != null } })
    }
}
