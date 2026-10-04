# iOS launch crash investigation — 2026-10-04

The user reported a crash immediately after launch. No device/version or crash trace has been supplied yet, so the reported incident has not been reproduced.

## Concrete defects and repairs

`iosApp/QuranApp/Info.plist` omitted the Boolean `CADisableMinimumFrameDurationOnPhone=true`. `MainViewController` creates `ComposeUIViewController` with its default strict plist check. JetBrains documents a startup crash for this omission: https://kotlinlang.org/docs/multiplatform/whats-new-compose-170.html#disabling-minimum-frame-duration-on-ios-is-mandatory . The required setting is now present; strict enforcement is retained.

Local Xcode 15.4/iOS 17.5 simulator app linking with Compose 1.11.1 failed on `_OBJC_CLASS_$_UIViewLayoutRegion` from CMPLayoutRegion.o. A candidate Compose 1.12.1 update includes runtime lookup introduced by upstream PR 3278: https://github.com/JetBrains/compose-multiplatform-core/pull/3278 . The candidate fixed local iOS linking, but Android requires SDK 37/AGP 9.1 for that release. It was reverted to avoid an unrelated build-system migration. Final dependencies remain Compose 1.11.1, Kotlin 2.3.20 and Navigation 3 1.1.1. The upstream fix describes older Xcode 16 compatibility; it alone does not establish full Xcode 15.4 support.

The existing iOS CI job compiled/linked only. It now launches the built app on an available iPhone simulator and requires positive bundle/PID confirmation before a 20-second process-lifetime check. Early exit, missing launch confirmation or screenshot failure fails the check. Console output and a screenshot are retained. This smoke check catches launch failures; it is not full iOS navigation, media or device acceptance.

## Evidence and limits

- Plist syntax, Python syntax and diff whitespace checks passed.
- Post-plist-fix shared framework build passed (2 seconds); assembled application plist contained the required Boolean true. Application linking still failed on UIViewLayoutRegion before the dependency update.
- Independent Sol 6.1 review cleared the plist, smoke-check logic after repairing a false-positive risk: a stalled simctl process cannot substitute for bundle/PID confirmation.
- The candidate Compose update linked successfully on Xcode 15.4, but was reverted after Android compatibility failed; that success is not evidence for the final dependency set.
- Final shared JVM tests and Android debug APK build passed (7 seconds, `/private/tmp/quran-ios-plist-final-build.log`).
- With final dependencies, local shared iOS compilation passes but Xcode 15.4 application linking remains blocked by UIViewLayoutRegion. The assembled app plist was checked and contains the required Boolean true.
- Both local iPhone 15 and iPhone SE simulators failed to boot with `launchd_sim` unable to bind to the session. This is a simulator-service failure before the Quran app starts; no successful runtime launch is claimed.
- PRs #11 and #12 remain closed at the user's request. The user subsequently authorized committing the narrow launch fix and opening a new PR; the closed design changes are excluded from that PR.
