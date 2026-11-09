package com.mauforge.paintlife.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme

// Android Studio previews of every gallery sample. Debug source set only: sample states never
// reach release builds. Names start with "Sample" so a preview is never mistaken for real data.

@Composable
private fun SamplePreview(id: String) {
    PaintLifeTheme { galleryEntries.first { it.id == id }.content() }
}

private const val W = 390
private const val H = 844
private const val BG = 0xFF0D1117

@Preview(name = "Sample · Foundations", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun FoundationsPreview() = SamplePreview("foundations")

@Preview(name = "Sample · Components", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun ComponentsPreview() = SamplePreview("components")

@Preview(name = "Sample · Cat avatars", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun CatsPreview() = SamplePreview("cats")

@Preview(name = "Sample · Today, empty", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun TodayEmptyPreview() = SamplePreview("today_empty")

@Preview(name = "Sample · Today, partly painted", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun TodayPartialPreview() = SamplePreview("today_partial")

@Preview(name = "Sample · Today, partly painted, 200% text", widthDp = W, heightDp = H, fontScale = 2f, showBackground = true, backgroundColor = BG)
@Composable private fun TodayLargeTextPreview() = SamplePreview("today_partial")

@Preview(name = "Sample · Today, fully painted", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun TodayFullPreview() = SamplePreview("today_full")

@Preview(name = "Sample · Exercise, first day", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun ExerciseFirstPreview() = SamplePreview("exercise_first")

@Preview(name = "Sample · Exercise, two cats beaten", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun ExerciseMidwayPreview() = SamplePreview("exercise_midway")

@Preview(name = "Sample · Counter, running", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun SessionPreview() = SamplePreview("session_volume")

@Preview(name = "Sample · Timed challenge, paused", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun TimedPausedPreview() = SamplePreview("session_timed_paused")

@Preview(name = "Sample · Cat detail, locked", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun CatLockedPreview() = SamplePreview("cat_locked")

@Preview(name = "Sample · Victory", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun VictoryPreview() = SamplePreview("victory")

@Preview(name = "Sample · Quiet, running", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun QuietRunningPreview() = SamplePreview("quiet_running")

@Preview(name = "Sample · Quiet, completed", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun QuietDonePreview() = SamplePreview("quiet_done")

@Preview(name = "Sample · My Time, day 3", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun MyTimeDay3Preview() = SamplePreview("my_time_day3")

@Preview(name = "Sample · My Time, actual and planned", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun MyTimeBothPreview() = SamplePreview("my_time_both")

@Preview(name = "Sample · Log activity", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun LogPreview() = SamplePreview("log_new")

@Preview(name = "Sample · Overlap", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun OverlapPreview() = SamplePreview("overlap")

@Preview(name = "Sample · Progress, 7 days", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun ProgressWeekPreview() = SamplePreview("progress_week")

@Preview(name = "Sample · Progress, this month", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun ProgressMonthPreview() = SamplePreview("progress_month")

@Preview(name = "Sample · Design gallery", widthDp = W, heightDp = H, showBackground = true, backgroundColor = BG)
@Composable private fun GalleryPreview() = PaintLifeTheme { DesignGallery(onClose = {}) }
