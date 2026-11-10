# Project manifest

Updated at the end of each iteration. Last update: Iteration 3 (Today screen and daily planning).

## Toolchain and versions

All versions are pinned in `gradle/libs.versions.toml`.

| Component | Version | Source |
|---|---|---|
| Gradle (wrapper, SHA-256 pinned) | 9.7.1 | https://gradle.org/releases/ |
| Gradle daemon JVM | 25 | https://docs.gradle.org/current/userguide/compatibility.html |
| Android Gradle Plugin | 9.3.3 | https://developer.android.com/build/releases/agp-9-3-0-release-notes |
| Kotlin (built-in via AGP, KGP pinned) | 2.4.21 | https://kotlinlang.org/docs/gradle-configure-project.html |
| Compose compiler plugin | 2.4.21 | https://developer.android.com/develop/ui/compose/bom |
| KSP | 2.3.13 | https://github.com/google/ksp/releases |
| Compose BOM | 2026.09.00 | https://developer.android.com/develop/ui/compose/bom |
| Room | 2.8.5 | https://developer.android.com/jetpack/androidx/releases/room |
| Hilt (+ hilt-android-testing) | 2.60.1 | https://github.com/google/dagger/releases |
| androidx.hilt (hilt-lifecycle-viewmodel-compose) | 1.4.0 | https://developer.android.com/jetpack/androidx/releases/hilt (needs AGP 9.2+, KGP 2.2+) |
| Navigation | 2.10.2 | https://developer.android.com/jetpack/androidx/releases/navigation |
| DataStore | 1.3.0 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Lifecycle | 2.11.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Activity | 1.13.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| androidx.core | 1.19.1 | https://developer.android.com/jetpack/androidx/releases/core |
| kotlinx.serialization | 1.12.0 | https://github.com/Kotlin/kotlinx.serialization/releases |
| kotlinx.coroutines | 1.11.0 | https://github.com/Kotlin/kotlinx.coroutines/releases |
| Robolectric | 4.17 | https://github.com/robolectric/robolectric/releases |
| androidx.test core / ext.junit / espresso | 1.7.0 / 1.3.0 / 3.7.0 | https://developer.android.com/jetpack/androidx/releases/test |
| SDK | min 26, compile/target 37 | |

Known deviations from the official tables:
- AGP 9.3.3 and Gradle 9.7.1 are one patch release past Kotlin 2.4.21's listed maximums
  (AGP 9.3.1, Gradle 9.7.0). The owner approved this to get the 9.3.2/9.3.3 fixes.
- KSP 2.3.13 and Hilt 2.60.1 don't name Kotlin 2.4 support in their docs. The build proves
  they work together.
- Lint reports newer Gradle (9.9.0) and AGP (9.5.1) releases. Those are deliberately not used,
  because Kotlin 2.4.21 officially supports AGP only up to 9.3.1.

## Files

### Build
- `settings.gradle.kts`, `build.gradle.kts` (pins KGP on the classpath), `gradle.properties`
- `gradle/libs.versions.toml`, `gradle/gradle-daemon-jvm.properties`
- `gradle/wrapper/gradle-wrapper.{jar,properties}`, `gradlew`, `gradlew.bat`
- `app/build.gradle.kts`: SDK levels, Java 17 bytecode, release signing guard, Room schema export, Robolectric JVM flags
- `app/proguard-rules.pro`, `keystore.properties.example`
- `app/schemas/com.mauforge.paintlife.core.database.PaintLifeDatabase/{1,2,3,4,5}.json`: generated, checked in

### Manifest and resources (`app/src/main/`)
- `AndroidManifest.xml`: `allowBackup=false`; Quiet Time permissions (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`) and `QuietTimerService` (`specialUse` with its subtype property)
- `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API 26-30): exclude everything
- `res/values/{strings,colors,themes}.xml`: dark window theme; all UI strings and plurals
- `res/values/cat_art_{ana,mia,zoe,eva,luna,sara,nina,maya,aria,champion}.xml`: TEMPORARY aliases of the placeholder, replaced by `res/drawable-nodpi/cat_<key>.webp` on import
- `res/drawable/cat_placeholder.xml`: TEMPORARY neutral cat avatar
- `res/drawable/ic_{check,lock,add,undo,pause,play,flag,info,chevron_left,chevron_right,close,edit,refresh,arrow_up,arrow_down}.xml`: Material icon shapes (Apache 2.0)
- `res/font/bricolage_grotesque.ttf`, `res/font/atkinson_hyperlegible_next.ttf`: bundled variable fonts (SIL OFL 1.1, licences in `docs/licenses/`)
- `res/drawable/ic_launcher_{foreground,monochrome}.xml`, `res/mipmap-anydpi/ic_launcher{,_round}.xml`
- `res/drawable/ic_nav_{today,exercise,quiet,my_time,progress}.xml`: original vector icons

