package com.mauforge.paintlife.exercise.cats

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.SeedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatRosterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `roster matches the seeded league levels in order`() {
        assertEquals(SeedData.levels.map { it.level to it.catKey }, CatRoster.cats.map { it.level to it.key })
    }

    @Test
    fun `names and breeds are the ten cats from the spec`() {
        val expected = listOf(
            "Ana" to "Orange tabby",
            "Mia" to "White Persian",
            "Zoe" to "Grey Scottish Fold",
            "Eva" to "Black cat",
            "Luna" to "Calico",
            "Sara" to "Maine Coon",
            "Nina" to "Siamese",
            "Maya" to "Bengal",
            "Aria" to "Tuxedo cat",
            "Champion" to "Tortoiseshell",
        )
        assertEquals(expected, CatRoster.cats.map { context.getString(it.nameRes) to context.getString(it.breedRes) })
    }

    @Test
    fun `every cat has its own art slot and a personality line`() {
        assertEquals(10, CatRoster.cats.map { it.avatarRes }.toSet().size)
        CatRoster.cats.forEach { assertTrue(context.getString(it.personalityRes).isNotBlank()) }
    }

    @Test
    fun `art slots resolve to the temporary placeholder until photos are imported`() {
        // When a photo is imported for a cat, its slot becomes a .webp and this test should be
        // updated to list which cats still use the placeholder.
        CatRoster.cats.forEach { cat ->
            assertTrue("${cat.key} should still be the placeholder", CatArt.isPlaceholder(context.resources, cat.avatarRes))
        }
    }

    @Test
    fun `lookups by key and level agree`() {
        CatRoster.cats.forEach { cat ->
            assertEquals(cat, CatRoster.forKey(cat.key))
            assertEquals(cat, CatRoster.forLevel(cat.level))
        }
    }
}
