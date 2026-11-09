package com.mauforge.paintlife.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mauforge.paintlife.R
import com.mauforge.paintlife.core.designsystem.component.BigNumber
import com.mauforge.paintlife.core.designsystem.component.CategoryChip
import com.mauforge.paintlife.core.designsystem.component.DayStrip
import com.mauforge.paintlife.core.designsystem.component.DayStripBlock
import com.mauforge.paintlife.core.designsystem.component.DestructiveButton
import com.mauforge.paintlife.core.designsystem.component.EmptyState
import com.mauforge.paintlife.core.designsystem.component.ErrorState
import com.mauforge.paintlife.core.designsystem.component.HeroPanel
import com.mauforge.paintlife.core.designsystem.component.LoadingState
import com.mauforge.paintlife.core.designsystem.component.MeterBar
import com.mauforge.paintlife.core.designsystem.component.PaintLifeCard
import com.mauforge.paintlife.core.designsystem.component.PaintLifeScreen
import com.mauforge.paintlife.core.designsystem.component.PrimaryButton
import com.mauforge.paintlife.core.designsystem.component.ProgressRing
import com.mauforge.paintlife.core.designsystem.component.QuietButton
import com.mauforge.paintlife.core.designsystem.component.SecondaryButton
import com.mauforge.paintlife.core.designsystem.component.SegmentedPicker
import com.mauforge.paintlife.core.designsystem.component.Swatch
import com.mauforge.paintlife.core.designsystem.component.SwatchCalendar
import com.mauforge.paintlife.core.designsystem.component.SwatchDay
import com.mauforge.paintlife.core.designsystem.component.SwatchSegment
import com.mauforge.paintlife.core.designsystem.component.SwatchWeek
import com.mauforge.paintlife.core.designsystem.component.Tray
import com.mauforge.paintlife.core.designsystem.component.TrayDivider
import com.mauforge.paintlife.core.designsystem.theme.CategoryPalette
import com.mauforge.paintlife.core.designsystem.theme.PaintLifePalette
import com.mauforge.paintlife.core.designsystem.theme.PaintLifeTheme
import com.mauforge.paintlife.core.database.SeedData
import com.mauforge.paintlife.exercise.cats.CatAvatar
import com.mauforge.paintlife.exercise.cats.CatAvatarSize
import com.mauforge.paintlife.exercise.cats.CatAvatarState
import com.mauforge.paintlife.exercise.cats.CatRoster
import java.time.LocalDate
import java.time.YearMonth

// Debug-only review sheets for the design system. Sample values only.

private fun LazyListScope.sheetTitle(title: String, note: String) {
    item(key = "sheet_title") {
        Column(verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
            Text(text = note, style = MaterialTheme.typography.bodyMedium, color = PaintLifeTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoundationsSheet() {
    val colors = PaintLifeTheme.colors
    val spacing = PaintLifeTheme.spacing
    val tokens = listOf(
        Triple("Canvas", PaintLifePalette.Canvas, "Background"),
        Triple("Surface", PaintLifePalette.Surface, "Cards"),
        Triple("Raised", PaintLifePalette.Raised, "Hero panels, selected"),
        Triple("Sunken", PaintLifePalette.Sunken, "Trays, unpainted swatch"),
        Triple("Border", PaintLifePalette.Border, "Hairlines"),
        Triple("Text", PaintLifePalette.Text, "Primary text"),
        Triple("Text secondary", PaintLifePalette.TextSecondary, "Labels, hints"),
        Triple("Green", PaintLifePalette.Green, "Filled buttons"),
        Triple("Green bright", PaintLifePalette.GreenBright, "Accents, Exercise paint"),
        Triple("Amber", PaintLifePalette.Amber, "Progress, Quiet paint"),
        Triple("Amber soft", PaintLifePalette.AmberSoft, "Softer fills"),
        Triple("Blue", PaintLifePalette.Blue, "Info, links"),
        Triple("Red", PaintLifePalette.Red, "Errors and destructive only"),
    )
    PaintLifeScreen {
        sheetTitle("Foundations", "Dark only. Every text pairing is checked in ContrastTest.")
        item(key = "palette") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Heading("Palette")
                tokens.forEach { (name, color, role) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(PaintLifeTheme.shapes.chip).background(color).border(1.dp, colors.border, PaintLifeTheme.shapes.chip))
                        Column {
                            Text(text = "$name  ${hex(color)}", style = MaterialTheme.typography.titleSmall)
                            Text(text = role, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        item(key = "categories") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Heading("My Time categories")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SeedData.categories.forEach { CategoryChip(it.name, CategoryPalette.forKey(it.colorKey), isDistraction = it.isDistraction) }
                }
            }
        }
        item(key = "type") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Heading("Type")
                val type = MaterialTheme.typography
                listOf<Pair<String, TextStyle>>(
                    "Display small · Bricolage" to type.displaySmall,
                    "Headline medium · Bricolage" to type.headlineMedium,
                    "Title large · Atkinson" to type.titleLarge,
                    "Body large · Atkinson, for everything you read" to type.bodyLarge,
                    "Label large · Atkinson" to type.labelLarge,
                ).forEach { (text, style) -> Text(text = text, style = style) }
                val numbers = PaintLifeTheme.numbers
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.Bottom) {
                    Text("640", style = numbers.hero)
                    Text("29:41", style = numbers.large)
                    Text("5h 40m", style = numbers.medium)
                }
                Text(text = "1Il 0O 5S 1111 0000", style = numbers.medium)
            }
        }
        item(key = "swatches") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("Swatches")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    LabelledSwatch("Unpainted", emptyList())
                    LabelledSwatch("Exercise", listOf(SwatchSegment(colors.swatchExercise, 1f)))
                    LabelledSwatch("Quiet", listOf(SwatchSegment(colors.swatchQuiet, 1f)))
                    LabelledSwatch(
                        "Time, 9h",
                        listOf(
                            SwatchSegment(colors.category("night"), 7f / 24),
                            SwatchSegment(colors.category("sage"), 1f / 24),
                            SwatchSegment(colors.category("slate"), 1f / 24),
                        ),
                    )
                }
                val start = LocalDate.of(2026, 11, 9)
                SwatchWeek(
                    days = (0L..6L).map { SwatchDay(start.plusDays(it), if (it % 2 == 0L) listOf(SwatchSegment(colors.swatchQuiet, 1f)) else emptyList()) },
                    describe = { it.date.toString() },
                )
                SwatchCalendar(
                    month = YearMonth.of(2026, 11),
                    fills = (1..12).filter { it % 3 != 0 }.associate { LocalDate.of(2026, 11, it) to listOf(SwatchSegment(colors.swatchQuiet, 1f)) },
                    describe = { date, painted -> "$date ${if (painted) "painted" else "unpainted"}" },
                )
            }
        }
    }
}

