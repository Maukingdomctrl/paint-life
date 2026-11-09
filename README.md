# Paint Life

Offline Android companion for daily movement, five minutes of quiet,
and knowing where your time goes.

- **Today** – up to three priorities and a calm daily overview
- **Exercise** – step-up counter and the Paint Life Cat League
- **Quiet Time** – a five-minute daily timer
- **My Time** – a 24-hour timeline of where the day actually goes
- **Progress** – weekly and monthly reports, no guilt

No account, no ads, no internet. All data stays on the device.

Built with Kotlin, Jetpack Compose, Material 3, Room and DataStore. Dark theme only.
Status: in development (Iteration 2 of 8 complete: visual design and all screen layouts).

## Design gallery (debug builds only)

Debug builds show a small bug icon in the top-right corner. Tap it, then **Design gallery**, to
review every component and all five screens in labelled sample states. Each sample carries a
"Sample" badge. Release builds have no gallery and no sample data.

## Cat photos

The Cat League avatars use one temporary placeholder until the real photos are added.

1. Put the photos in `art/cats/` as `01_ana.png`, `02_mia.png`, `03_zoe.png`, `04_eva.png`,
   `05_luna.png`, `06_sara.png`, `07_nina.png`, `08_maya.png`, `09_aria.png`, `10_champion.png`.
   This folder is not packaged into the app.
2. For each photo: crop to a centred square, resize to 640×640 and save as WebP (quality about 85)
   at `app/src/main/res/drawable-nodpi/cat_<name>.webp`, e.g. `cat_ana.webp`. Use `cwebp` or
   Android Studio's *Convert to WebP*.
3. Delete the matching placeholder alias `app/src/main/res/values/cat_art_<name>.xml`.
   Keeping both causes a duplicate-resource build error, which is deliberate.
4. If a cat's face sits off-centre after the circular crop, set `photoAlignment` for that cat in
   `exercise/cats/CatRoster.kt`.

No code changes are needed. When all ten are in, `res/drawable/cat_placeholder.xml` can go, and
`CatRosterTest` should be updated, since it currently expects the placeholder.

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
