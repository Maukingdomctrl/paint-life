package com.mauforge.paintlife.debug

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.Tray
import com.mauforge.paintlife.core.designsystem.component.TrayDivider
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.exercise.CatDetailContent
import com.mauforge.paintlife.exercise.ChallengeCompleteContent
import com.mauforge.paintlife.exercise.CorrectCountBody
import com.mauforge.paintlife.exercise.ExerciseContent
import com.mauforge.paintlife.exercise.ExerciseHistoryContent
import com.mauforge.paintlife.exercise.ExerciseHistoryUiState
import com.mauforge.paintlife.exercise.HistoryEditorBody
import com.mauforge.paintlife.exercise.ExerciseSessionContent
import com.mauforge.paintlife.exercise.ExerciseSessionUiState
import com.mauforge.paintlife.exercise.ExerciseUiState
import com.mauforge.paintlife.exercise.LeagueEntry
import com.mauforge.paintlife.exercise.SafetyGuidanceContent
import com.mauforge.paintlife.exercise.VictoryContent
import com.mauforge.paintlife.exercise.VictoryUiState
import com.mauforge.paintlife.exercise.cats.CatRoster
import com.mauforge.paintlife.mytime.LogActivityContent
import com.mauforge.paintlife.mytime.LogActivityUiState
import com.mauforge.paintlife.mytime.MyTimeContent
import com.mauforge.paintlife.mytime.MyTimeUiState
import com.mauforge.paintlife.mytime.OverlapContent
import com.mauforge.paintlife.progress.ProgressContent
import com.mauforge.paintlife.progress.ProgressUiState
import com.mauforge.paintlife.quiet.QuietContent
import com.mauforge.paintlife.quiet.QuietUiState
import com.mauforge.paintlife.today.TodayContent
import com.mauforge.paintlife.today.TodayUiState

/** Debug builds only. Opened from the debug menu. */
class DesignGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent { PaintLifeTheme { DesignGallery(onClose = ::finish) } }
    }
}

/** One reviewable sample: a component sheet or a screen layout in one labelled state. */
class GalleryEntry(val id: String, val section: String, val title: String, val content: @Composable () -> Unit)

private fun today(id: String, title: String, state: TodayUiState) = GalleryEntry(id, "Today", title) {
    TodayContent(state, { _, _ -> }, {}, {}, {}, {}, {})
}

private fun exercise(id: String, title: String, state: ExerciseUiState) = GalleryEntry(id, "Exercise", title) {
    ExerciseContent(state, {}, {}, {}, {}, {}, {})
}

private fun history(id: String, title: String, state: ExerciseHistoryUiState) = GalleryEntry(id, "Exercise", title) {
    ExerciseHistoryContent(state, onOpenSession = {})
}

/** A sheet body shown as a screen, so it can be reviewed without opening the sheet. */
private fun sheet(id: String, title: String, body: @Composable () -> Unit) = GalleryEntry(id, "Exercise", title) {
    PaintLifeScreen { item { body() } }
}

private fun session(id: String, title: String, state: ExerciseSessionUiState) = GalleryEntry(id, "Exercise", title) {
    ExerciseSessionContent(state, {}, {}, {}, {}, {}, {}, {}, {})
}

private fun cat(id: String, title: String, entry: LeagueEntry) = GalleryEntry(id, "Exercise", title) {
    CatDetailContent(entry, previous = CatRoster.forLevel(entry.cat.level - 1), onStartTimedChallenge = {})
}

private fun victory(id: String, title: String, state: VictoryUiState) = GalleryEntry(id, "Exercise", title) {
    VictoryContent(state, onContinue = {})
}

private fun quiet(id: String, title: String, state: QuietUiState) = GalleryEntry(id, "Quiet Time", title) {
    QuietContent(state, {}, {}, {}, {}, {}, {})
}

private fun myTime(id: String, title: String, state: MyTimeUiState) = GalleryEntry(id, "My Time", title) {
    MyTimeContent(state, {}, {}, {}, {}, {}, {}, {})
}

private fun log(id: String, title: String, state: LogActivityUiState) = GalleryEntry(id, "My Time", title) {
    LogActivityContent(state, {}, {}, {}, {}, {}, {}, {})
}

private fun progress(id: String, title: String, state: ProgressUiState) = GalleryEntry(id, "Progress", title) {
    ProgressContent(state, onPeriodChange = {})
}

