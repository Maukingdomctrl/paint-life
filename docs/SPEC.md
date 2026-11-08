# PROJECT: PAINT LIFE
## Production-quality Android application — v1.0

You are acting as a senior Android architect, product engineer, UI/UX designer, Kotlin developer, database engineer and QA engineer.

Your responsibility is to design and implement a complete, production-quality personal life management application called **Paint Life**.

This is not a tutorial application, a sample project, a prototype or a basic CRUD application.

The goal is to deliver a polished, reliable Android application that the owner can use every day for years.

The project must be developed through eight controlled implementation iterations, with each iteration building on the previous one.

Do not attempt to generate the entire project in one response.

---

# 1. PRODUCT VISION

Paint Life helps the user improve their actual life through:

1. Physical activity and progressive challenges.
2. Five minutes of daily quiet time.
3. Awareness and control of daily time.
4. Daily priorities and manageable routines.
5. Weekly and monthly self-assessment.

The product is designed for someone who benefits from:
- Clear targets.
- Visual progress.
- Competition and achievements.
- Simple instructions.
- Small actionable steps.
- Flexible routines.
- Minimal cognitive overhead.

The application must reduce friction rather than introduce more work.

The application should feel like a premium personal companion, not a corporate productivity dashboard.

# 2. NON-NEGOTIABLE DEVELOPMENT REQUIREMENTS

## Technology

Use the newest mutually compatible stable versions available at project creation.

Target baseline:
- JDK 25 for the Gradle runtime.
- Gradle 9.5.0 or a newer compatible stable version.
- Android Gradle Plugin 9.3.1 or a newer mutually compatible stable version.
- Kotlin 2.4.21 or a newer compatible stable version.
- Kotlin DSL.
- Jetpack Compose.
- Material 3.
- Room.
- DataStore.
- Kotlin Coroutines and Flow.
- Navigation Compose.

Check official compatibility documentation before selecting versions.

Never silently downgrade the requested JDK.

Do not blindly set Android bytecode compatibility to Java 25. Distinguish build JDK, Java toolchain, Kotlin JVM target, Android bytecode and Android runtime API compatibility.

If the available Android Studio or installed SDK creates a compatibility limitation, explain the exact limitation and provide the correct solution.

Use a Gradle version catalog (`libs.versions.toml`).

Pin dependency versions. Avoid dynamic dependency versions.

No deprecated APIs where modern supported alternatives exist.

## Architecture

Use a modular, maintainable MVVM architecture.

Prefer:
- Clean separation of presentation, domain and data.
- Repository pattern.
- Dependency injection with Hilt, if compatible with the selected AGP.
- Room database.
- DataStore for preferences.
- StateFlow and immutable UI state.
- Coroutines for asynchronous operations.
- Navigation Compose.
- Unit tests.
- Compose UI tests.

Avoid unnecessary abstraction and excessive interfaces.

Do not create an overengineered architecture that makes a small application difficult to maintain.

## Application restrictions

- Android application.
- Offline-first.
- No account creation.
- No backend.
- No subscriptions.
- No advertisements.
- No external AI APIs.
- No unnecessary permissions.
- All personal data stays locally on the device.

# 3. DESIGN DIRECTION

Design the application before implementing business logic.

The UI must be polished from the beginning.

## Visual identity

Application name: Paint Life.

Visual language:
- Deep forest green.
- Warm amber.
- Warm cream background.
- Soft neutral surfaces.
- Carefully selected typography.
- Large readable numerical displays.
- Consistent spacing.
- Subtle shadows.
- Refined rounded cards.
- Restrained animations.

Create a reusable design system:
- Colour tokens.
- Typography scale.
- Spacing scale.
- Shapes.
- Buttons.
- Cards.
- Progress indicators.
- Navigation components.
- Empty states.
- Loading states.
- Error states.

Use Material 3 as a foundation, not as the finished visual design.

The result must not resemble an unmodified Material 3 sample application.

Support:
- Android edge-to-edge layout.
- Status and navigation bar insets.
- Different screen sizes.
- Accessibility font scaling.
- Dark theme only if it can be implemented consistently without compromising the primary design.

# 4. APPLICATION INFORMATION ARCHITECTURE

Create five primary destinations.

## SCREEN A: TODAY

Purpose: Give the user a clear picture of today.