### Kotlin (`app/src/main/kotlin/com/mauforge/paintlife/`)
- `PaintLifeApplication.kt`: Hilt app; records the first-launch date; creates the exercise and quiet session controllers at process start (recovery)
- `MainActivity.kt`: edge-to-edge, light system-bar icons on the dark theme, no nav-bar scrim; checkpoints running exercise and quiet sessions in `onStop`; a Quiet Time notification tap opens the Quiet tab
- `navigation/`: `Routes` (incl. the nested Exercise graph: tab, cat detail, session, result, safety, history; the nested My Time graph: timeline, insights, schedule proposal), `TopLevelDestination`, `PaintLifeNavHost`, `PaintLifeAppShell` (adaptive navigation suite, styled; hosts the debug menu slot; opens Quiet on request)
- `core/designsystem/theme/`: `Color` (dark palette, `CategoryPalette`), `PaintLifeColors` (extended roles, swatch rule), `Type` (fonts, `NumberStyles`), `Shape` (`ShapeTokens`), `Spacing`, `Theme` (dark only)
- `core/designsystem/component/`: `Cards` (`PaintLifeCard`, `HeroPanel`, `Tray`), `Buttons`, `Progress` (`ProgressRing`, `MeterBar`), `Swatch` (+ week, calendar), `BigNumber`, `States` (empty/loading/error), `PaintLifeScreen` (+ `SectionHeader`), `Controls` (`SegmentedPicker`, `CategoryChip`), `DayStrip` (+ hatching), `Locale`, `NotYetBuiltState`, `Sheets` (`PaintLifeSheet`, `ConfirmDialog`, field colours), `Motion` (`rememberReducedMotion`)
- `core/database/`: `PaintLifeDatabase` (v5), `Converters`, `Migrations` (`allMigrations`), `MigrationSql` (shared helpers), `Migration1To2`, `Migration2To3`, `Migration3To4`, `Migration4To5` (My Time for 6a and 6b), `SeedData` (+ `SeedCallback`), `SessionStatus`
- `core/datastore/UserPreferencesRepository.kt`: first-launch date, exercise safety read, victories seen, quiet notification permission asked, My Time observation complete, sleep card dismissed, insights and Progress periods
- `core/di/AppModule.kt`: database, DataStore, `TimeProvider`, `DayClock`, `SessionClock`, application scope
- `core/time/`: `TimeProvider`, `DayClock` (+ `DayMoment`, `DayPart`: which day it is, across midnight, zone and DST changes), `TimerCheckpoint`, `SessionClock` (monotonic clock + boot count), `SessionTimer` (pure pause/resume/checkpoint/recovery rules), `DurationFormat`
- `today/`: `TodayScreen` (wired: ViewModel, sheets, Undo snackbar), `TodayViewModel`, `TodayRepository` (+ `PriorityTitle`), `TodayDao`, `TodayEntities` (DailyRecord, Priority), `TodayUiState`, `TodayContent`, `TodaySheets` (add/edit, carry-over picker)
- `exercise/`: `ExerciseScreen` (tab, cat detail, session, result, safety and history routes), `ExerciseViewModels` (tab, cat detail, session, result, history, safety), `ExerciseSessionController` (the open session: counting, saves, checkpoints, recovery, timed finish window with Undo), `SessionCounter` (count + undo rules), `ExerciseRepository` (+ `SessionNote`, `PersonalBestKeys`; Cat League unlocks and best times), `ChallengeCompleteContent` (1,000 reps without a win), `ExerciseDao` (+ `ExerciseDayTotals`), `ExerciseEntities` (ChallengeLevel, ExerciseSession, PersonalBest), `ExerciseUiState` (+ `League` mapping, result states), `ExerciseContent`, `ExerciseSessionContent` (+ safety guidance), `ExerciseHistoryContent`, `ExerciseSheets` (correct count, edit session), `CatDetailContent` (+ victory)
- `exercise/cats/`: `CatRoster` (single source for cat words and art), `CatAvatar` (+ `CatArt` placeholder detection)
- `quiet/`: `QuietScreen` (wired: permission asked once, Undo snackbar, notices), `QuietViewModel`, `QuietSessionController` (the open session: timing, checkpoints, 5:00 completion, 30-minute pause limit, recovery as incomplete), `QuietTimerService` (`specialUse` foreground service, wake lock only while running, 5:00 alert), `QuietNotifications` (channels, ongoing countdown, alert), `QuietRepository`, `QuietDao`, `QuietRules` (what counts as completed, minutes, swatch fill), `QuietEntities` (QuietSession), `QuietUiState`, `QuietContent` (+ completion bloom)
- `mytime/`: `MyTimeScreen` (wired: log sheet for actual and planned, overlap choice, Undo, schedule-block prompt, categories sheet), `MyTimeViewModel` (layers, planning up to 7 days ahead), `TimeRepository` (save with overlap rules, delete/undo, running activity, sleep, observation, copy a schedule day, distraction flag), `TimeDao`, `TimeRules` (limits, rounding, overlap plan, midnight split, observation days, sleep prefill, repeated times), `DayView` (which zone a day is shown in, blocks, totals, strip), `MyTimeLaunch` (Today → My Time requests), `MyTimeEntities` (ActivityCategory, TimeEntry, CurrentActivity, DailySchedule, ScheduleBlock), `TimelineGeometry` (DST/midnight-aware), `MyTimeUiState`, `MyTimeContent` (+ now doing, log sheet with steppers, overlap), `PlannedLayer` (+ `ScheduleRules`, `ScheduleWithBlocks`: own plans vs approved schedule per day), `Insights` (pure totals, `InsightsPeriod`), `InsightsViewModel`, `InsightsContent` (+ `InsightsScreen`), `Observation` (shared observation-week flow for My Time, Insights and Progress), `ScheduleSuggester` (plain counting over observed days), `ScheduleRepository` (approve in one transaction), `ScheduleViewModel` (in-memory proposal), `ScheduleContent` (+ `ScheduleScreen`, block editor), `CategoriesContent` (+ schedule-block prompt)
- `progress/`: `ProgressScreen` (wired), `ProgressViewModel` (remembered period, follows `DayClock`, range reads only), `ProgressRules` (pure reports reusing each screen's own rules: exercise by start day, `QuietRules.dayTotals`, `Insights.report` + `DayView.stretches`), `ProgressUiState` (`ProgressPeriod.range`, `TimeSection` locked/ready), `ProgressContent`, `Charts` (category bars, planned vs actual, daily distribution)

### Tests (`app/src/test/`)
- `resources/robolectric.properties`: uses a plain Application in tests
- `core/database/`: `ConvertersTest`, `DatabaseSeedTest`, `MigrationTest` (v1 through v5, data kept)
- `core/datastore/UserPreferencesRepositoryTest`
- `core/time/SessionTimerTest` (pauses excluded, checkpoints, reboot), `core/time/TimeProviderTest`, `core/time/DurationFormatTest`, `core/time/DayClockTest` (midnight, noon, zone change, DST, missed broadcast)
- `today/TodayRepositoryTest`: max 3 (also concurrent), order, delete/restore, move, carry-over, zones, restart
- `today/TodayViewModelTest`: midnight rollover, date-line crossing, repeated taps, undo
- `today/TodayScreenTest`: Compose UI tests for add, complete, edit, move, delete + undo, carry-over, quiet swatch, quick actions, sleep card
- `quiet/QuietSessionControllerTest`: exact 5:00, pauses excluded, screen-off catch-up, finish early, cancel + undo, pause limit, killed → incomplete, reboot → incomplete, incomplete never counts, midnight, zone and DST
- `quiet/QuietRulesTest`: completed vs minutes vs incomplete, proportional fill
- `quiet/QuietScreenTest` (+ `QuietContentTest`): Compose UI end to end, notifications-off note
- `quiet/QuietTimerServiceTest`: foreground notification, wake lock only while running, Pause action, alert and stop at 5:00, channels
- `testing/TestTime`, `testing/FakeSessionClock`, `testing/TestAppModule` (Hilt test graph replacing `AppModule`; `TestMonotonic` offset)
- `core/designsystem/theme/ContrastTest`: WCAG ratios for every pairing and category colour
- `core/designsystem/component/ComponentsTest`: touch targets, semantics, real actions
- `exercise/SessionCounterTest`: +1/+5/+10, undo order, never below zero, corrections, reset
- `exercise/ExerciseSessionControllerTest`: real database file; restart and reboot recovery, pause/resume timing, one open session, discard, lifetime totals
- `exercise/ExerciseRepositoryTest`: midnight and zone changes, history edits and deletes, timed results read-only
- `exercise/CatLeagueTest`: strictly-faster unlock (−1 ms, tie, slower), stopped/interrupted never count, locked levels, replays, Champion, ladder
- `exercise/TimedChallengeControllerTest`: clock runs while paused, auto-finish at 1,000, undo window, result applied after a kill, stop, restart recovery
- `exercise/ChallengeResultTest`: victory vs completion, replay wins, each victory offered once
- `exercise/VictoryScreenTest`: Compose tests for the victory and completion screens
- `exercise/ExerciseSessionScreenTest`, `exercise/ExerciseHistoryScreenTest`: Compose UI end to end (step-ups and timed challenges)
- `exercise/cats/CatRosterTest`: roster vs seed data, names, placeholder slots
- `mytime/TimelineGeometryTest`: midnight split, DST 23/25-hour days, gaps
- `mytime/TimeRulesTest`: entries crossing midnight (spec §7), own-zone splits, DST nights and repeated times, limits, rounding, overlap plans, observation days, sleep prefill
- `mytime/DayViewTest`: clipping, totals, the day so far, which zone a day is shown in
- `mytime/TimeRepositoryTest`: overlaps (trim, split, replace, undo), edit, delete + undo, running activity (switch, under a minute, over 16 hours, overlaps, restart), recent, sleep, observation, 5-year history through the index
- `mytime/MyTimeScreenTest`: Compose UI for gap tap, overlap fit + undo, delete + undo, now doing, past days, observation banner, insights hidden until 7 days, planning tomorrow, change a schedule block for one day
- `mytime/PlannedLayerTest`: own plans vs schedule, history ranges, blocks across midnight and DST, block overlaps
- `mytime/InsightsTest`: planned never counts as actual, planned vs actual on plan days only, midnight split, DST 23/25-hour days, distraction flag changes, hourly patterns
- `mytime/ScheduleSuggesterTest`: not enough days, half-the-days rule, ties, 15-minute slots, patchy and irregular weeks, weekday/weekend split, sleep across midnight, DST
- `mytime/PlanningRepositoryTest`: planned never counts as actual, planned overlaps, approval history, invalid proposals, change for this day + undo, plans on schedule days, distraction flag
- `mytime/PlanningViewModelTest`: insights hidden before 7 days, flag change updates insights, remembered period, suggestion saved only on approval, discard confirmation
- `mytime/PlanningFixtures`: entries, schedules and categories for the pure tests
- `testing/TestPreferences`: throwaway DataStore
- `debug/ScreenLayoutsTest`: every gallery sample at 200% text on 320dp, plus layout rules
- `progress/ProgressRulesTest`: period ranges (month start/end, January, leap February), quiet consistency (today only once done, weekly lines), minutes rounded once, records, cats beaten in their own zone, midnight split across months, today up to now, DST 23/25-hour days, agreement with Insights, empty periods
- `progress/ProgressViewModelTest`: real database; Today period equals the Today screen, This month equals the Quiet screen, remembered period, midnight, zone change, 7-day gate, empty database
- `progress/ProgressLargeHistoryTest`: 5 years of records; each period reads only its own days, exact month totals, query plans use the date/start indexes
- `progress/ProgressScreenTest`: Compose UI for switching periods and the gated time section
- `navigation/NavigationTest`: the real activity under Hilt test + Robolectric

### Debug only (`app/src/debug/`), never in release
- `AndroidManifest.xml`: `DesignGalleryActivity`
- `res/drawable/ic_debug.xml`, `res/values/debug_strings.xml`
- `kotlin/.../debug/DebugMenu.kt`: top-corner menu opening the gallery
- `kotlin/.../debug/DesignGallery.kt`: gallery activity, index and full-screen sample frame with a "Sample" badge
- `kotlin/.../debug/DesignSheets.kt`: foundations, components and cat avatar review sheets
- `kotlin/.../debug/SampleStates.kt`: labelled sample UI states for every screen
- `kotlin/.../debug/PlanningSamples.kt`: planned day, insights and schedule proposal samples (computed by the real rules)
- `kotlin/.../debug/DesignPreviews.kt`: Android Studio previews

### Release only (`app/src/release/`)
- `kotlin/.../debug/DebugMenu.kt`: empty, so release has no debug menu

### Docs
- `docs/licenses/OFL-BricolageGrotesque.txt`, `docs/licenses/OFL-AtkinsonHyperlegibleNext.txt`
