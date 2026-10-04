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
- [x] Current-head CI review/acceptance policy for Android tests/runtime and iOS framework/app.

## Active batch

- [x] Independent Arabic/translation sizes, additive persisted settings, shared typography rendering.
- [x] Focused settings controls, source review, 76 JVM tests and all 9 local emulator journeys passed.
- [ ] Current-head CI and merge of the reading-preferences PR. Implementation is complete; do not duplicate it while checks run.

## Next unblocked batch

Native visual review and design refinement: inspect the actual reader, library, settings and practice in English/Arabic and light/dark, refine hierarchy/navigation icons/reading density against the existing Stitch design direction, and verify accessibility with larger text. CI waiting does not block this work.

## Remaining product work

- [ ] Native visual review and redesign refinement against Stitch direction; verify real screen behavior in English/Arabic and light/dark.
- [ ] Merge reading typography preferences; assess further readable display modes using canonical content.
- [ ] Download management: storage totals/removal and aggregate cache policy.
- [ ] Reader listening controls and background media notification/interruption acceptance.
- [ ] Memorization review journey with explicit per-ayah progress; assess session restoration needs.
- [ ] Children learning flow refinement and accessibility checks with larger fonts.
- [ ] Qibla orientation/calibration verification on physical devices.
- [ ] iOS app runtime, native audio and permission verification; CI builds are not device acceptance.
- [ ] Audit remaining upstream parity, image Mushaf requirements and content/licensing attribution.
- [ ] Final end-to-end product QA and accurately documented release limits.

## Deferred

- [ ] AI learning integration and provider configuration (user explicitly deferred).

Do the next unblocked task while PR checks run. Build after a combined batch, not after each edit. Keep the lead plus one implementation engineer; use a temporary independent review at checkpoints.
