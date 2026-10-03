# Quran KMP

A modular Android/iOS Quran app built with Compose Multiplatform and Navigation 3. This directory is the new app; the repository root retains the original Quran Android implementation and its license/history.

## Open and run

Open this `multiplatform/` directory as the Gradle project in Android Studio. Use JDK 17 and an installed Android SDK. Set `sdk.dir` in an untracked `local.properties` if Android Studio does not create it automatically.

```sh
./gradlew :core:model:jvmTest :core:domain:jvmTest :core:data:jvmTest :feature:memorization:jvmTest
./gradlew :androidApp:assembleDebug :androidApp:lintDebug
```

For iOS, open `iosApp/QuranApp.xcodeproj` and select the shared `QuranApp` scheme. The Xcode build phase builds the Kotlin framework. Kotlin 2.3.20's supported Xcode version is 26.0; see the architecture and QA documents for local toolchain limitations.

```sh
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
xcodebuild -project iosApp/QuranApp.xcodeproj -scheme QuranApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

## Features and boundaries

- Offline Arabic Quran reading with canonical verse identifiers and all 114 surahs.
- Saved reading position, bookmarks, language, children-mode preference and explicit memorization progress.
- Finite repeat counts or repetition until the learner marks the verse memorized; hide/reveal and self-recitation practice.
- Playback of local verse audio supplied by the user, through Android Media3 and iOS AVFoundation. Hosted reciter catalogs and downloads need a configured content provider.
- English and Arabic UI with RTL support. Interface language does not supply a Quran translation or tafsir.
- Qibla great-circle direction from true north, with manual coordinates and optional native heading support. Hardware/permission failures keep the manual calculation available.
- Children mode with simplified practice controls. It is a local learning preference, not an authenticated parental-control system.
- Guided offline learning. AI integration is deferred at the user's request.

The new reader does not claim equivalence with the legacy app's image-based Madani page layout, gapless reciter catalog, basmallah timing metadata or downloadable translation/tafsir packages. The original app remains available while these capabilities migrate.

## Structure

`core:model` owns canonical identities. `core:domain` owns contracts and deterministic behavior. `core:data` implements corpus/persistence/native adapters. `core:designsystem` provides shared components and tokens. Feature modules expose presentation functions; `composeApp` assembles dependencies and owns typed Navigation 3 destinations. `androidApp` and `iosApp` are platform hosts.

Read [architecture](docs/architecture.md), [upstream reuse](docs/upstream-reuse.md) and [actual verification evidence](docs/qa/verification.md). RED and GREEN test artifacts are preserved under `docs/qa/`; passing JVM tests alone do not establish native app verification.

## Design and provenance

The [Stitch project](https://stitch.withgoogle.com/projects/260892313540630519) supplies visual references. Generated designs must not be treated as authoritative Quran text, translations or scholarly commentary.

Arabic text is bundled from [Tanzil](https://tanzil.net), Uthmani version 1.1, and must remain unaltered. Its full notice and source are in `core/data/content/`. The app provides source attribution. Repository source remains subject to the upstream GPL-3.0 license; Quran content and audio have their own source licenses.

GitHub CI builds both platform hosts and runs shared tests. The fork's [PR acceptance policy](../.github/PR_ACCEPTANCE.md) describes the recurring reviewer and merge gates.
