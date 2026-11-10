package com.mauforge.paintlife.debug

import com.mauforge.paintlife.core.database.SeedData
import com.mauforge.paintlife.mytime.ActivityCategoryEntity
import com.mauforge.paintlife.mytime.BlockEditorUi
import com.mauforge.paintlife.mytime.BlockProblem
import com.mauforge.paintlife.mytime.CategoryOption
import com.mauforge.paintlife.mytime.EvidenceGroup
import com.mauforge.paintlife.mytime.Insights
import com.mauforge.paintlife.mytime.InsightsPeriod
import com.mauforge.paintlife.mytime.InsightsUiState
import com.mauforge.paintlife.mytime.InsightsViewModel
import com.mauforge.paintlife.mytime.MyTimeUiState
import com.mauforge.paintlife.mytime.ProposalBlockUi
import com.mauforge.paintlife.mytime.ScheduleContentUi
import com.mauforge.paintlife.mytime.ScheduleRules
import com.mauforge.paintlife.mytime.ScheduleSuggester
import com.mauforge.paintlife.mytime.SuggestionResult
import com.mauforge.paintlife.mytime.TimeEntryEntity
import com.mauforge.paintlife.mytime.TimeEntryKind
import com.mauforge.paintlife.mytime.TimeLayer
import com.mauforge.paintlife.mytime.TimelineBlock
import com.mauforge.paintlife.mytime.TimelineGeometry
import com.mauforge.paintlife.mytime.MinuteSpan
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug-only sample states for the planned layer, insights and the schedule proposal. The
 * insights and proposal are computed by the real rules from a made-up fortnight.
 */
object PlanningSamples {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val today: LocalDate = LocalDate.of(2026, 11, 10)

    private val categories: List<ActivityCategoryEntity> = SeedData.categories.mapIndexed { i, c ->
        ActivityCategoryEntity(c.id, c.name, c.colorKey, c.isDistraction, i, isBuiltIn = true, isArchived = false)
    }

    private val options = categories.map { CategoryOption(it.id, it.name, it.colorKey, it.isDistraction) }

    /** Two weeks of fairly regular days, with some evenings left unrecorded. */
    private val fortnight: List<TimeEntryEntity> = (1..14).flatMap { back ->
        val date = today.minusDays(back.toLong())
        val weekend = date.dayOfWeek.value >= 6
        fun entry(category: String, fromH: Int, fromM: Int, toDate: LocalDate, toH: Int, toM: Int): TimeEntryEntity {
            val start = date.atTime(fromH, fromM).atZone(zone).toInstant()
            return TimeEntryEntity(
                id = "$date-$category-$fromH",
                kind = TimeEntryKind.ACTUAL,
                categoryId = category,
                startAt = start,
                endAt = toDate.atTime(toH, toM).atZone(zone).toInstant(),
                zoneId = zone,
                endZoneId = zone,
                note = null,
                createdAt = start,
                createdZoneId = zone,
                updatedAt = start,
                updatedZoneId = zone,
            )
        }
        buildList {
            add(entry("sleep", 0, 0, date, if (weekend) 8 else 7, 0))
            add(entry("personal_care", if (weekend) 8 else 7, 0, date, if (weekend) 9 else 8, 0))
            if (weekend) {
                add(entry("family", 10, 0, date, 13, 0))
                add(entry("entertainment", 14, 0, date, 16, 0))
            } else {
                add(entry("work", 9, 0, date, 12, 30))
                add(entry("phone_internet", 12, 30, date, 13, 0))
                add(entry("work", 13, 0, date, 17, 30))
                if (back % 3 != 0) add(entry("exercise", 18, 0, date, 18, 45))
            }
            add(entry("sleep", 23, 0, date.plusDays(1), 0, 0))
        }
    }

    val insightsReady: InsightsUiState = InsightsUiState.Ready(
        InsightsViewModel.toUi(
            InsightsPeriod.Week,
            Insights.report(today.minusDays(7), today.minusDays(1), today, zone, fortnight, emptyList(), emptyList(), categories),
            categories,
        ),
    )

    val insightsLocked: InsightsUiState = InsightsUiState.Locked(4)

    val proposal: ScheduleContentUi = run {
        var n = 0
        val result = ScheduleSuggester.suggest(today, zone, fortnight) { "b${n++}" } as SuggestionResult.Ready
        val byId = categories.associateBy { it.id }
        ScheduleContentUi.Proposal(
            basedOnFrom = result.basedOnFrom,
            basedOnTo = result.basedOnTo,
            recordedDays = result.recordedDays,
            unrecordedPercent = result.unrecordedPercent,
            split = result.split,
            name = "My schedule",
            blocks = result.blocks.map { b ->
                ProposalBlockUi(
                    id = b.id,
                    categoryName = byId.getValue(b.categoryId).name,
                    colorKey = byId.getValue(b.categoryId).colorKey,
                    startMinute = b.startMinute,
                    endMinute = b.endMinute,
                    daysMask = b.daysMask,
                    seenDays = b.seenDays,
                    groupDays = b.groupDays,
                    group = when (b.daysMask) {
                        ScheduleRules.WEEKDAYS -> EvidenceGroup.Weekdays
                        ScheduleRules.WEEKEND -> EvidenceGroup.Weekend
                        else -> EvidenceGroup.Days
                    },
                )
            },
            replacesCurrent = false,
            approving = false,
        )
    }

    val notEnough: ScheduleContentUi = ScheduleContentUi.NotEnough(4)

    val blockEditorOverlap = BlockEditorUi(
        isNew = false,
        categories = options,
        selectedCategoryId = "learning",
        startMinute = 12 * 60,
        endMinute = 13 * 60,
        daysMask = ScheduleRules.WEEKDAYS,
        problem = BlockProblem.Overlaps,
        overlapsWith = "Work 09:00–12:30",
    )

    val categoryOptions: List<CategoryOption> = options

    /** Tomorrow: plans only, from the approved schedule. */
    val futureDay: MyTimeUiState = run {
        val date = today.plusDays(1)
        fun block(id: String, from: Int, to: Int, key: String, name: String) =
            TimelineBlock(id, MinuteSpan(from, to), "%02d:%02d".format(java.util.Locale.ROOT, from / 60, from % 60), "%02d:%02d".format(java.util.Locale.ROOT, to / 60, to % 60), name, key, false, fromSchedule = true)
        MyTimeUiState(
            date = date,
            isToday = false,
            layer = TimeLayer.Planned,
            actual = emptyList(),
            planned = listOf(
                block("schedule:a:$date", 0, 7 * 60, "night", "Sleep"),
                block("schedule:b:$date", 9 * 60, 12 * 60 + 30, "slate", "Work"),
                block("schedule:c:$date", 13 * 60, 17 * 60 + 30, "slate", "Work"),
                block("schedule:d:$date", 23 * 60, 24 * 60, "night", "Sleep"),
            ),
            hourMarks = TimelineGeometry.hourMarks(date, zone),
            dayLengthMinutes = TimelineGeometry.dayLengthMinutes(date, zone),
            nowMinute = null,
            observedDays = null,
            insightsReady = true,
            layersAvailable = false,
            isFuture = true,
            canGoForward = true,
        )
    }
}
