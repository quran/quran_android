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

Download management is being integrated: native inventory/totals, explicit removal, completed-file capacity policy, shared storage/playback leases, a separate downloads feature with English/Arabic resources, and Navigation 3 settings entry. Common behavior tests and a native removal/relaunch journey are authored; combined build and runtime verification remain pending. Continue implementation while design CI runs.

## Remaining product work

- [x] Native visual review and redesign refinement against Stitch direction; exercised English/Arabic light/dark/150%-font screens passed, further product refinement continues.
- [x] Merge reading typography preferences; further display modes remain subject to canonical-content review.
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
