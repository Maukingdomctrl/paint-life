package com.mauforge.paintlife.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the database against the exported schema JSON in app/schemas. When version 2
 * arrives, add a test that creates v1 with data, migrates, and checks the data survived.
 *
 * Uses the driver-based helper with an explicit file: the name-based helper compares a bare
 * database name with a full path, which fails on Windows host tests.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = PaintLifeDatabase::class,
    )

    @Test
    fun version1MatchesTheExportedSchema() {
        helper.createDatabase(1).close()
        helper.runMigrationsAndValidate(1, ALL_MIGRATIONS.toList()).close()
    }

    @Test
    fun everyVersionAfterOneHasAMigration() {
        val currentVersion = 1
        val covered = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        val required = (1 until currentVersion).map { it to it + 1 }.toSet()
        assertEquals(required, covered)
    }
}
