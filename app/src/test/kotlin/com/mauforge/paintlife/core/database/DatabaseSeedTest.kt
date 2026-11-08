package com.mauforge.paintlife.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DatabaseSeedTest {

    private lateinit var db: PaintLifeDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PaintLifeDatabase::class.java)
            .addCallback(SeedCallback)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun creatingTheDatabaseSeedsElevenCategoriesAndTenLevels() {
        assertEquals(11, count("activity_category"))
        assertEquals(10, count("challenge_level"))
    }

    @Test
    fun seedingAgainNeverDuplicatesOrOverwrites() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE activity_category SET isDistraction = 1 WHERE id = 'work'")

        SeedData.seed(sql)

        assertEquals(11, count("activity_category"))
        assertEquals(10, count("challenge_level"))
        assertEquals(listOf("1"), column("SELECT isDistraction FROM activity_category WHERE id = 'work'"))
    }

    @Test
    fun onlyPhoneAndEntertainmentAreDistractionsByDefault() {
        assertEquals(
            listOf("entertainment", "phone_internet"),
            column("SELECT id FROM activity_category WHERE isDistraction = 1 ORDER BY id"),
        )
    }

    @Test
    fun levelsHaveSpecBenchmarksAndOnlyAnaStartsUnlocked() {
        val expectedMinutes = (30L downTo 21L).toList()
        assertEquals(
            expectedMinutes.map { TimeUnit.MINUTES.toMillis(it).toString() },
            column("SELECT benchmarkMillis FROM challenge_level ORDER BY level"),
        )
        assertEquals(
            listOf("ana", "mia", "zoe", "eva", "luna", "sara", "nina", "maya", "aria", "champion"),
            column("SELECT catKey FROM challenge_level ORDER BY level"),
        )
        assertEquals(listOf("1"), column("SELECT level FROM challenge_level WHERE isUnlocked = 1"))
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun column(sql: String): List<String> =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
}
