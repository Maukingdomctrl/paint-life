package com.mauforge.paintlife.core.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.time.SessionTimer
import com.mauforge.paintlife.exercise.ExerciseRepository
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import com.mauforge.paintlife.today.TodayRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.function.Supplier

/**
 * The card's flow on a real database, with the file picker's streams played by Robolectric: an
 * import asks first, only "Replace everything" writes, and every failure leaves the data alone.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BackupViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(18, 0), ZoneId.of("Asia/Kolkata"))
    private val uri: Uri = Uri.parse("content://files/backup.json")
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var db: PaintLifeDatabase
    private lateinit var exercise: ExerciseRepository
    private lateinit var replaced: DataReplaced
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        main = newSingleThreadContext("test-main")
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        exercise = ExerciseRepository(db, time.provider)
        replaced = DataReplaced()
        viewModel = BackupViewModel(context, BackupRepository(db, testPreferences(context), exercise, time.provider, replaced), time.provider)
        runBlocking { TodayRepository(db, time.provider).add(day, "Mine, from before") }
    }

    @After
    fun tearDown() {
        runBlocking { viewModel.viewModelScope.coroutineContext.job.cancelAndJoin() }
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    private fun onMain(block: () -> Unit) = runBlocking { withContext(main) { block() } }

    private fun await(predicate: (BackupUiState) -> Boolean): BackupUiState = runBlocking {
        withTimeout(10_000) { viewModel.state.first(predicate) }
    }

    /** The state once the action in progress has finished. */
    private fun settled(): BackupUiState = await { !it.busy }

    private fun fileContains(text: String) {
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri, Supplier<InputStream> { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) })
    }

    private fun priorities(): List<String> = runBlocking { db.backupDao().priorities().map { it.title } }

    private fun choose() {
        onMain { viewModel.importFrom(uri) }
        await { it.busy || it.preview != null || it.message != null }
        settled()
    }

    @Test
    fun exportWritesABackupThatCanBeReadBack() {
        val out = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, out)

        onMain { viewModel.exportTo(uri) }
        val state = await { it.message != null }

        assertEquals(BackupMessage.Exported(BackupSummary(1, 0, 0, 0, 0)), state.message)
        val file = BackupFormat.decode(out.toString(Charsets.UTF_8.name()))
        assertEquals(listOf("Mine, from before"), file.priorities.map { it.title })
        assertEquals(time.provider.now().toEpochMilli(), file.exportedAt)
        assertEquals("paint-life-backup-2026-11-09.json", viewModel.suggestedFileName())
    }

    @Test
    fun cancellingTheFilePickerDoesNothing() {
        onMain {
            viewModel.exportTo(null)
            viewModel.importFrom(null)
        }
        assertEquals(BackupUiState(), viewModel.state.value)
    }

    @Test
    fun importAsksFirstAndOnlyReplaceEverythingWrites() {
        fileContains(BackupFormat.encode(BackupFixtures.full))

        // Import: nothing is in progress, so the picker may open.
        onMain { viewModel.requestImport() }
        val token = await { it.openPicker != null }.openPicker!!
        onMain { viewModel.pickerOpened(token) }
        assertNull(settled().openPicker)

        // The file is read and checked, and the confirmation shows both sides. Nothing is written.
        choose()
        val preview = viewModel.state.value.preview!!
        assertEquals(BackupSummary(3, 3, 3, 3, 2), preview.inFile)
        assertEquals(BackupSummary(1, 0, 0, 0, 0), preview.onPhone)
        assertEquals(BackupFixtures.full.exportedAt, preview.exportedAt.toEpochMilli())
        assertEquals(listOf("Mine, from before"), priorities())

        // Cancel: still nothing written.
        onMain { viewModel.cancelImport() }
        assertNull(viewModel.state.value.preview)
        assertEquals(listOf("Mine, from before"), priorities())
        assertEquals(0, replaced.count.value)

        // Choose it again and confirm; a second tap on the button changes nothing more.
        choose()
        onMain {
            viewModel.confirmImport()
            viewModel.confirmImport()
        }
        val done = await { it.message is BackupMessage.Imported }
        assertEquals(BackupMessage.Imported(BackupSummary(3, 3, 3, 3, 2)), done.message)
        assertNull(settled().preview)
        assertEquals(listOf("Call the dentist", "Water the plants", "Water the plants"), priorities())
        assertEquals(1, replaced.count.value)
    }

    @Test
    fun exportingFromTheConfirmationSavesTheCurrentDataAndStaysThere() {
        fileContains(BackupFormat.encode(BackupFixtures.full))
        choose()
        val out = ByteArrayOutputStream()
        val saveUri = Uri.parse("content://files/before-import.json")
        shadowOf(context.contentResolver).registerOutputStream(saveUri, out)

        onMain { viewModel.exportTo(saveUri) }
        val state = await { it.preview?.currentSaved == true }

        assertTrue(state.preview != null)
        assertEquals(listOf("Mine, from before"), BackupFormat.decode(out.toString(Charsets.UTF_8.name())).priorities.map { it.title })
        assertEquals(listOf("Mine, from before"), priorities())
    }

    @Test
    fun aFileThatCannotBeImportedSaysWhyAndChangesNothing() {
        fileContains("just some notes")
        choose()
        assertEquals(BackupMessage.ImportFailed(BackupProblem.NotABackup), viewModel.state.value.message)

        fileContains(BackupFormat.encode(BackupFixtures.full).replace("\"formatVersion\": 1", "\"formatVersion\": 3"))
        choose()
        assertEquals(BackupMessage.ImportFailed(BackupProblem.TooNew), viewModel.state.value.message)

        fileContains(BackupFormat.encode(BackupFixtures.full.copy(challengeLevels = BackupFixtures.levels.take(4))))
        choose()
        assertEquals(BackupMessage.ImportFailed(BackupProblem.CatLeague), viewModel.state.value.message)

        shadowOf(context.contentResolver).registerInputStreamSupplier(uri, Supplier<InputStream> {
            object : InputStream() {
                override fun read(): Int = throw IOException("gone")
            }
        })
        choose()
        assertEquals(BackupMessage.ImportFailed(BackupProblem.Unreadable), viewModel.state.value.message)

        assertNull(viewModel.state.value.preview)
        assertEquals(listOf("Mine, from before"), priorities())
        assertEquals(0, replaced.count.value)
    }

    @Test
    fun importDoesNotEvenOpenThePickerWhileASessionIsInProgress() {
        val open = runBlocking { exercise.startVolumeSession(SessionTimer.started(1_000, 3)) }

        onMain { viewModel.requestImport() }
        val state = await { it.message != null }

        assertEquals(BackupMessage.Blocked(setOf(InProgress.Exercise)), state.message)
        assertNull(settled().openPicker)

        // A session started after the file was chosen still blocks the replace itself.
        runBlocking { exercise.discardEmpty(open.id) }
        fileContains(BackupFormat.encode(BackupFixtures.full))
        choose()
        runBlocking { exercise.startVolumeSession(SessionTimer.started(2_000, 3)) }
        onMain { viewModel.confirmImport() }
        assertEquals(BackupMessage.Blocked(setOf(InProgress.Exercise)), await { it.message is BackupMessage.Blocked }.message)
        assertEquals(listOf("Mine, from before"), priorities())
    }

    @Test
    fun aBackupThatCannotBeWrittenSaysSo() {
        shadowOf(context.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(b: Int) = throw IOException("disk full")
        })

        onMain { viewModel.exportTo(uri) }

        assertEquals(BackupMessage.ExportFailed, await { it.message != null }.message)
        assertEquals(listOf("Mine, from before"), priorities())
    }
}
