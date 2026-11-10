# Paint Life — working rules

The product spec is `docs/SPEC.md`. If this file and the spec disagree, this file wins; if anything is unclear, ask.

## Process
- Work through the 8 iterations in `docs/SPEC.md` §9 in order. Stop after each one, report in the format from spec §8, and wait for my go-ahead.
- Never change scope without asking me. That includes adding, dropping or reinterpreting features.
- A task is done only when `./gradlew assembleDebug testDebugUnitTest lintDebug` passes. Never claim a build passed without running it.
- Run the `build-check` skill before reporting any task or iteration as done.
- Never run `git commit` or `git push`. When work is ready, tell me and suggest a commit message.

## Build and versions
- Use stable versions that work together, checked against official compatibility docs (AGP↔Gradle↔Kotlin↔JDK, Compose BOM, Hilt, Room/KSP). Pin every version in `gradle/libs.versions.toml`.
- The Gradle runtime uses JDK 25. Java `sourceCompatibility`/`targetCompatibility` and Kotlin `jvmTarget` are 17. Never downgrade the JDK without telling me.
- minSdk 26. compileSdk/targetSdk = the latest stable API level, checked at setup.
- Use Hilt only if it works with the chosen AGP. If it doesn't, use manual constructor injection through one app-level container.

## Code structure
- One `:app` module, organised by feature (`today/`, `exercise/`, `quiet/`, `mytime/`, `progress/`, plus `core/` for the design system, database, time and backup).
- Keep it lean: no interface unless it has 2+ real implementations (test fakes don't count as a reason). MVVM, with immutable UI state in StateFlow.

## Time and dates
- Store timestamps as an `Instant` (epoch millis, UTC) plus the zone ID it was recorded in. Derive local dates from those two. Never store only local date-times.
- Measure active duration with a monotonic clock (`SystemClock.elapsedRealtime`) and save checkpoints regularly. Never time anything with a Compose recomposition timer.
- Midnight: My Time entries are split at local midnight, so each day gets its share. Exercise and quiet sessions count on the day they started.
- Handle midnight rollover, time-zone changes and DST. A date change never deletes data.

## Visual design (decided in Iteration 2; overrides the cream direction in docs/SPEC.md §3)
- Dark theme only, inspired by GitHub Dark / VS Code Dark. No light theme in v1. Status and navigation bar icons are light.
- Palette: background #0D1117, cards #161B22, raised #1C2128, sunken #010409, borders #30363D, text #E6EDF3, secondary text #8B949E, green #238636 (filled buttons, white label) and #3FB950 (accents/text), amber #D29922 / #BB8009, blue #58A6FF (info/links), red #F85149 (errors and destructive actions only). Shades may change only to pass contrast; `ContrastTest` enforces it.
- Swatch rule everywhere: Exercise paints green, Quiet paints amber, Time paints in its category colours, partly painted = filled in proportion, unpainted = sunken surface. Never red for a missed day.
- Category colours are muted, spread in lightness and at least 3:1 against #161B22. Charts and timelines always name categories in text, so colour is never the only cue.
- Quiet Time keeps its own calm full-screen look (sunken background, one amber ring) within this palette.
- Fonts: Bricolage Grotesque for headings and cat names, Atkinson Hyperlegible Next for text and all numbers (tabular figures). Both bundled, SIL OFL.
- The Design gallery and all sample states live only in `app/src/debug/`. Release builds show the honest "Coming in Iteration N" screens until each feature is wired.

## Exercise / Cat League
- The timed 1,000-rep challenge is in v1. The next cat unlocks only when the user beats the current cat's benchmark, and beating means strictly faster (a tie doesn't unlock).
- In timed mode the user can pause, but the clock keeps running. In normal sessions, paused time is left out of active duration.
- Lifetime total reps (volume) and timed results are separate achievements. Earlier levels can be replayed. Never raise intensity automatically, and never punish missed days.
- Cat artwork: I will provide it. Until then, use one neutral placeholder that is clearly marked as temporary and loads from a single place, so swapping in my art means replacing files only. Never load images from remote URLs.
- Cat photos arrive as `art/cats/01_ana.png` … `10_champion.png`. On import: centre-crop square, resize to 640×640, convert to WebP, save as `app/src/main/res/drawable-nodpi/cat_<key>.webp`, and delete the matching `res/values/cat_art_<key>.xml` placeholder alias. Avatars are circular, with fixed sizes (40/56/96/160dp); locked = greyscale at 55% plus a lock badge.

## Quiet Time
- The timer runs in a foreground service with an ongoing notification and an alert when it finishes. Request `POST_NOTIFICATIONS` (API 33+) at the moment it's needed, and handle a refusal gracefully.
- The timer is based on timestamps, so it stays correct when the screen is locked, the app is in the background or the user is interrupted. An incomplete session never counts as a completed 5-minute session.
- Foreground service type `specialUse`, with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explanation (`shortService` is too short). No `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`.
- The 5:00 alert plays the system notification sound and vibration on its own channel, and respects silent mode and Do Not Disturb.
- `WAKE_LOCK` is allowed for Quiet Time only. Hold a partial wake lock only while the timer runs (timeout = remaining time plus a few seconds), and release it on pause, finish, cancel and 5:00.
- Cancel discards the session at once and shows an Undo snackbar. Nothing is saved.
- Finish early saves the session as `ENDED_EARLY`, which never counts as completed. Its minutes count toward the monthly minutes, and quiet swatches fill in proportion (minutes ÷ 5). A fully painted day needs one completed 5:00 session that started that day.
- A session paused for 30 minutes is saved automatically as `ENDED_EARLY` and its notification is removed.
- The ongoing notification shows the countdown and a Pause/Resume action only. Tapping it opens Quiet Time. Finish and Cancel are only in the app.

