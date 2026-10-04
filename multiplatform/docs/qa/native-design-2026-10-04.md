# Native design refinement verification

This batch replaces Unicode navigation symbols with three reusable vectors, removes a duplicate library heading and makes chapter rows open the canonical first ayah. Flexible rows retain Arabic RTL and localized actions. The existing Quran corpus and metadata are unchanged.

The combined build passed in 10 seconds: 76 shared JVM tests, shared JVM UI compilation, Android debug app and instrumentation APK. All nine existing native emulator journeys passed in 172.375 seconds against this updated APK. Independent source review found no actionable findings.

The signed-in Stitch project generated Library/Home and continuous Reader refinements. Only interaction/layout direction informs native code; mock progress, source credentials, bearings, analysis and unsupported controls were not copied. Stitch screenshots are design evidence, not screenshots of the app.

The two native visual journeys exercise English/Arabic library, reader, practice and settings in light/dark themes, plus both languages at 150% system font size. They verify actual node clipping, line bounds/truncation, 48dp targets, mirrored navigation and selected native theme background; 24 successful screenshots were saved. CI and merge are pending for this batch. Simulator builds do not prove iOS device runtime, physical compass calibration or background interruption acceptance.

Source-token WCAG contrast calculation (foreground/background pairs, not screenshots): light body and primary action 11.89:1, supporting text 7.60:1; dark body 13.40:1, supporting text 9.97:1 and primary action 8.35:1. These pairs exceed 4.5:1. Other states, antialiasing and device rendering still require visual review.

The initial visual-test implementation treated the semantics `TextLayoutResult.hasVisualOverflow` as a rendering verdict. Diagnostics showed a complete 505.39px English line inside a 506px node, and a complete 194px Arabic line inside a 194px node while the reconstructed paragraph retained an 810px parent constraint. AndroidX's [ParagraphLayoutCache](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/modifiers/ParagraphLayoutCache.kt) exposes semantics by rebuilding a MultiParagraph with parent constraints while returning the actual layout size. The corrected test measures the same font/style/density/direction against the actual node box and check line edges, truncation, ellipsis and unclipped node bounds. No production text was changed to accommodate a test artifact.

The visual checks exposed a real undersized reader action. `VerseReaderItem` now uses the shared `QuranTextButton` with a 48dp minimum rather than a raw Material text button. Localized labels, per-verse tags and navigation callbacks are unchanged. The final combined Android app/test APK build passed in 5 seconds; all 11 native journeys passed against that APK in 308.321 seconds. Source review accepted the production fix, scoped selector and final measurement assertions.

Independent inspection of four large-font screenshots found no visible text or diacritic clipping. It noted tight Back/title spacing, which was refined with 8dp logical-start title padding only when a back action exists. The final combined 76-test JVM/shared UI/Android app+test APK build passed in 6 seconds. The two affected visual journeys were rerun after this small spacing change and passed in 155.958 seconds, producing the full 24-screen capture set again. The nine behavioral journeys were green before this spacing-only delta; CI runs all eleven again on the published head.

Source review found no remaining actionable findings. English/Arabic resources retain 146 matching keys. This evidence covers the exercised emulator screens and actions; it does not establish exhaustive TalkBack traversal, every chapter/translation/font, physical compass performance, iOS runtime or media interruption/lock-screen acceptance.


## CI acceptance and merge

The first CI run passed all eleven instrumentation tests but failed after them: emulator-runner ran separate script lines in independent shells, and AGP removed installed APKs and their app-owned screenshots. A single committed Bash script now retains status across commands, attempts capture even after test failure, preserves the original failing test code, and passes AGP's supported `android.injected.androidTest.leaveApksInstalledAfterRun=true` property only on the ephemeral CI emulator.

Independent review cleared this repair. Run `37180877098` passed KMP Android and tests, KMP Android runtime including screenshot collection, and KMP iOS framework/simulator application for head `5dc59656e68145de38c3a1ac679fff367189d523`. Reviews/threads/statuses and mergeability were refreshed before authored acceptance COMMENT `5404536785` and expected-SHA merge. PR #7 merged as `54c97a4891e65b2ef2b91a1d1fdbcf2bbf7f0555`. The comment is not a formal GitHub approval.
