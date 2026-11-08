# Paint Life

Offline Android companion for daily movement, five minutes of quiet,
and knowing where your time goes.

- **Today** – up to three priorities and a calm daily overview
- **Exercise** – step-up counter and the Paint Life Cat League
- **Quiet Time** – a five-minute daily timer
- **My Time** – a 24-hour timeline of where the day actually goes
- **Progress** – weekly and monthly reports, no guilt

No account, no ads, no internet. All data stays on the device.

Built with Kotlin, Jetpack Compose, Material 3, Room and DataStore.
Status: in development (Iteration 1 of 8 complete: foundation and app shell).

## Build

Requirements: JDK 25 installed (Gradle runs on it), Android SDK with platform 37 and
build-tools 36.0.0. Android Studio 2026.2.2 or newer works.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

- Gradle picks JDK 25 automatically through `gradle/gradle-daemon-jvm.properties`, whatever
  `java` is first on your PATH. JDK 25 must be installed locally (no auto-download is configured).
- `local.properties` (gitignored) holds `sdk.dir` if `ANDROID_HOME` is not set.
- All tests, including Room and Compose UI tests, run on the JVM through Robolectric.
- The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Windows: home folder with a space

Robolectric mis-encodes spaces in its Maven cache path. If your user folder contains a space,
add a space-free path (the 8.3 short name works) to your **user** `~/.gradle/gradle.properties`:

```properties
paintlife.robolectric.mavenRepoLocal=C:/Users/SHORTN~1/.m2/repository
```

### Release build

Release builds are signed with your own keystore and never fall back to the debug key.
Copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill it in, or set
`PAINTLIFE_STORE_FILE`, `PAINTLIFE_STORE_PASSWORD`, `PAINTLIFE_KEY_ALIAS` and
`PAINTLIFE_KEY_PASSWORD`. Then run `./gradlew assembleRelease`.
