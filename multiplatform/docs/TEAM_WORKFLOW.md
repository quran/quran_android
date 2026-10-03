# Delivery workflow

Use a small team: a lead who integrates and verifies, plus one implementation agent for a bounded task. Bring in an independent reviewer at PR checkpoints; end that review assignment once its findings are delivered. Do not keep separate permanent architecture, QA, security, and design agents or duplicate investigations across agents.

Use ECC Android clean architecture, Compose Multiplatform, Kotlin testing, and design-system skills where relevant. Navigation 3 remains required. Domain contracts remain framework independent; platform adapters and networking belong in data. Keep models and reusable components in focused files.

Finish the combined edits before a build, as requested. Tests cover user-visible behavior, cancellation, persistence, source integrity, and platform lifecycle; no framework churn merely to mirror a skill example.

PR acceptance requires substantive independent review and passing Android/tests, iOS framework, and Android runtime checks for the current head. Never merge drafts or bypass review protections. AI integration remains deferred.
