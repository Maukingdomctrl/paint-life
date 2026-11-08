---
name: build-check
description: Verify the Paint Life Android project builds, passes unit tests and lint before any task or iteration is reported as done. Use whenever you are about to say work is finished, ready, or passing, or the user asks to build, test, lint, or check the project.
---

# Build check

A task counts as done only when this passes, so run it yourself rather than predicting the result.

1. If the project root has no `gradlew`, say "No Gradle wrapper yet (it is created in Iteration 1)" and stop.
2. Run from the project root (Bash; in PowerShell use `.\gradlew.bat`):
   `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain`
3. Pass → report (step 6). Fail → fix, then rerun the full command.

## Fixing failures
- Show only the relevant error lines (first error, failing test names, lint issue IDs with file:line), not the whole log.
- State the root cause in one line, fix that cause, rerun. Repeat until it passes.
- Fix the code, not the check. Without asking the user first, never: delete, skip, or `@Ignore` tests; weaken assertions; lower lint severity or add a lint baseline; add `@Suppress`/`@SuppressLint`/`tools:ignore`; downgrade or swap versions. These hide the problem the check exists to catch.
- Track attempts per distinct error. If the same error survives 3 fix attempts, stop and explain: the error, what you tried, and your best theory and options.

## Report on success
- Status: PASS
- Tests: total run, failed, skipped. Sum the `tests`/`failures`/`skipped` attributes in `app/build/test-results/testDebugUnitTest/*.xml`.
- Lint: warning count from `app/build/reports/lint-results-debug.xml` (count `severity="Warning"` issues); mention any new ones by ID.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (confirm it exists).

Never run `git commit` or `git push`. If work is ready, suggest a commit message instead.
