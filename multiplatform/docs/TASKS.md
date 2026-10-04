# Quran app delivery tasks

This list tracks the requested app, beyond individual pull requests. AI integration is deferred by the user. A PR merge finishes its slice; it does not finish the app.

## Delivered slices

- [x] Fork-based modular KMP Android/iOS foundation, domain/data/features separated.
- [x] Navigation 3 routes, reader entry positions and canonical Surah/Juz browsing.
- [x] Real English/Arabic XML resources and RTL app layout.
- [x] Persisted reading, bookmarks, language, children preference and explicit memorization.
- [x] Shared theme, text styles, buttons, chips, inputs, switches, dialog, bottom sheet and ScreenWrapper/scaffold.
- [x] Offline Quran text and on-demand translations with source attribution.
- [x] Qibla calculation and opt-in native compass adapters.
- [x] Native media service/adapters and one-ayah reciter downloads/repetition (PR #4).
- [x] Bounded same-surah practice ranges, verse-aware queue playback and explicit per-ayah memorization (PR #5).
- [x] Separate persisted Arabic/translation reading sizes (PR #6, all three CI checks passed).
- [x] Current-head CI review/acceptance policy for Android tests/runtime and iOS framework/app.

## Active batch

- [x] Stitch refinement request and generated Library/Reader design direction inspected.
- [x] Shared navigation vectors, compact single library heading and full-row chapter actions; independent source review clear.
- [x] Combined shared tests/UI compilation and Android app/test APK build passed.
- [x] Native 11-journey regression/visual suite, 24 English/Arabic light/dark/150%-font captures; final spacing visual rerun passed.
- [x] Publish design refinement as PR #7.
- [x] PR #7 merged as `54c97a4891e65b2ef2b91a1d1fdbcf2bbf7f0555` after independent review and all three current-head CI gates passed (run `37180877098`).

## Next unblocked batch

Download management PR #8 merged as `f3017c42ff6f475937c455fd9b64cfbb129e5cfc`. Its reviewed head `478a05cca` passed all three CI gates, 13 native tests and independent inspection of the new downloads capture. The next slice, explicit one-ayah reader listening, is implemented and independently reviewed: 109 JVM tests and Android app/test compilation passed. Published as PR #9 at `83dbbea289c0dcf65c55c61d03c9d6e4146df59e`; its Android/test CI passed, with native runtime and iOS acceptance still running. The next direct memorized-ayah review slice is implemented, with a real navigation/persistence journey authored and combined build passed (109 JVM tests and Android app/test APKs). Continue the task list while checks run.

## iOS launch crash priority

The mandatory Compose plist setting is repaired and CI now includes a simulator launch smoke check. Shared JVM tests and Android APK build passed. Local Xcode 15.4 linking and simulator boot prevent runtime confirmation; see `docs/qa/ios-launch-crash-2026-10-04.md`. PRs #11/#12 were closed without merging at the user's request; the new PR contains only the crash fix and launch check.

## Remaining product work

- [x] Native visual review and redesign refinement against Stitch direction; exercised English/Arabic light/dark/150%-font screens passed, further product refinement continues.
- [x] Merge reading typography preferences; further display modes remain subject to canonical-content review.
- [x] Download management: storage totals/removal and aggregate cache policy (PR #8).
- [ ] Reader listening controls and background media notification/interruption acceptance (platform implementation landed; lock-screen, notification and interruption behavior still require physical-device acceptance).
- [ ] Direct memorized-ayah review journey: implemented; native persistence/navigation acceptance pending.
- [ ] Practice session restoration: current sessions start fresh without autoplay; interrupted range/repetition restoration remains unimplemented.
- [ ] Children learning flow refinement and accessibility checks with larger fonts.
- [ ] Qibla orientation/calibration verification on physical devices.
- [ ] iOS app runtime, native audio and permission verification; CI builds are not device acceptance.
- [ ] Audit remaining upstream parity, image Mushaf requirements and content/licensing attribution.
- [ ] Final end-to-end product QA and accurately documented release limits.

## Deferred

- [ ] AI learning integration and provider configuration (user explicitly deferred).

Do the next unblocked task while PR checks run. Build after a combined batch, not after each edit. Keep the lead plus one implementation engineer; use a temporary independent review at checkpoints.
