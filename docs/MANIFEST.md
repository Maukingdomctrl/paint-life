# Project manifest

Updated at the end of each iteration. Last update: Iteration 1 (foundation and architecture).

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
| Hilt | 2.60.1 | https://github.com/google/dagger/releases |
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
- `app/schemas/com.mauforge.paintlife.core.database.PaintLifeDatabase/1.json`: generated, checked in

### Manifest and resources (`app/src/main/`)
- `AndroidManifest.xml`: no permissions, `allowBackup=false`
- `res/xml/data_extraction_rules.xml` (API 31+) and `res/xml/backup_rules.xml` (API 26-30): exclude everything
- `res/values/{strings,colors,themes}.xml`
- `res/drawable/ic_launcher_{foreground,monochrome}.xml`, `res/mipmap-anydpi/ic_launcher{,_round}.xml`
- `res/drawable/ic_nav_{today,exercise,quiet,my_time,progress}.xml`: original vector icons

### Kotlin (`app/src/main/kotlin/com/mauforge/paintlife/`)
- `PaintLifeApplication.kt`: Hilt app; records the first-launch date
- `MainActivity.kt`: edge-to-edge, dark system-bar icons, hosts the shell
- `navigation/`: `Routes`, `TopLevelDestination`, `PaintLifeNavHost`, `PaintLifeAppShell` (adaptive navigation suite)
- `core/designsystem/theme/`: `Color`, `Type`, `Shape`, `Spacing`, `Theme`
- `core/designsystem/component/`: `PaintLifeCard`, `NotYetBuiltState`
- `core/database/`: `PaintLifeDatabase` (v1), `Converters`, `Migrations`, `SeedData` (+ `SeedCallback`), `SessionStatus`
- `core/datastore/UserPreferencesRepository.kt`
- `core/di/AppModule.kt`: database, DataStore, `TimeProvider`, application scope
- `core/time/`: `TimeProvider`, `TimerCheckpoint`
- `today/`: `TodayScreen`, `TodayEntities` (DailyRecord, Priority)
- `exercise/`: `ExerciseScreen`, `ExerciseEntities` (ChallengeLevel, ExerciseSession, PersonalBest)
- `quiet/`: `QuietScreen`, `QuietEntities` (QuietSession)
- `mytime/`: `MyTimeScreen`, `MyTimeEntities` (ActivityCategory, TimeEntry, DailySchedule, ScheduleBlock)
- `progress/ProgressScreen.kt`

### Tests (`app/src/test/`)
- `resources/robolectric.properties`: uses a plain Application in tests
- `core/database/`: `ConvertersTest`, `DatabaseSeedTest`, `MigrationTest`
- `core/datastore/UserPreferencesRepositoryTest`
- `core/time/TimeProviderTest`
- `navigation/NavigationTest`: Compose UI test under Robolectric