@Composable
private fun LabelledSwatch(label: String, segments: List<SwatchSegment>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PaintLifeTheme.spacing.xs)) {
        Swatch(segments, contentDescription = label, modifier = Modifier.width(64.dp).height(80.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = PaintLifeTheme.colors.textSecondary)
    }
}

@Composable
fun ComponentsSheet() {
    val spacing = PaintLifeTheme.spacing
    val colors = PaintLifeTheme.colors
    var period by remember { mutableStateOf("7 days") }
    var selectedChip by remember { mutableStateOf("work") }
    PaintLifeScreen {
        sheetTitle("Components", "Every reusable piece, with sample values.")
        item(key = "buttons") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Heading("Buttons")
                PrimaryButton("Start session", {}, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_play))
                SecondaryButton("Replay level", {}, Modifier.fillMaxWidth())
                QuietButton("Correct count", {}, icon = painterResource(R.drawable.ic_edit))
                DestructiveButton("Delete entry", {}, Modifier.fillMaxWidth())
                PrimaryButton("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)
            }
        }
        item(key = "containers") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Heading("Containers")
                HeroPanel(Modifier.fillMaxWidth()) { Text("Hero panel: one per screen", style = MaterialTheme.typography.titleMedium) }
                PaintLifeCard(Modifier.fillMaxWidth()) { Text("Card: the default block", style = MaterialTheme.typography.titleMedium) }
                Tray(Modifier.fillMaxWidth()) {
                    Text("Tray row one", Modifier.padding(spacing.lg))
                    TrayDivider()
                    Text("Tray row two", Modifier.padding(spacing.lg))
                }
            }
        }
        item(key = "progress") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("Progress")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(0.6f, "3 of 5 minutes", size = 120.dp, strokeWidth = 10.dp) {
                        Text("3:00", style = PaintLifeTheme.numbers.small)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        MeterBar(0.64f, "640 of 1,000")
                        MeterBar(0.4f, "400 of 1,000", color = colors.highlight, goalMarker = 0.75f)
                    }
                }
                BigNumber("640", unit = "step-ups", label = "Lifetime step-ups", style = PaintLifeTheme.numbers.hero)
                DayStrip(
                    blocks = listOf(
                        DayStripBlock(0, 420, colors.category("night")),
                        DayStripBlock(420, 480, null),
                        DayStripBlock(480, 720, colors.category("slate")),
                        DayStripBlock(720, 800, colors.category("amber")),
                    ),
                    contentDescription = "Sample day strip",
                )
            }
        }
        item(key = "controls") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("Controls")
                SegmentedPicker(listOf("Today", "7 days", "This month", "Last month"), period, { period = it }, label = { it })
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    CategoryChip("Work", colors.category("slate"), selected = selectedChip == "work", onClick = { selectedChip = "work" })
                    CategoryChip("Phone", colors.category("amber"), isDistraction = true, selected = selectedChip == "phone", onClick = { selectedChip = "phone" })
                }
            }
        }
        item(key = "states") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("States")
                PaintLifeCard(Modifier.fillMaxWidth()) {
                    EmptyState("No sessions yet", "Sessions you finish will show here.", action = { SecondaryButton("Start session", {}) })
                }
                PaintLifeCard(Modifier.fillMaxWidth()) { LoadingState() }
                PaintLifeCard(Modifier.fillMaxWidth()) {
                    ErrorState("Couldn't read the backup file", "The file isn't a Paint Life export. Choose a different file.", onRetry = {})
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatAvatarSheet() {
    val spacing = PaintLifeTheme.spacing
    val ana = CatRoster.cats.first()
    PaintLifeScreen {
        sheetTitle("Cat avatars", "Temporary placeholder art until the photos are imported. Initials tell the cats apart.")
        item(key = "states") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("States")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    CatAvatarState.entries.forEach { state ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CatAvatar(ana, state, size = CatAvatarSize.Large)
                            Text(state.name, style = MaterialTheme.typography.labelMedium, color = PaintLifeTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }
        item(key = "sizes") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("Sizes")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.Bottom) {
                    CatAvatarSize.entries.forEach { CatAvatar(ana, CatAvatarState.Current, size = it) }
                }
            }
        }
        item(key = "roster") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Heading("All ten, by state")
                CatAvatarState.entries.forEach { state ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        CatRoster.cats.forEach { CatAvatar(it, state, size = CatAvatarSize.Small) }
                    }
                }
            }
        }
    }
}
