package com.mauforge.paintlife.exercise.cats

import android.content.Context
import android.util.TypedValue
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
    fun `names and breeds are the ten cats in level order`() {
        val expected = listOf(
            "Aero" to "Grey and white tabby",
            "Aria" to "Tuxedo",
            "Pepper" to "Silver tabby",
            "Coco" to "Bengal",
            "Luna" to "Calico",
            "Simba" to "Orange longhair",
            "Cloud" to "Ragdoll",
            "Titan" to "Maine Coon",
            "Olive" to "Scottish Fold",
            "Milo" to "Siamese",
        )
        assertEquals(expected, CatRoster.cats.map { context.getString(it.nameRes) to context.getString(it.breedRes) })
    }

    @Test
    fun `every cat has its own art slot and a personality line`() {
        assertEquals(10, CatRoster.cats.map { it.avatarRes }.toSet().size)
        CatRoster.cats.forEach { assertTrue(context.getString(it.personalityRes).isNotBlank()) }
    }

    @Test
    fun `every cat has its own bundled WebP picture`() {
        CatRoster.cats.forEach { cat ->
            assertEquals("cat_${cat.key}", context.resources.getResourceEntryName(cat.avatarRes))
            val value = TypedValue()
            context.resources.getValue(cat.avatarRes, value, true)
            assertTrue("${cat.key} should be a .webp, was ${value.string}", value.string.toString().endsWith("cat_${cat.key}.webp"))
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
