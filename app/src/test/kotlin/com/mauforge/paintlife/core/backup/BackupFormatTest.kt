package com.mauforge.paintlife.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The file text: what is written can be read back, and anything else is refused by name. */
class BackupFormatTest {

    private fun problemOf(text: String): BackupProblem = try {
        BackupFormat.decode(text)
        fail("Expected the file to be refused")
        throw AssertionError()
    } catch (error: BackupException) {
        error.problem
    }

    @Test
    fun whatIsWrittenReadsBackTheSame() {
        val text = BackupFormat.encode(BackupFixtures.full)
        assertEquals(BackupFixtures.full, BackupFormat.decode(text))
    }

    @Test
    fun theFileSaysWhatItIsAndWhichFormat() {
        val text = BackupFormat.encode(BackupFixtures.full)
        assertTrue(text.contains("\"format\": \"paint-life-backup\""))
        assertTrue(text.contains("\"formatVersion\": 1"))
        // Moments are epoch milliseconds beside their zone id; dates are ISO text.
        assertTrue(text.contains("\"exportedAt\": ${BackupFixtures.full.exportedAt}"))
        assertTrue(text.contains("\"exportedZoneId\": \"Asia/Kolkata\""))
        assertTrue(text.contains("\"date\": \"2026-11-08\""))
    }

    @Test
    fun anythingThatIsNotABackupIsRefused() {
        assertEquals(BackupProblem.NotABackup, problemOf(""))
        assertEquals(BackupProblem.NotABackup, problemOf("not json at all"))
        assertEquals(BackupProblem.NotABackup, problemOf("[1, 2, 3]"))
        assertEquals(BackupProblem.NotABackup, problemOf("{\"hello\": \"world\"}"))
        assertEquals(BackupProblem.NotABackup, problemOf("{\"format\": \"someone-elses-backup\", \"formatVersion\": 1}"))
    }

    @Test
    fun aFileFromANewerAppIsRefusedAsTooNew() {
        val newer = BackupFormat.encode(BackupFixtures.full).replace("\"formatVersion\": 1", "\"formatVersion\": 2")
        assertEquals(BackupProblem.TooNew, problemOf(newer))
    }

    @Test
    fun aMissingOrMistypedFieldIsDamaged() {
        val text = BackupFormat.encode(BackupFixtures.full)
        assertEquals(BackupProblem.Damaged, problemOf(text.replace("\"formatVersion\": 1", "\"formatVersion\": \"one\"")))
        assertEquals(BackupProblem.Damaged, problemOf(text.replace("\"formatVersion\": 1", "\"formatVersion\": 0")))
        assertEquals(BackupProblem.Damaged, problemOf("{\"format\": \"paint-life-backup\", \"formatVersion\": 1}"))
        assertEquals(BackupProblem.Damaged, problemOf(text.replace("\"position\": 1", "\"position\": \"second\"")))
        assertEquals(BackupProblem.Damaged, problemOf(text.replace("\"title\": \"Call the dentist\",", "")))
    }

    @Test
    fun fieldsAddedByALaterAppAreIgnored() {
        val text = BackupFormat.encode(BackupFixtures.full).replaceFirst("{", "{\n    \"somethingNew\": {\"a\": [1, 2]},")
        assertEquals(BackupFixtures.full, BackupFormat.decode(text))
    }

    @Test
    fun aBackupOfAnEmptyAppIsValid() {
        val empty = BackupFile(exportedAt = 0L, exportedZoneId = "UTC", challengeLevels = BackupFixtures.levels.map { it.copy(isUnlocked = it.level == 1, unlockedAt = null, unlockedZoneId = null, beatenAt = null, beatenZoneId = null) })
        val contents = BackupMapper.toContents(BackupFormat.decode(BackupFormat.encode(empty)))
        assertTrue(contents.summary.isEmpty)
        assertEquals(11, contents.distractionFlags.size)
    }
}