## Phone reboot or app being killed
- Exercise: restore the session as paused, with the reps and active time from the last saved checkpoint. The user then resumes or finishes it.
- Quiet Time: save the session as incomplete. Never count it as done.

## My Time
- Actual entries never overlap. If saving one would overlap, offer to trim or replace the neighbouring entry. Planned entries are a separate layer.
- A "distraction" is any category with a distraction flag, which the user can change. Phone/internet and Entertainment have it on by default.
- The first 7 days are observation. Suggested schedules always need the user's approval.
- Observation ends once 7 days each have at least 1 hour of actual time recorded. Insights (where time went, unrecorded time, distractions, hourly patterns, Suggest a schedule) unlock only then. The banner shows "N of 7 days recorded".
- Entries are stored to the whole minute and last from 1 minute to 24 hours. Time pickers move in 5-minute steps and "now" is rounded to 5 minutes. A running activity records to the minute.
- Planned entries never overlap other planned entries. The two layers are independent.
- Overlaps: a neighbour that overlaps partly can be trimmed, one that is completely covered is replaced, and a new entry inside a longer one splits it in two. The sheet lists every affected entry, and an Undo snackbar follows.
- Unrecorded time is every minute not covered by an actual entry: all of a past day (23 or 25 hours on DST days), and today up to now.
- Any past day can be corrected. Nothing is locked.
- Fast logging: tap a gap to fill it; "Log since last entry" (end of the last entry to now) on Today and My Time; a running "now doing" activity; recent activities (the last 6 distinct category + note pairs) as one-tap chips.
- Running "now doing": starting a new activity ends and saves the current one at that moment. Stopping or switching one that has run more than 16 hours asks the user to confirm its end time first.
- Sleep: each morning until sleep is logged, Today offers "Log last night's sleep", prefilled from the median of the last 7 logged sleeps (23:00 to 07:00 at first). It can be dismissed.
- Categories are the spec's 11 fixed ones. Only the distraction flag can be changed. Today's flag applies to all past days.

## My Time: planned layer, insights and schedules (decided in Iteration 6b)
- Planned entries never count as actual time. Every total, swatch, observation count, insight and recent chip reads actual entries only. Fast logging (gap fill, Log since last entry, now doing, sleep) is actual-only.
- The planned layer works from day one (no prompts to plan), on past days, today and up to 7 days ahead. Future days accept planned entries only.
- An approved schedule is the default plan: from its approval date on, a day without its own planned entries shows the schedule's blocks in the Planned lane. "Change for this day" copies that day's blocks into planned entries for that day only (with Undo). Past days are never rewritten.
- Approving a schedule makes it active from today; the previous approved schedule ends yesterday. A proposal is kept in memory only and written to the database solely on approval (one transaction). Leaving an edited proposal asks "Discard this suggestion?".
- Insights cover complete days ending yesterday, with a remembered "Last 7 days / Last 4 weeks" switch. Each day is read in its own zone, split at local midnight, 23/25 hours on DST days. Planned vs actual counts only days that had a plan. No percentages, scores or "on track" labels.
- Distraction flags are edited in a Categories sheet reachable from My Time (from day one) and from the Distractions section in Insights.
- Suggest a schedule is plain counting: the last 28 complete days, recorded days only (at least 1 hour actual), at least 7 needed. Each day is cut into 48 half-hour wall-clock slots; a slot's category that day is the one covering at least 15 of its 30 minutes (else not recorded). A category goes into a slot only if it was picked on at least half of the group's recorded days; ties or less leave the slot open. Adjacent equal slots merge into blocks, each showing its evidence ("seen on 5 of 6 weekdays"). Weekdays and weekends split when each has at least 2 recorded days, else one pattern. Distraction categories are treated like any other time. Never reminders or prompts outside My Time.

## Destructive actions
- Small deletions that are easy to put back (for example, deleting one priority) happen at once and show an Undo snackbar. No confirmation dialog.
- Bigger destructive actions (resetting counts, deleting history, replacing data on import) always ask for confirmation first.

## Data and backup
- Use Room with explicit migrations and stable IDs. Never use destructive migration fallbacks. Wrap multi-table writes in transactions.
- Provide JSON export/import through the system file picker (SAF), so no storage permission is needed. The JSON is versioned. Import validates the file and confirms before replacing anything.
- Android Auto Backup is off: set `android:allowBackup="false"` and exclude everything in `data_extraction_rules.xml` (cloud backup and device transfer, API 31+) and `backup_rules.xml` (API 26–30). Data leaves the device only through a manual JSON export.
- Request no permissions beyond `POST_NOTIFICATIONS` and the foreground-service permissions without asking me first.

## Release
- Sign release builds with my own keystore. Read its details from a gitignored `keystore.properties` file or environment variables. Never commit keystores or passwords. If neither is present, the build should fail with a clear message, not fall back to the debug key.

## Skills
- When doing Compose/UI work, use the project skills in `.claude/skills/` (compose-*, edge-to-edge, mobile-android-design, frontend-design).
