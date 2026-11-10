package com.mauforge.paintlife.core.backup

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.then
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** The "Your data" card and the import confirmation, as the user reads and taps them. */
@RunWith(AndroidJUnit4::class)
class BackupCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preview = ImportPreview(
        exportedAt = Instant.parse("2026-11-10T08:30:00Z"),
        exportedZone = ZoneId.of("Asia/Kolkata"),
        inFile = BackupSummary(priorities = 3, exerciseSessions = 1, quietSessions = 0, timeEntries = 410, schedules = 2),
        onPhone = BackupSummary(0, 0, 0, 0, 0),
    )

    private fun card(state: BackupUiState, onExport: () -> Unit = {}, onImport: () -> Unit = {}) {
        composeRule.setContent { PaintLifeTheme { BackupCard(state, onExport, onImport) } }
    }

    @Test
    fun theCardOffersExportAndImportAsFullSizeButtons() {
        var exports = 0
        var imports = 0
        card(BackupUiState(), { exports++ }, { imports++ })

        composeRule.onNodeWithText("Your data").assertExists()
        composeRule.onNodeWithText("Export backup").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Import backup").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1 to 1, exports to imports)
        composeRule.onNodeWithTag("backup_message").assertDoesNotExist()
    }

    @Test
    fun whileWorkingTheButtonsAreOff() {
        var taps = 0
        card(BackupUiState(busy = true), { taps++ }, { taps++ })

        composeRule.onNodeWithText("Export backup").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Import backup").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test
    fun theCardSaysWhatTheLastActionDid() {
        card(BackupUiState(message = BackupMessage.Exported(BackupSummary(3, 1, 0, 410, 2))))
        composeRule.onNodeWithTag("backup_message")
            .assertTextEquals("Backup saved: 3 priorities, 1 exercise session, 410 time entries, 2 schedules.")
    }

    @Test
    fun aBlockedImportNamesWhatToFinish() {
        card(BackupUiState(message = BackupMessage.Blocked(setOf(InProgress.NowDoing, InProgress.Exercise))))
        composeRule.onNodeWithTag("backup_message")
            .assertTextEquals("Finish these first, then import: your exercise session, the activity you're doing now. Nothing was changed.")
    }

    @Test
    fun aRefusedFileSaysWhyAndThatNothingChanged() {
        card(BackupUiState(message = BackupMessage.ImportFailed(BackupProblem.Overlap)))
        composeRule.onNodeWithTag("backup_message")
            .assertTextEquals("That backup can't be imported: two of its time entries overlap. Nothing was changed.")
    }

    @Test
    fun everyProblemHasItsOwnWords() {
        composeRule.setContent {
            PaintLifeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupProblem.entries.forEach { problem -> BackupCard(BackupUiState(message = BackupMessage.ImportFailed(problem)), {}, {}) }
                }
            }
        }

        val texts = composeRule.onAllNodesWithTag("backup_message").fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
        }
        assertEquals(BackupProblem.entries.size, texts.size)
        assertEquals("Each problem reads differently", texts.size, texts.toSet().size)
        texts.forEach { assertTrue(it, it.endsWith("Nothing was changed.")) }
    }

    @Test
    fun theConfirmationShowsBothSidesAndThreeChoices() {
        var exported = 0
        var replaced = 0
        var cancelled = 0
        composeRule.setContent {
            PaintLifeTheme { ImportConfirmDialog(preview, busy = false, onExportFirst = { exported++ }, onReplace = { replaced++ }, onCancel = { cancelled++ }) }
        }

        composeRule.onNodeWithText("Replace everything with this backup?").assertExists()
        composeRule.onNodeWithTag("import_in_file").assertTextEquals("In the backup: 3 priorities, 1 exercise session, 410 time entries, 2 schedules.")
        composeRule.onNodeWithTag("import_on_phone").assertTextEquals("On this phone now: nothing recorded.")
        composeRule.onNodeWithTag("import_current_saved").assertDoesNotExist()

        composeRule.onNodeWithText("Export current data first").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Replace everything").performScrollTo().assertHeightIsAtLeast(48.dp).assertIsEnabled().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(Triple(1, 1, 1), Triple(exported, replaced, cancelled))
    }

    @Test
    fun theConfirmationWorksAtDoubleTextOnASmallPhone() {
        var replaced = 0
        var cancelled = 0
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f) then DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 640.dp))) {
                PaintLifeTheme {
                    ImportConfirmDialog(preview.copy(onPhone = BackupSummary(42, 18, 30, 4100, 2), currentSaved = true), busy = false, onExportFirst = {}, onReplace = { replaced++ }, onCancel = { cancelled++ })
                }
            }
        }

        // The body scrolls, so the long text never pushes a choice out of reach.
        composeRule.onNodeWithText("Export current data first").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Replace everything").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1 to 1, replaced to cancelled)
    }

    @Test
    fun whileReplacingNothingInTheConfirmationCanBeTappedAgain() {
        var taps = 0
        composeRule.setContent {
            PaintLifeTheme { ImportConfirmDialog(preview.copy(currentSaved = true), busy = true, onExportFirst = { taps++ }, onReplace = { taps++ }, onCancel = { taps++ }) }
        }

        composeRule.onNodeWithTag("import_current_saved").assertTextEquals("Current data saved.")
        composeRule.onNodeWithText("Replace everything").performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Export current data first").performScrollTo().assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }
}
