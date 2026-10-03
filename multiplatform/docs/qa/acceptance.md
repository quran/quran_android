# Independent acceptance review

Reviewed on 2026-10-03 by the acceptance reviewer agent, independently of the implementation and QA agents. Review covered current KMP source, actual JUnit artifacts, platform manifests, native adapters and fork CI. No competing Gradle build was run by this reviewer.

GitHub PR: https://github.com/salehnahya/quran_android/pull/1. The authoritative head fetched during review was `025bea917e64426afcfe5feb5b405724bf397c35`; it was open, draft and unmerged. Remote playback-controller, Android service and iOS compass files were fetched at that exact SHA. Local files and local test evidence contain later changes, so this review is **not unconditional approval of that earlier snapshot**.

## Findings and resolution

The earlier playback findings are resolved in the reviewed implementation. `RepeatPlaybackController` invalidates queued callbacks using a generation per play attempt, pauses before memorization/reset/disposal, prevents manual counting while playback runs and rejects activity after disposal. Screen configuration changes pause the old controller; import callbacks have a composition lifetime guard. Memorization assessment remains enabled after fixed-count completion until the learner explicitly marks the verse. Four passing controller regression cases cover cancellation, pause/resume, reset/disposal, manual double-counting and fixed completion.

Tanzil attribution includes an actionable source link in the updated composition root. An independent comparison parsed all generated chapter literals against the bundled source by chapter/verse identity: exactly 6,236 entries in both, with exact Unicode text equality. Canonical model validation rejects invalid chapter/ayah values. Persistence tests verify language, child mode, bookmarks and explicit memorization survive repository reconstruction and reject corrupt/future storage.

One snapshot discrepancy requires resolution before acceptance: the local Xcode project excludes unsupported simulator `x86_64`, while the fetched PR head's project does not. The CI host command uses a generic simulator destination. Sync the reviewed architecture fix, source and latest verification evidence, then require the new head's host job to pass. New commits invalidate approval of an earlier SHA.

## Correctness and architecture evidence

Typed serializable Navigation 3 keys and the polymorphic serializer registry are present in common Compose code. Actual Android UI tests exercise library → reader → practice → back, saved progress, Arabic/LTR mirroring and Activity recreation. The latest actual emulator XML reports four tests, zero failures/errors/skips, including children mode persistence and a three-repetition starting preference. This is stronger evidence than the older three-test description.

Domain logic has no Android, Compose or native imports. Platform adapters implement common contracts; the app composition root assembles repository/platform implementations and feature screens. Shared components are used across reading, practice, preferences and Qibla. This establishes useful module boundaries, not complete migration of every legacy capability.

Android Media3 playback is owned by a non-exported `MediaSessionService`; the adapter connects through a controller, registers real completion/error listeners and releases its connection. Service teardown releases player/session, and task removal pauses/stops it. A real native test using a synthetic silent PCM WAV completed exactly two playbacks; this fixture proves player behavior, not Quran recitation accuracy or background notification/interruption acceptance.

Qibla validates finite coordinates, computes true-north great-circle bearing and explicitly handles undefined direction at the Kaaba/antipode. Live Android heading adds geomagnetic declination and remaps display axes; iOS rejects invalid/unreliable true headings and requests location only after opting into live compass. Screen lifecycle stops heading updates on background/disposal; coordinate edits clear old direction and stop listeners. The UI explicitly asks for flat portrait operation. Physical calibration, orientation behavior and iOS permission/device accuracy remain unverified.

## Security and CI

The Android manifest limits exported components to the launcher; the media service is non-exported and declares media-playback foreground permissions/type. Backup is disabled and updated local extraction rules exclude study data. Playback imports accept local URI schemes; arbitrary remote schemes are rejected. No AI provider credentials, remote assistant, telemetry or account service is implemented. AI remains deferred by explicit user decision. Child mode is accurately described as a local learning preference; parental authentication was not requested and is not an acceptance prerequisite.

The fork KMP workflow uses `contents: read`, checkout without persisted credentials, PR-scoped jobs and no configured provider secrets. Legacy artifact/comment workflows are gated to the upstream repository, preventing fork runs from relying on upstream artifact history or commenting there. Android CI runs model/domain/data/controller tests, builds APK and runs lint. The iOS job compiles the framework and builds the actual Swift simulator host. Acceptance policy requires substantive review, current head checks and expected-SHA merge; authors cannot formally approve their own GitHub PR, so an independent agent decision recorded as a comment must not be represented as a formal approval.

## Acceptance decision

No unresolved high-priority correctness/security finding was identified in the current local implementation after the playback fixes. Acceptance remains **pending**, not approved for merge: synchronize the reviewed local changes, refresh the PR description/evidence, and verify Android and complete iOS host CI on the exact resulting PR head. The local Swift host failed at link with the installed Xcode 15.4 SDK; successful shared-framework linking does not establish iOS app build/runtime success. Require a supported-toolchain host gate to resolve this limitation.

This review supports the delivered migration slice only. Licensed hosted reciter catalogs/downloads, translations, image-based legacy Mushaf parity and iOS physical/runtime acceptance are accurately documented limitations. AI integration is intentionally deferred. The lead must separately audit the full user objective before declaring the overall goal complete.