/** Every sample in the gallery, in review order. Also used by the layout tests. */
val galleryEntries: List<GalleryEntry> = listOf(
    GalleryEntry("foundations", "Design system", "Colours, type and swatches") { FoundationsSheet() },
    GalleryEntry("components", "Design system", "Components and states") { ComponentsSheet() },
    GalleryEntry("cats", "Design system", "Cat avatars") { CatAvatarSheet() },
    today("today_empty", "Empty morning", TodaySamples.emptyMorning),
    today("today_partial", "Partly painted", TodaySamples.partlyPainted),
    today("today_two", "Two priorities", TodaySamples.twoPriorities),
    today("today_full", "Fully painted evening", TodaySamples.fullyPainted),
    today("today_not_tracked", "Iteration 3: not tracked yet, carry-over offer", TodaySamples.notTrackedYet),
    exercise("exercise_first", "First day", ExerciseSamples.firstDay),
    exercise("exercise_midway", "Two cats beaten", ExerciseSamples.midway),
    exercise("exercise_restored", "Session restored after closing", ExerciseSamples.restoredSession),
    exercise("exercise_all", "All cats beaten", ExerciseSamples.allBeaten),
    exercise("exercise_restored_overnight", "Session restored from yesterday", ExerciseSamples.restoredOvernight),
    session("session_volume", "Counter, running", ExerciseSamples.volumeRunning),
    session("session_volume_paused", "Counter, paused", ExerciseSamples.volumePaused),
    session("session_timed", "Timed challenge, running", ExerciseSamples.timedRunning),
    session("session_timed_paused", "Timed challenge, paused", ExerciseSamples.timedPaused),
    session(
        "session_timed_finishing",
        "Timed challenge, 1,000 reached (undo window)",
        ExerciseSamples.timedRunning.copy(reps = 1_000, elapsedMillis = 27 * 60_000L + 48_300L, finishing = true, canUndo = false),
    ),
    cat("cat_current", "Cat detail, current", ExerciseSamples.catCurrent),
    cat("cat_beaten", "Cat detail, beaten", ExerciseSamples.catBeaten),
    cat("cat_locked", "Cat detail, locked", ExerciseSamples.catLocked),
    victory("victory", "Victory", ExerciseSamples.victory),
    victory("victory_champion", "Victory over Champion", ExerciseSamples.victoryChampion),
    victory("victory_replay", "Victory on a replay", ExerciseSamples.victoryReplay),
    GalleryEntry("complete_slower", "Exercise", "1,000 reps, slower than the cat") {
        ChallengeCompleteContent(ExerciseSamples.completeSlower, onContinue = {})
    },
    GalleryEntry("complete_tie", "Exercise", "1,000 reps, a tie") { ChallengeCompleteContent(ExerciseSamples.completeTie, onContinue = {}) },
    GalleryEntry("safety", "Exercise", "Safety guidance") { SafetyGuidanceContent(onDone = {}) },
    sheet("correct_count", "Correct count sheet") { CorrectCountBody(currentReps = 120, onSave = {}) },
    history("history", "Session history", ExerciseSamples.history),
    history("history_empty", "Session history, empty", ExerciseSamples.historyEmpty),
    sheet("history_editor", "Edit session sheet") { HistoryEditorBody(ExerciseSamples.historyEditor, { _, _ -> }, {}, {}) },
    sheet("history_editor_timed", "Edit timed session sheet") {
        HistoryEditorBody(ExerciseSamples.historyEditor.copy(canEditReps = false, canDelete = false), { _, _ -> }, {}, {})
    },
    quiet("quiet_ready", "Ready", QuietSamples.ready),
    quiet("quiet_running", "Running", QuietSamples.running),
    quiet("quiet_paused", "Paused", QuietSamples.paused),
    quiet("quiet_done", "Completed", QuietSamples.completed),
    myTime("my_time_day1", "Observation, day 1", MyTimeSamples.observationEmpty),
    myTime("my_time_day3", "Observation, day 3", MyTimeSamples.observationDay3),
    myTime("my_time_both", "After observation, actual and planned", MyTimeSamples.afterObservationBoth),
    myTime("my_time_planned", "Past day, planned only", MyTimeSamples.pastDayPlanned),
    myTime("my_time_dst", "Clocks go back (25-hour day)", MyTimeSamples.dstFallBack),
    log("log_new", "Log activity", MyTimeSamples.logNew),
    log("log_edit", "Edit overnight entry", MyTimeSamples.logEditOvernight),
    GalleryEntry("overlap", "My Time", "Overlap: fit it in or replace") { OverlapContent(MyTimeSamples.overlap, {}, {}, {}) },
    GalleryEntry("overlap_split", "My Time", "Overlap: split around a short entry") { OverlapContent(MyTimeSamples.overlapSplit, {}, {}, {}) },
    log("log_confirm_running", "Confirm a long-running activity", MyTimeSamples.confirmRunning),
    progress("progress_today", "Today, nothing yet", ProgressSamples.todayEmpty),
    progress("progress_week", "Last 7 days", ProgressSamples.lastSevenDays),
    progress("progress_month", "This month", ProgressSamples.thisMonth),
    progress("progress_last_month", "Last month", ProgressSamples.lastMonth),
)

@Composable
fun DesignGallery(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = galleryEntries.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        SampleFrame(open, onClose = { openId = null }, modifier = modifier)
    } else {
        GalleryIndex(onOpen = { openId = it }, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun GalleryIndex(onOpen: (String) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    PaintLifeScreen(modifier = modifier.testTag("design_gallery")) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.debug_design_gallery),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.debug_close_gallery), tint = colors.text)
                }
            }
            Text(text = stringResource(R.string.debug_gallery_note), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        galleryEntries.groupBy { it.section }.forEach { (section, entries) ->
            item(key = "section_$section") {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = section, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Tray(Modifier.fillMaxWidth()) {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) TrayDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button) { onOpen(entry.id) }
                                    .padding(horizontal = spacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = entry.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A sample shown full screen, with a "Sample" badge and a close button so it is never mistaken for real data. */
@Composable
private fun SampleFrame(entry: GalleryEntry, onClose: () -> Unit, modifier: Modifier) {
    val colors = PaintLifeTheme.colors
    Box(modifier.fillMaxSize().testTag("sample_${entry.id}")) {
        entry.content()
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(PaintLifeTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.debug_sample_badge),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onHighlight,
                modifier = Modifier
                    .clip(PaintLifeTheme.shapes.pill)
                    .background(colors.highlight)
                    .padding(horizontal = PaintLifeTheme.spacing.md, vertical = PaintLifeTheme.spacing.xs),
            )
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.debug_close_sample), tint = colors.text)
            }
        }
    }
}

