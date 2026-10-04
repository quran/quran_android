# Memorized-ayah review checkpoint — 2026-10-04

Settings already displays explicitly memorized ayahs. Previously Review verse opened the reader, requiring another action to enter practice. Review now navigates directly through Navigation 3 to practice for the selected ayah. Open verse remains a separate reading action; Needs more practice is the only removal action. The row is a separate shared-control component and reuses existing English/Arabic XML resources.

Review starts a fresh paused session, preserves saved progress, and requires explicit learner confirmation. Reconfirming an already memorized ayah remains an idempotent set addition. Interrupted range/repetition session restoration is not implemented in this slice.

A native journey was authored before host wiring/component integration: explicitly memorize, recreate the app, review directly without an intermediate reader, reconfirm and retain one saved ayah, read separately, remove explicitly, then relaunch and verify removal. No observed RED execution is claimed. Fresh CI native execution remains pending. Combined verification passed after all edits: 109 JVM tests with zero failures/errors, shared UI compilation and Android app/test APK assembly (8 seconds). Independent Sol 6.1 source review cleared the local slice; native runtime acceptance remains pending.

The existing StoredProgressRepository was reformatted with explicit imports and a private storage key constant. The progress.v1 serialization order, parsing and default behavior are unchanged; existing recreation/corruption/bookmark tests remain the compatibility evidence.
