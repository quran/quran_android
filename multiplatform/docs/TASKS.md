# Quran app delivery tasks

This list tracks the requested app, beyond individual pull requests. AI integration is deferred by the user. A PR merge finishes its slice; it does not finish the app.

## Product goal

Build a production-quality Quran app in `/Users/saleh/AndroidStudioProjects/Quran` by evolving the fork of `quran/quran_android` into a modular Kotlin Multiplatform app for Android and iOS. The original native Quran Android app is the product and UX authority; Stitch is reference material only and does not define the final visual direction.

The app must preserve canonical Quran content and provide the native reading, Mushaf, search, bookmarks, history, downloads, reciters, translations, tafsir entry points, Qibla, memorization, children learning and persisted-progress experiences across English and Arabic with RTL and accessibility support. Navigation 3, clean architecture, separate model/component files, real localized resources, native media lifecycle and platform boundaries are required. AI learning remains deferred.

Every change uses TDD where behavior is involved, passes focused tests before the combined build, moves through the Todo → In Progress → Review → Done board workflow, receives independent review and merges only after current-head required checks pass. Implementation work stays with a small Luna worker team under Sol 6.1 technical leadership and review. Hardware-dependent behavior is tracked with explicit device evidence and release limits.

## Completion plan

Each phase is complete only when implementation, focused behavior tests, current-head CI evidence and independent review are recorded. Device-only items remain open until physical-hardware validation.

### Work-board workflow

Track every feature, bug fix, platform task, design-system change and QA item through these columns: **Todo → In Progress → Review → Done**. A task enters Review only after implementation, focused tests and a PR exist; it enters Done only after current-head CI, independent review and required acceptance pass and the PR is merged. GitHub Issues are disabled for this fork, so the canonical board is this checklist plus the attached PR artifacts until a project-board connector is enabled.

Every ticket must include:

- **Todo:** user outcome, scope, dependencies and acceptance criteria.
- **In Progress:** owner, implementation branch and files in scope.
- **Review:** PR link, test/build evidence, screenshots or device evidence where required, and independent review.
- **Done:** merged commit, exact CI checks, known limitations and follow-up tickets.

Current board:

- **Done:** children-mode reading accessibility and practice restoration wiring — PR #15, merged as `b13d64cdbd0e6d78bdc9dc959098123ba7ef6ef5`.
- **In Progress:** native library flow — search normalization, clear action, accessible tabs and chapter browsing (Antigravity/Luna worker).
- **Todo:** Mushaf reader parity, children-flow refinement, physical-device validation, upstream/content audit and final QA.

### Phase 1 — Native Quran reading experience

- [ ] Match the original native library flow: search, filters, bookmarks, history, downloads, reciters and reading resume.
- [ ] Match the original reader/Mushaf behavior: page and ayah navigation, translations, tafsir entry points, audio controls, font/display settings and RTL behavior.
- [ ] Keep Navigation 3 routes serializable and restore the exact reader destination after process recreation.
- [ ] Verify canonical Quran text, image Mushaf requirements, attribution and licensing before adding content.

### Phase 2 — Study, memorization and children

- [ ] Persist active practice ranges, current ayah, repetition count and autoplay intent; restore after relaunch without starting audio unexpectedly.
- [ ] Complete the memorized-ayah review journey with native persistence and direct-navigation acceptance.
- [ ] Refine children mode with guided practice, larger fonts, clear semantics, touch targets and reduced cognitive load.
- [ ] Add English/Arabic behavior coverage for progress, review and children-mode accessibility.

### Phase 3 — Native platform behavior

- [ ] Validate Android lock-screen notification, audio focus, headset disconnect, task removal and notification actions on physical hardware.
- [ ] Validate iOS launch, background audio, interruptions, native audio permissions and document picker on a physical device.
- [ ] Validate Qibla calibration, sensor orientation, permission denial and unreliable sensor states on physical hardware.
- [ ] Record device model, OS version, result and evidence in `docs/qa/`.

### Phase 4 — Quality and release readiness

- [ ] Run the full English/Arabic RTL, dark/light, large-font and accessibility journey matrix.
- [ ] Audit upstream parity, content integrity, image Mushaf assets, offline behavior and licensing attribution.
- [ ] Run final Android and iOS builds plus required KMP checks from the exact reviewed commit.
- [ ] Perform an independent security and architecture review with no unresolved findings.
- [ ] Publish release limits and known external requirements; AI remains deferred until separately specified.

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
- [x] Practice session restoration: durable snapshot/store, paused domain restore, Navigation 3 wiring and one-time checkpoint consumption are implemented and covered by common tests; physical-device resume evidence remains tracked separately.
- [ ] Children learning flow refinement and accessibility checks with larger fonts.
- [ ] Qibla orientation/calibration verification on physical devices.
- [ ] iOS app runtime, native audio and permission verification; CI builds are not device acceptance.
- [ ] Audit remaining upstream parity, image Mushaf requirements and content/licensing attribution.
- [ ] Final end-to-end product QA and accurately documented release limits.

## Deferred

- [ ] AI learning integration and provider configuration (user explicitly deferred).

Do the next unblocked task while PR checks run. Build after a combined batch, not after each edit. Keep the lead plus one implementation engineer; use a temporary independent review at checkpoints.
