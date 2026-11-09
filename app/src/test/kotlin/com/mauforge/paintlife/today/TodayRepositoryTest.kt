package com.mauforge.paintlife.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.database.allMigrations
import com.mauforge.paintlife.testing.TestTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TodayRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today: LocalDate = LocalDate.of(2026, 11, 9)
    private val yesterday: LocalDate = today.minusDays(1)
    private val time = TestTime(today.atTime(9, 0), ZoneId.of("Asia/Kolkata"))
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TodayRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java)
            .addCallback(SeedCallback)
            .allowMainThreadQueries()
            .build()
        repository = TodayRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun titles(date: LocalDate): List<String> = runBlocking { repository.observePriorities(date).first().map { it.title } }

    private fun priorities(date: LocalDate): List<PriorityEntity> = runBlocking { repository.observePriorities(date).first() }

    private fun added(date: LocalDate, title: String): PriorityEntity = runBlocking {
        (repository.add(date, title) as AddResult.Added).priority
    }

    private fun dailyRecordCount(): Int = db.query("SELECT COUNT(*) FROM daily_record", null).use { it.moveToFirst(); it.getInt(0) }

    // Adding and the three-per-day rule

    @Test
    fun addedPrioritiesKeepTheirOrderAndCreateOneDailyRecord() {
        assertEquals(0, dailyRecordCount())
        added(today, "Call the bank")
        added(today, "Walk to the market")

        assertEquals(listOf("Call the bank", "Walk to the market"), titles(today))
        assertEquals(listOf(0, 1), priorities(today).map { it.position })
        assertEquals(1, dailyRecordCount())
    }

    @Test
    fun aFourthPriorityIsRefused() = runBlocking<Unit> {
        repeat(3) { added(today, "Priority $it") }

        assertEquals(AddResult.LimitReached, repository.add(today, "One too many"))
        assertEquals(3, priorities(today).size)
    }

    @Test
    fun manySimultaneousAddsNeverMakeMoreThanThree() = runBlocking<Unit> {
        val results = withContext(Dispatchers.Default) {
            (1..8).map { n -> async { repository.add(today, "Tap $n") } }.awaitAll()
        }

        assertEquals(3, results.count { it is AddResult.Added })
        assertEquals(5, results.count { it == AddResult.LimitReached })
        assertEquals(listOf(0, 1, 2), priorities(today).map { it.position })
    }

    @Test
    fun theLimitIsPerDay() {
        repeat(3) { added(yesterday, "Old $it") }
        added(today, "New")
        assertEquals(listOf("New"), titles(today))
    }

    @Test
    fun blankOrOverlongTitlesAreRejectedAndSpacesTidied() = runBlocking<Unit> {
        assertEquals(AddResult.InvalidTitle, repository.add(today, "   "))
        assertEquals(AddResult.InvalidTitle, repository.add(today, "x".repeat(PriorityTitle.MAX_LENGTH + 1)))
        added(today, "  Call \n the   bank ")
        assertEquals(listOf("Call the bank"), titles(today))
    }

    @Test
    fun timestampsAreStoredWithTheZoneTheyWereRecordedIn() {
        val priority = added(today, "Call the bank")
        assertEquals(time.provider.now(), priority.createdAt)
        assertEquals(ZoneId.of("Asia/Kolkata"), priority.createdZoneId)
    }

    // Editing, completing

    @Test
    fun renameChangesOnlyTheTitle() = runBlocking<Unit> {
        val priority = added(today, "Call the bank")
        assertTrue(repository.rename(priority.id, "Call the bank about the card"))
        assertFalse(repository.rename(priority.id, " "))
        assertFalse(repository.rename("missing", "Anything"))

        val saved = priorities(today).single()
        assertEquals("Call the bank about the card", saved.title)
        assertEquals(priority.position, saved.position)
    }

    @Test
    fun completingTwiceIsTheSameAsOnceAndUncompletingClearsTheTime() = runBlocking<Unit> {
        val priority = added(today, "Call the bank")
        repository.setCompleted(priority.id, true)
        val first = priorities(today).single()
        time.setLocal(today.atTime(10, 0))
        repository.setCompleted(priority.id, true)

        val second = priorities(today).single()
        assertTrue(second.isCompleted)
        assertEquals(first.completedAt, second.completedAt)
        assertEquals(ZoneId.of("Asia/Kolkata"), second.completedZoneId)

        repository.setCompleted(priority.id, false)
        val reopened = priorities(today).single()
        assertFalse(reopened.isCompleted)
        assertNull(reopened.completedAt)
        assertNull(reopened.completedZoneId)
    }

    // Delete, undo, reorder

    @Test
    fun deleteClosesTheGapAndRestorePutsItBackInPlace() = runBlocking<Unit> {
        added(today, "A")
        val middle = added(today, "B")
        added(today, "C")
        repository.setCompleted(middle.id, true)
        val completedMiddle = priorities(today)[1]

        val deleted = repository.delete(middle.id)
        assertEquals(listOf("A", "C"), titles(today))
        assertEquals(listOf(0, 1), priorities(today).map { it.position })

        assertNotNull(deleted)
        assertTrue(repository.restore(deleted!!))
        assertEquals(listOf("A", "B", "C"), titles(today))
        assertEquals(completedMiddle, priorities(today)[1])
        assertFalse("restoring twice must not duplicate", repository.restore(deleted!!))
        assertEquals(3, priorities(today).size)
    }

    @Test
    fun restoreIsRefusedWhenTheDayFilledUpMeanwhile() = runBlocking<Unit> {
        val first = added(today, "A")
        added(today, "B")
        added(today, "C")
        val deleted = repository.delete(first.id)!!
        added(today, "D")

        assertFalse(repository.restore(deleted))
        assertEquals(listOf("B", "C", "D"), titles(today))
    }

    @Test
    fun deletingSomethingAlreadyGoneDoesNothing() = runBlocking<Unit> {
        assertNull(repository.delete("missing"))
    }

    @Test
    fun moveSwapsNeighboursAndStopsAtTheEnds() = runBlocking<Unit> {
        val a = added(today, "A")
        added(today, "B")
        val c = added(today, "C")

        repository.move(c.id, MoveDirection.Up)
        assertEquals(listOf("A", "C", "B"), titles(today))
        repository.move(a.id, MoveDirection.Up)
        assertEquals(listOf("A", "C", "B"), titles(today))
        repository.move(a.id, MoveDirection.Down)
        assertEquals(listOf("C", "A", "B"), titles(today))
        assertEquals(listOf(0, 1, 2), priorities(today).map { it.position })
    }

    // Days and carry-over

    @Test
    fun addingToANewDayLeavesEarlierDaysUntouched() = runBlocking<Unit> {
        val old = added(yesterday, "Yesterday's")
        added(today, "Today's")

        assertEquals(listOf(old), priorities(yesterday))
        assertEquals(listOf("Today's"), titles(today))
    }

    @Test
    fun theOfferListsOpenPrioritiesFromTheLatestPlannedDay() = runBlocking<Unit> {
        added(today.minusDays(3), "Older")
        val open = added(yesterday, "Open")
        val done = added(yesterday, "Done")
        repository.setCompleted(done.id, true)

        val offer = repository.observeCarryOver(today).first()
        assertEquals(yesterday, offer?.sourceDate)
        assertEquals(listOf(open.id), offer?.candidates?.map { it.id })
    }

    @Test
    fun noOfferWhenTheLastPlannedDayIsAllDoneOrOverAWeekAgo() = runBlocking<Unit> {
        added(today.minusDays(8), "Long ago")
        assertNull(repository.observeCarryOver(today).first())

        val done = added(yesterday, "Done")
        repository.setCompleted(done.id, true)
        assertNull(repository.observeCarryOver(today).first())
    }

    @Test
    fun carryOverCopiesAndNeverChangesTheOriginals() = runBlocking<Unit> {
        val walk = added(yesterday, "Walk")
        val read = added(yesterday, "Read")
        val originals = priorities(yesterday)

        assertEquals(2, repository.carryOver(today, listOf(read.id, walk.id)))

        assertEquals(originals, priorities(yesterday))
        val copies = priorities(today)
        assertEquals(listOf("Walk", "Read"), copies.map { it.title })
        assertEquals(listOf(walk.id, read.id), copies.map { it.carriedFromId })
        assertTrue(copies.none { it.isCompleted || it.id == walk.id || it.id == read.id })
        assertNull(repository.observeCarryOver(today).first())
    }

    @Test
    fun repeatingACarryOverCopiesNothingTwice() = runBlocking<Unit> {
        val walk = added(yesterday, "Walk")
        assertEquals(1, repository.carryOver(today, listOf(walk.id, walk.id)))
        assertEquals(0, repository.carryOver(today, listOf(walk.id)))
        assertEquals(listOf("Walk"), titles(today))
    }

    @Test
    fun carryOverFillsOnlyTheFreePlaces() = runBlocking<Unit> {
        val ids = (1..3).map { added(yesterday, "Old $it").id }
        added(today, "New 1")
        added(today, "New 2")

        assertEquals(1, repository.carryOver(today, ids))
        assertEquals(listOf("New 1", "New 2", "Old 1"), titles(today))
        assertEquals(0, repository.carryOver(today, ids))
    }

    @Test
    fun notNowHidesTheOfferForThatDayOnly() = runBlocking<Unit> {
        added(yesterday, "Walk")
        repository.dismissCarryOver(today)
        assertNull(repository.observeCarryOver(today).first())

        val tomorrow = today.plusDays(1)
        assertNotNull(repository.observeCarryOver(tomorrow).first())
    }

    // Persistence

    @Test
    fun prioritiesSurviveClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val name = "restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, PaintLifeDatabase::class.java, name)
            .addMigrations(*allMigrations { time.zone })
            .addCallback(SeedCallback)
            .build()

        val first = open()
        val priority = (TodayRepository(first, time.provider).add(today, "Survives") as AddResult.Added).priority
        TodayRepository(first, time.provider).setCompleted(priority.id, true)
        first.close()

        val second = open()
        val reloaded = TodayRepository(second, time.provider).observePriorities(today).first().single()
        assertEquals("Survives", reloaded.title)
        assertTrue(reloaded.isCompleted)
        second.close()
        context.deleteDatabase(name)
    }

    @Test
    fun aMidnightPriorityBelongsToTheDayItWasPlannedFor() {
        time.setLocal(LocalDateTime.of(2026, 11, 9, 23, 59))
        val late = added(today, "Late")
        time.setLocal(LocalDateTime.of(2026, 11, 10, 0, 1))

        assertEquals(listOf(late), priorities(today))
        assertEquals(emptyList<PriorityEntity>(), priorities(today.plusDays(1)))
    }
}