Features:
- Date and day.
- Greeting.
- Three daily priorities maximum.
- Exercise progress.
- Quiet-time progress.
- Today's time summary.
- Quick actions.
- Daily completion overview.

The screen should answer:

"What matters today?"

Avoid displaying too many metrics simultaneously.

Allow users to edit priorities and mark them complete.

## SCREEN B: EXERCISE — PAINT LIFE CAT LEAGUE

This is a major application feature.

Create a visually engaging exercise challenge system with ten fictional cat opponents.

All opponents must be cats.

No human competitors.

### Cat characters

1. Ana — orange tabby.
2. Mia — white Persian.
3. Zoe — grey Scottish Fold.
4. Eva — black cat.
5. Luna — calico.
6. Sara — Maine Coon.
7. Nina — Siamese.
8. Maya — Bengal.
9. Aria — tuxedo cat.
10. Champion — distinctive tortoiseshell cat.

Each character needs:
- Unique illustrated avatar.
- Name.
- Breed/appearance.
- Personality.
- Challenge level.
- Fictional benchmark.
- Locked/unlocked state.
- Victory celebration.

Use original locally stored illustrations or bundled artwork. Never depend on remote image URLs.

### Challenge benchmarks

The fictional benchmark is 1,000 repetitions.

| Level | Character | Benchmark |
|---|---|---|
| 1 | Ana | 30 minutes |
| 2 | Mia | 29 minutes |
| 3 | Zoe | 28 minutes |
| 4 | Eva | 27 minutes |
| 5 | Luna | 26 minutes |
| 6 | Sara | 25 minutes |
| 7 | Nina | 24 minutes |
| 8 | Maya | 23 minutes |
| 9 | Aria | 22 minutes |
| 10 | Champion | 21 minutes |

These are fictional game benchmarks, not medical recommendations.

Keep volume accumulation and timed competition as separate achievements.

### Initial exercise objective

The user's initial goal is 1,000 cumulative step-ups across multiple sessions.

A timed 1,000-repetition challenge is a separate optional activity after sufficient preparation.

### Counting definition

One complete repetition means:
- Step onto the platform.
- Bring both feet onto it.
- Return both feet to the floor.

One complete up-and-down cycle equals one repetition.

Do not count each foot placement as a separate repetition.

### Manual counter

Implement:
- Large +1 control.
- +5 control.
- +10 control.
- Undo.
- Session pause.
- Session resume.
- Finish session.
- Reset with confirmation.
- Automatic session persistence.
- Recovery after unexpected app closure.
- Manual count correction.

### Exercise timing

Use a monotonic elapsed-time source for accurate session duration.

Pause time must not be included in active exercise duration.

Do not rely on a Compose recomposition timer for timing accuracy.

### Exercise records

Store:
- Session ID.
- Date.
- Start time.
- End time.
- Active duration.
- Repetitions.
- Challenge level.
- Session type.
- Completion status.
- Notes.

### Unlock system

The next opponent unlocks only when the user beats the current opponent's benchmark in the optional timed challenge.

Show:
- Opponent benchmark.
- User's achieved time.
- Personal best.
- Remaining repetitions.
- Challenge completion.

Allow previous levels to be replayed.

Never force the user to attempt a faster challenge.

### Safety

Include warm-up guidance, comfortable step height, alternating lead legs, rest breaks and clear stop guidance for pain, dizziness, chest discomfort or unusual breathlessness.

Never automatically increase exercise intensity.

No punishment for missed sessions.

## SCREEN C: QUIET TIME

Purpose: Establish a five-minute daily quiet habit.

Features:
- Five-minute timer.
- Attractive circular timer visualization.
- Start.
- Pause.
- Resume.
- Finish.
- Cancel.
- Completion animation.
- Daily completion record.
- Weekly history.
- Monthly total.

The timer must remain accurate when:
- The screen is locked.
- The application moves to the background.
- The device is temporarily interrupted.

Use a suitable Android lifecycle-aware timing design.

Do not record an incomplete session as a completed five-minute session.

## SCREEN D: MY TIME

This is one of the most important features.

Purpose: Help the user discover where their day actually goes.

Create a visual 24-hour timeline.

The user must be able to:
- View each hour.
- Record an activity.
- Edit an activity.
- Correct historical records.
- Categorise activities.
- Add start and end times.
- Record planned activities separately from actual activities.

