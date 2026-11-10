package com.mauforge.paintlife.mytime

import com.mauforge.paintlife.core.backup.DataReplaced
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.database.PaintLifeDatabase
import com.mauforge.paintlife.core.database.SeedCallback
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.testing.TestTime
import com.mauforge.paintlife.testing.testPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** My Time end to end: real ViewModel and repository, in-memory database, hand-driven clock. */
@RunWith(AndroidJUnit4::class)
class MyTimeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/London")
    private val day: LocalDate = LocalDate.of(2026, 11, 9)
    private val time = TestTime(day.atTime(14, 0), zone)
    private lateinit var db: PaintLifeDatabase
    private lateinit var repository: TimeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PaintLifeDatabase::class.java).addCallback(SeedCallback).build()
        repository = TimeRepository(db, time.provider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun show() {
        val viewModel = MyTimeViewModel(repository, testPreferences(context), MyTimeLaunch(), time.provider, time.dayClock, DataReplaced())
        composeRule.setContent { PaintLifeTheme { MyTimeScreen(viewModel = viewModel) } }
        waitFor(hasTestTag("my_time_content"))
    }

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun at(h: Int, m: Int = 0, date: LocalDate = day): Instant = date.atTime(h, m).atZone(zone).toInstant()

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNo(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    /**
     * Taps a button by its label (buttons merge their text, so the merged tree is searched),
     * scrolling it into view in the list of the screen or sheet tagged [screen].
     */
    private fun tap(text: String, screen: String = "my_time_content") {
        val matcher = hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag(screen))
        val list = hasScrollToIndexAction() and hasAnyAncestor(hasTestTag(screen))
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
        // Lazy lists only compose what is on screen: scroll first (this also waits for it to appear).
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNode(list).performScrollToNode(matcher) }.isSuccess }
        composeRule.onNode(matcher).performClick()
    }

    /** Scrolls the timeline into view, so its gaps and blocks are composed. */
    private fun showTimeline() {
        composeRule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("my_time_content"))).performScrollToNode(hasTestTag("timeline"))
    }

    private fun stored(date: LocalDate = day): List<Triple<String, String, String>> = runBlocking {
        repository.observeDayEntries(date).first().map {
            Triple(it.entry.categoryId, TimeRules.wallTime(it.entry.startAt, zone).text, TimeRules.wallTime(it.entry.endAt, zone).text)
        }
    }

    private fun save(start: Instant, end: Instant, category: String) = runBlocking {
        repository.save(EntryDraft(null, TimeEntryKind.ACTUAL, category, null, start, end), Resolution.None)
    }

    @Test
    fun theObservationBannerCountsRecordedDays() {
        save(at(9, date = day.minusDays(1)), at(11, date = day.minusDays(1)), "work")
        show()
        waitFor(hasTestTag("observation_banner"))
        waitFor(hasText(string(R.string.my_time_observation_title, 1)))
    }

    @Test
    fun tappingAGapLogsExactlyThatStretch() {
        save(at(9), at(10), "work")
        show()
        // 10:00 to now (14:00) is unrecorded.
        val gap = hasContentDescription(string(R.string.my_time_gap_description, "10:00", "14:00"))
        showTimeline()
        waitFor(gap)
        composeRule.onNode(gap).performSemanticsAction(SemanticsActions.OnClick)

        waitFor(hasTestTag("log_activity"))
        composeRule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("log_activity"))).performScrollToNode(hasTestTag("time_start"))
        waitFor(hasContentDescription("${string(R.string.my_time_start)} 10:00"))
        waitFor(hasContentDescription("${string(R.string.my_time_end)} 14:00"))
        tap("Learning", screen = "log_activity")
        tap(string(R.string.action_save), screen = "log_activity")

        waitForNo(hasTestTag("log_activity"))
        composeRule.waitUntil(5_000) { stored().size == 2 }
        assertEquals(listOf(Triple("work", "09:00", "10:00"), Triple("learning", "10:00", "14:00")), stored())
    }

    @Test
    fun anOverlapIsShownAndFittingInCanBeUndone() {
        save(at(9), at(12), "work")
        show()
        // Log since last entry: 12:00 to 14:00. Move the start back to 10:00.
        tap(string(R.string.action_log_since_last))
        waitFor(hasTestTag("log_activity"))
        composeRule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("log_activity"))).performScrollToNode(hasTestTag("time_start"))
        repeat(2) { composeRule.onNode(hasContentDescription(string(R.string.my_time_earlier_hour, string(R.string.my_time_start)))).performClick() }
        tap("Family", screen = "log_activity")
        tap(string(R.string.action_save), screen = "log_activity")

        waitFor(hasTestTag("overlap"))
        waitFor(hasText(string(R.string.overlap_outcome_trimmed, "09:00–10:00")))
        tap(string(R.string.overlap_fit), screen = "overlap")

        waitFor(hasText(string(R.string.my_time_fitted)))
        assertEquals(listOf(Triple("work", "09:00", "10:00"), Triple("family", "10:00", "14:00")), stored())
        composeRule.onNode(hasText(string(R.string.action_undo)) and hasClickAction()).performClick()
        composeRule.waitUntil(5_000) { stored() == listOf(Triple("work", "09:00", "12:00")) }
    }

    @Test
    fun deletingAnEntryIsImmediateWithUndo() {
        save(at(9), at(10), "work")
        show()
        val block = hasContentDescription("Work, 09:00 to 10:00", substring = true)
        showTimeline()
        waitFor(block)
        composeRule.onNode(block).performSemanticsAction(SemanticsActions.OnClick)
        tap(string(R.string.action_delete_entry), screen = "log_activity")

        waitFor(hasText(string(R.string.my_time_deleted)))
        assertEquals(emptyList<Any>(), stored())
        composeRule.onNode(hasText(string(R.string.action_undo)) and hasClickAction()).performClick()
        composeRule.waitUntil(5_000) { stored() == listOf(Triple("work", "09:00", "10:00")) }
    }

    @Test
    fun nowDoingStartsAndStopsIntoAnEntry() {
        show()
        tap("Work", screen = "my_time_content")
        waitFor(hasText(string(R.string.now_doing_running, "Work")))

        time.setLocal(day.atTime(14, 45))
        tap(string(R.string.action_stop_now), screen = "my_time_content")

        composeRule.waitUntil(5_000) { stored().isNotEmpty() }
        assertEquals(listOf(Triple("work", "14:00", "14:45")), stored())
        waitForNo(hasText(string(R.string.now_doing_running, "Work")))
    }

    @Test
    fun aPastDayCanBeCorrectedFromItsFirstGap() {
        show()
        composeRule.onNode(hasContentDescription(string(R.string.my_time_previous_day))).performClick()
        waitForNo(hasTestTag("now_doing"))
        tap(string(R.string.action_log_activity))
        composeRule.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("log_activity"))).performScrollToNode(hasTestTag("time_start"))
        waitFor(hasContentDescription("${string(R.string.my_time_start)} 00:00"))
        waitFor(hasContentDescription("${string(R.string.my_time_end)} 01:00"))
        tap("Sleep", screen = "log_activity")
        tap(string(R.string.action_save), screen = "log_activity")
        composeRule.waitUntil(5_000) { stored(day.minusDays(1)).isNotEmpty() }
        assertEquals(listOf(Triple("sleep", "00:00", "01:00")), stored(day.minusDays(1)))
        composeRule.onNodeWithTag("my_time_content").assertExists()
    }

    private fun storedPlanned(date: LocalDate): List<Triple<String, String, String>> = runBlocking {
        val (from, to) = DayView.readWindow(date)
        repository.observeEntries(TimeEntryKind.PLANNED, from, to).first()
            .filter { it.entry.startAt.atZone(zone).toLocalDate() == date }
            .map { Triple(it.entry.categoryId, TimeRules.wallTime(it.entry.startAt, zone).text, TimeRules.wallTime(it.entry.endAt, zone).text) }
    }

    @Test
    fun insightsStayHiddenUntilSevenDaysAreRecorded() {
        (1..6).forEach { back -> save(at(9, date = day.minusDays(back.toLong())), at(11, date = day.minusDays(back.toLong())), "work") }
        show()
        waitFor(hasText(string(R.string.my_time_observation_title, 6)))
        assertEquals(0, composeRule.onAllNodes(hasTestTag("insights_card"), useUnmergedTree = true).fetchSemanticsNodes().size)

        save(at(9, date = day.minusDays(7)), at(11, date = day.minusDays(7)), "work")
        waitFor(hasTestTag("insights_card"))
        waitForNo(hasTestTag("observation_banner"))
    }

    @Test
    fun tomorrowTakesPlansOnlyAndTheyNeverCountAsActual() {
        show()
        composeRule.onNode(hasContentDescription(string(R.string.my_time_next_day))).performClick()
        waitFor(hasTestTag("future_note"))
        tap(string(R.string.action_plan_activity))
        waitFor(hasTestTag("log_activity"))
        waitFor(hasText(string(R.string.my_time_plan_title)))
        tap("Exercise", screen = "log_activity")
        tap(string(R.string.action_save), screen = "log_activity")

        val tomorrow = day.plusDays(1)
        composeRule.waitUntil(5_000) { storedPlanned(tomorrow).isNotEmpty() }
        assertEquals(listOf(Triple("exercise", "09:00", "10:00")), storedPlanned(tomorrow))
        assertEquals(emptyList<Any>(), stored(tomorrow))
    }

    @Test
    fun aScheduleBlockCanBeChangedForOneDay() {
        runBlocking {
            ScheduleRepository(db, time.provider).approve(
                ApprovedProposal("Plan", listOf(ProposalBlock("x", "work", 9 * 60, 12 * 60, ScheduleRules.ALL_DAYS)), null, null),
            )
        }
        show()
        tap(string(R.string.my_time_layer_planned))
        val block = hasContentDescription(string(R.string.my_time_block_planned, "Work", "09:00", "12:00"), substring = true)
        showTimeline()
        waitFor(block)
        composeRule.onNode(block).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasTestTag("schedule_prompt"))
        composeRule.onNode(hasText(string(R.string.action_change_this_day)) and hasClickAction()).performClick()

        composeRule.waitUntil(5_000) { storedPlanned(day).isNotEmpty() }
        assertEquals(listOf(Triple("work", "09:00", "12:00")), storedPlanned(day))
        waitFor(hasText(string(R.string.my_time_copied)))
    }
}
