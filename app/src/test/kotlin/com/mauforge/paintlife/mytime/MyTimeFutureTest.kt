package com.mauforge.paintlife.mytime

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.backup.DataReplaced
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/**
 * Actual time is recorded once it has happened: the log sheet's steppers stop at now, and time
 * that is still ahead can only be planned. Also: an imported backup clears a pending Undo.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MyTimeFutureTest {

    private val zone = ZoneId.of("Europe/London")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), zone)
    private val replaced = DataReplaced()
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TimeRepository
    private lateinit var viewModel: MyTimeViewModel
    private lateinit var collector: Job

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = TimeRepository(db, time.provider)
        viewModel = MyTimeViewModel(repository, testPreferences(ApplicationProvider.getApplicationContext()), MyTimeLaunch(), time.provider, time.dayClock, replaced)
        collector = CoroutineScope(main).launch { viewModel.state.collect { } }
        await { it.content != null }
    }

    @After
    fun tearDown() {
        runBlocking {
            collector.cancelAndJoin()
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun onMain(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun await(predicate: (MyTimeScreenState) -> Boolean): MyTimeScreenState = runBlocking {
        withTimeout(10_000) { viewModel.state.first(predicate) }
    }

    private var marks = 0

    /**
     * The sheet's start and end. State follows the taps a moment later, so a note is typed as a
     * marker first: once it shows, everything tapped before it has been applied.
     */
    private fun times(): Pair<String, String> {
        await { it.editor != null }
        val mark = "mark ${++marks}"
        onMain { viewModel.setNote(mark) }
        return await { it.editor?.note == mark }.editor!!.let { it.startLabel to it.endLabel }
    }

    private fun stored(kind: TimeEntryKind = TimeEntryKind.ACTUAL): List<Triple<String, String, String>> = runBlocking {
        repository.observeDayEntries(day, kind).first().map {
            Triple(it.entry.categoryId, TimeRules.wallTime(it.entry.startAt, zone).text, TimeRules.wallTime(it.entry.endAt, zone).text)
        }
    }

    @Test
    fun theEndOfAnActualEntryStopsAtNow() {
        // 18:00. Log activity offers the hour up to now.
        onMain { viewModel.openNew() }
        assertEquals("17:00" to "18:00", times())

        onMain {
            viewModel.nudgeEnd(5)
            viewModel.nudgeEnd(60)
        }
        assertEquals("17:00" to "18:00", times())

        // Earlier is still free, and so is coming back up to now.
        onMain { viewModel.nudgeEnd(-60) }
        assertEquals("17:00" to "17:05", times())
        onMain { viewModel.nudgeEnd(60) }
        assertEquals("17:00" to "18:00", times())

        onMain {
            viewModel.selectCategory("work")
            viewModel.save()
        }
        await { it.editor == null }
        assertEquals(listOf(Triple("work", "17:00", "18:00")), stored())
    }

    @Test
    fun theStartOfAnActualEntryStopsAStepBeforeNow() {
        onMain { viewModel.openNew() }
        assertEquals("17:00" to "18:00", times())
        onMain { viewModel.nudgeStart(60) }
        onMain { viewModel.nudgeStart(60) }

        assertEquals("17:55" to "18:00", times())
    }

    @Test
    fun timeAheadCanBePlannedButNotSavedAsActual() {
        onMain { viewModel.openNew() }
        assertEquals("17:00" to "18:00", times())
        onMain {
            viewModel.setEditorLayer(TimeLayer.Planned)
            viewModel.nudgeEnd(60)
            viewModel.nudgeStart(60)
            viewModel.selectCategory("learning")
        }
        assertEquals("18:00" to "19:00", times())

        // Switched back to Actual with those times: saving is refused, and says why.
        onMain {
            viewModel.setEditorLayer(TimeLayer.Actual)
            viewModel.save()
        }
        val refused = await { it.notice != null }
        assertEquals(MyTimeNotice.Kind.Future, refused.notice!!.kind)
        assertEquals(emptyList<Triple<String, String, String>>(), stored())

        // As a plan it saves.
        onMain {
            viewModel.setEditorLayer(TimeLayer.Planned)
            viewModel.save()
        }
        await { it.editor == null }
        assertEquals(listOf(Triple("learning", "18:00", "19:00")), stored(TimeEntryKind.PLANNED))
        assertEquals(emptyList<Triple<String, String, String>>(), stored())
    }

    @Test
    fun anImportedBackupClearsAnUndoFromBefore() {
        runBlocking { repository.save(EntryDraft(null, TimeEntryKind.ACTUAL, "work", null, day.atTime(9, 0).atZone(zone).toInstant(), day.atTime(10, 0).atZone(zone).toInstant()), Resolution.None) }
        val id = runBlocking { repository.observeDayEntries(day).first().single().entry.id }
        onMain { viewModel.openEntry(id) }
        await { it.editor != null }
        onMain { viewModel.delete() }
        val token = await { it.undo != null }.undo!!.token

        // Everything is replaced (here: by nothing). The old Undo must not put the entry back.
        replaced.signal()
        assertNull(await { it.undo == null }.undo)
        onMain { viewModel.undo(token) }

        assertEquals(emptyList<Triple<String, String, String>>(), stored())
    }
}
