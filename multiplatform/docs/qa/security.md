# Security and privacy review

The reviewed migration slice uses bundled Quran text and local study preferences. AI provider integration is deferred by user decision. No provider key belongs in the mobile binaries; `TutorGateway` is an integration boundary rather than an active remote assistant. Future AI responses require approved source retrieval and citations before being presented as Quran teaching.

Imported recordings must use local `content://` or `file://` URIs on Android and local file URLs on iOS. The player boundary rejects arbitrary network schemes. File selection should use platform document pickers and restrict playback to a user-selected audio file. UI labels must not imply that an arbitrary imported recording has been validated as the selected verse's recitation. Progress is the learner's explicit self-assessment.

Study progress serialization validates canonical chapter/verse identities and falls back safely when malformed settings are encountered. Persistence is local, and Android application backup is disabled. No telemetry, account service, remote AI call or public sharing is implemented in this slice. Screen-level child mode is a learning preference; it is not authenticated parental control or an account safety system. A future parental lock needs its own acceptance criteria and review.

Android's application manifest enables RTL and exposes only the launcher Activity initially. Any added media service must be reviewed in the merged manifest for export status, foreground-service permissions and notification behavior. Location and compass adapters must stop listeners when screens leave composition, handle denial/unavailability, and avoid sending coordinates to a server. Runtime physical compass accuracy requires device calibration and verification.

The acceptance agent must inspect the exact current PR head, obtain an independent review, require passing build/test/security gates, and recheck the head before approval/merge. A passing status alone is insufficient if the review targeted an older commit. New code must not alter the Quran Arabic corpus without explicit source comparison and review. GPL source and original notices must remain available with redistributed derivatives.

This document records scope and review criteria. Platform behavior is verified only where `verification.md` records an executed check; architectural intent does not establish a passing security or device test.
