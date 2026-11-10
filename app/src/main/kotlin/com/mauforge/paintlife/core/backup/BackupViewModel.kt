package com.mauforge.paintlife.core.backup

import android.content.Context
import android.content.pm.PackageManager
import android.database.SQLException
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mauforge.paintlife.core.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** What the card says after the last action. */
@Immutable
sealed interface BackupMessage {
    data class Exported(val summary: BackupSummary) : BackupMessage

    data class Imported(val summary: BackupSummary) : BackupMessage

    /** Import did nothing: these must be finished first. */
    data class Blocked(val inProgress: Set<InProgress>) : BackupMessage

    /** The chosen file cannot be imported; nothing was changed. */
    data class ImportFailed(val problem: BackupProblem) : BackupMessage

    data object ExportFailed : BackupMessage

    /** The phone has no app for choosing files. */
    data object NoFilePicker : BackupMessage
}

/** The confirmation before an import replaces everything. */
@Immutable
data class ImportPreview(
    val exportedAt: Instant,
    val exportedZone: ZoneId,
    val inFile: BackupSummary,
    val onPhone: BackupSummary,
    /** The current data was saved to a file from this dialog. */
    val currentSaved: Boolean = false,
)

@Immutable
data class BackupUiState(
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val preview: ImportPreview? = null,
    /** Set when the "open file" picker should open; [BackupViewModel.pickerOpened] clears it. */
    val openPicker: Long? = null,
)

/**
 * Export and import from the "Your data" card. Files are chosen in the system file picker, so
 * no storage permission is involved. An import never happens in one step: the file is read and
 * checked, then the user sees what is in it and what it would replace, and only "Replace
 * everything" writes. Entry points run on the main thread and ignore repeats while busy.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** The checked contents of the chosen file, kept until the user confirms or cancels. */
    private var pending: BackupContents? = null
    private var nextToken = 0L

    /** "paint-life-backup-2026-11-10.json" for today. */
    fun suggestedFileName(): String = "paint-life-backup-${time.today()}.json"

    /** Export to the file the user just created. Null means the picker was cancelled. */
    fun exportTo(uri: Uri?) {
        if (uri == null) return
        whileNotBusy {
            val message = try {
                val summary = withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
                    output.use { repository.export(it, appVersion()) }
                }
                _state.update { it.copy(preview = it.preview?.copy(currentSaved = true)) }
                BackupMessage.Exported(summary)
            } catch (_: IOException) {
                BackupMessage.ExportFailed
            } catch (_: SecurityException) {
                BackupMessage.ExportFailed
            }
            _state.update { it.copy(message = message) }
        }
    }

    /** Import was tapped: the picker opens unless something is still in progress. */
    fun requestImport() = whileNotBusy {
        val busy = repository.inProgress()
        _state.update {
            if (busy.isEmpty()) it.copy(openPicker = ++nextToken, message = null) else it.copy(message = BackupMessage.Blocked(busy))
        }
    }

    fun pickerOpened(token: Long) {
        _state.update { if (it.openPicker == token) it.copy(openPicker = null) else it }
    }

    /** Reads and checks the chosen file, then asks before replacing anything. */
    fun importFrom(uri: Uri?) {
        if (uri == null) return
        whileNotBusy {
            try {
                val contents = withContext(Dispatchers.IO) {
                    val input = openOrNull(uri) ?: throw BackupException(BackupProblem.Unreadable)
                    input.use { repository.read(it) }
                }
                val onPhone = repository.summary()
                pending = contents
                _state.update {
                    it.copy(preview = ImportPreview(contents.exportedAt, contents.exportedZone, contents.summary, onPhone), message = null)
                }
            } catch (error: BackupException) {
                _state.update { it.copy(message = BackupMessage.ImportFailed(error.problem)) }
            }
        }
    }

    /** "Replace everything" in the confirmation. */
    fun confirmImport() {
        val contents = pending ?: return
        whileNotBusy {
            val message = try {
                when (val result = repository.replaceAll(contents)) {
                    is ReplaceResult.Replaced -> BackupMessage.Imported(result.summary)
                    is ReplaceResult.Blocked -> BackupMessage.Blocked(result.inProgress)
                }
            } catch (_: SQLException) {
                // The transaction was rolled back: everything is as it was.
                BackupMessage.ImportFailed(BackupProblem.Damaged)
            }
            pending = null
            _state.update { it.copy(preview = null, message = message) }
        }
    }

    fun cancelImport() {
        if (_state.value.busy) return
        pending = null
        _state.update { it.copy(preview = null) }
    }

    /** The picker could not be opened at all (the phone has no app for choosing files). */
    fun pickerUnavailable() {
        _state.update { it.copy(message = BackupMessage.NoFilePicker) }
    }

    private fun openOrNull(uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    private fun whileNotBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