Initial categories:
- Personal care.
- Exercise.
- Work.
- Learning.
- Household.
- Family.
- Rest.
- Entertainment.
- Phone/internet.
- Sleep.
- Other.

Support activities crossing hour boundaries.

Calculate actual duration accurately.

The first seven days should operate primarily as an observation period.

Do not force a rigid schedule immediately.

After seven days, show:
- Where time was spent.
- Unclassified time.
- Common distractions.
- Planned versus actual time.
- Hourly activity patterns.

Allow the user to create a proposed schedule based on observed data.

The user must approve all suggested schedules.

## SCREEN E: PROGRESS

Create useful reports for:
- Today.
- Last seven days.
- Current month.
- Previous month.

Exercise analytics:
- Total repetitions.
- Number of sessions.
- Average session duration.
- Personal bests.
- Cat League levels unlocked.

Quiet-time analytics:
- Completed sessions.
- Total minutes.
- Weekly consistency.
- Monthly consistency.

Time analytics:
- Hours per category.
- Planned versus actual.
- Daily distribution.
- Unclassified time.

Use well-designed charts and visualisations.

Avoid misleading productivity scores.

Do not punish missed days or display shame-based messaging.

# 5. DATABASE AND DATA INTEGRITY

Use Room.

Create appropriate entities, DAOs, relationships and migrations.

At minimum:
- DailyRecord.
- Priority.
- ExerciseSession.
- ChallengeLevel.
- PersonalBest.
- QuietSession.
- TimeEntry.
- ActivityCategory.
- DailySchedule.

Use stable IDs.

Avoid duplicate records.

Use transactions where required.

Preserve historical data when application versions change.

Never delete user data merely because a date changes.

Implement appropriate backup/export options only if they fit the fixed scope.

# 6. APPLICATION QUALITY

The application must handle:

- App restarts.
- Phone restarts.
- Interrupted timers.
- Midnight transitions.
- Time-zone changes.
- Daylight-saving changes.
- Empty database.
- Incorrect or incomplete records.
- Repeated taps.
- Accidental deletion.
- Database migration.
- Large historical datasets.

Provide confirmations for destructive actions.

Use accessible labels and adequate touch targets.

No dead buttons.

No fake analytics.

No placeholder screens presented as finished functionality.

# 7. TESTING REQUIREMENTS

Create:
- Unit tests for business logic.
- Repository tests.
- Database tests.
- Timer tests.
- Challenge progression tests.
- Time calculations tests.
- Compose UI tests for important interactions.

Test the counting logic carefully.

Test pause/resume behaviour.

Test cumulative totals across sessions.

Test that a timed challenge cannot unlock the next cat without beating its benchmark.

Test that time entries crossing midnight are calculated correctly.

Test Room persistence after application restart.

# 8. DELIVERY REQUIREMENTS

Final deliverable:
- Complete Android Studio project.
- Source code.
- Gradle wrapper.
- Version catalog.
- Room entities and migrations.
- Tests.
- Local artwork.
- README.
- Build instructions.
- Release APK.

Do not claim a successful build unless the build command actually succeeded.

Do not provide incomplete files, pseudo-code or ellipses in place of implementations.

Maintain a project file manifest.

At the end of each iteration, report:
1. Files created.
2. Files modified.
3. Important implementation decisions.
4. Build status.
5. Tests executed.
6. Remaining issues.
7. Exact next iteration instruction.

Do not repeat previously completed work.

Do not silently change established architecture.

# 9. PROJECT EXECUTION

The project will be completed in eight iterations.

Iteration 1: Foundation and architecture.
Iteration 2: Complete visual design and all screen layouts.
Iteration 3: Today screen and daily planning.
Iteration 4: Exercise and Cat League.
Iteration 5: Quiet Time.
Iteration 6: My Time.
Iteration 7: Progress and analytics.
Iteration 8: Integration, testing and release.

Each iteration must be substantial and leave the project buildable.

Maintain continuity across iterations.

Wait for the next explicit iteration instruction before beginning the next stage.

Begin by analysing the requirements and producing the technical architecture, dependency compatibility matrix, complete project structure and Iteration 1 implementation plan.

Do not start with a generic tutorial or a minimal demonstration.

The final objective is a genuinely useful, polished personal Android application.
