# Verification evidence

Validation is specific to the new KMP application. The legacy upstream application's feature parity is not established by these tests.

## TDD and domain/data checks

Preserved JUnit XML in `red-domain/` captures 10 failing tests out of 11 before implementation. `red-data/` captures 4 failing persistence tests before implementation. The failures originate in explicitly unimplemented model/domain/data functions; some repetition tests initially fail at verse validation during construction. These demonstrate a preimplementation failing run, not isolated coverage of every later UI behavior.

The independently rerun JVM suites on 2026-10-03 pass all 18 tests: 3 canonical model tests, 4 repetition tests, 4 Qibla tests, 4 persistence tests and 3 bundled-corpus tests. Copies of the actual XML are in `green-domain/` and `green-data/`. Corpus checks visit all 114 chapters and 6,236 canonical verse identities, check contiguous verse numbers, unique identities and nonblank Arabic/source fields, and confirm changing interface language preserves Arabic text. These structural checks do not replace checking the corpus against its authoritative source.

`red-lifecycle/compile.log` captures the playback lifecycle regression tests failing to compile before the presentation controller exists. The controller implementation subsequently passes all four added tests, preserved in `green-lifecycle/`, for a total of 22 passing JVM tests. These cover stale completion callbacks after pause/resume, stopping old playback before explicit memorization, reset/disposal cancellation, manual progress double-counting and fixed-count completion.

## Platform validation

Local tools: Corretto JDK 17.0.17, Gradle 8.13, Android SDK 36 and Xcode 15.4 (15F31d). After repairing the errors exposed by actual compilation, `:androidApp:assembleDebug :androidApp:lintDebug :androidApp:connectedDebugAndroidTest` succeeds. Lint reports zero errors and 12 warnings, chiefly version catalog suggestions, a missing launcher icon, and Android 12 data extraction rules. The actual lint output is preserved in `green-android/lint.txt`.

Kotlin's [official compatibility table](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html) lists Xcode 26.0 for Kotlin 2.3.20. This machine's Xcode 15.4 is outside that supported combination. Actual `:composeApp:linkDebugFrameworkIosSimulatorArm64` nevertheless compiles and links successfully in 3m 20s without compatibility suppression. This proves that shared framework build on this installed toolchain; the Swift host build and runtime behavior have separate validation requirements. A JVM test pass must not be reported as native validation.

The actual Swift host build compiles Swift and reaches the native app linker, then fails with undefined `_OBJC_CLASS_$_UIViewLayoutRegion` from Compose's `CMPLayoutRegion.o`. The installed iOS 17.5 simulator SDK also cannot resolve auto-linked `UIUtilities` and `_LocationEssentials` frameworks; an ICU object reports an iOS simulator 18.5 build version. These are concrete toolchain/SDK incompatibilities, so the iOS app host is **not locally build-verified**. The first hosted CI run used Xcode 26 on Apple Silicon and successfully linked the KMP framework, then failed because the Xcode build phase inherited unsupported `x86_64`. CI is now pinned to arm64 and will rerun with the next source revision. The host remains unverified until that check passes.

Executed host command: `xcodebuild -project iosApp/QuranApp.xcodeproj -scheme QuranApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build`, with Corretto JDK 17 and Gradle 8.13. No provider credentials, signing identity or Apple account were used.

## Android runtime acceptance

Four Android instrumentation tests exercise the actual `MainActivity` on the existing Pixel 8 Pro API 33 emulator, all passing with zero failures or skips. The final `connectedDebugAndroidTest lintDebug` invocation succeeds in 27 seconds. Actual JUnit XML is in `green-android/`.

| Executed journey | Verified behavior |
| --- | --- |
| Library → reader → practice → back | Verse bookmark remains saved; hide/reveal changes the rendered exercise; explicit memorization disables its action and increments stored progress; Navigation 3 returns through reader to library. |
| English → Arabic → Activity recreation | English navigation positions read left to right; Arabic positions mirror right to left; Arabic preference survives recreating the real Activity; switching back restores English. |
| Children mode → Activity recreation → practice | Child mode persists; guided practice starts with three repetitions; manual completion increments the count and does not automatically mark the verse memorized. |
| Native MediaSessionService playback | A synthetic 0.5-second silent PCM WAV fixture completes two actual native playbacks through the real platform adapter and repetition controller, with exactly two completion counts and no reported playback error. The fixture is not Quran recitation. |

This is automated UI and native-player verification, not a manual visual design review. Native computer-use UI access currently fails with `Sky Computer Use service startup request failed`, so an interactive screenshot review was not performed. Physical compass calibration, iOS device playback/document-picker behavior, background/lock-screen media controls, interruption/audio-focus scenarios and full legacy reciter parity require additional device acceptance checks.
