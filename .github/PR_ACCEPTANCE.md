# PR review and acceptance

The user authorized an acceptance agent to review each PR and merge after checks pass. A Codex heartbeat, **Quran PR reviewer and acceptance agent**, checks this fork every 30 minutes. It uses the connected GitHub identity and performs substantive code review; it is separate from GitHub Actions build jobs.

Scope: `salehnahya/quran_android`, PRs targeting `main`. No automated writes to upstream `quran/quran_android`.

Review drafts, but merge only after they are ready for review. For each changed PR head:

1. Review the full diff and relevant surrounding code. Check domain behavior, Navigation 3, KMP boundaries, persistence, source integrity, RTL/accessibility, child mode and playback lifecycle.
2. Require `KMP Android and tests` and `KMP iOS framework` and `KMP Android runtime` from the Quran KMP workflow, plus every required repository check. The iOS job builds both the shared framework and the simulator host app.
3. Resolve all actionable review findings before acceptance. Green CI alone is insufficient.
4. Re-fetch head/base/checks immediately before accepting. A new head invalidates the previous review.
5. Submit APPROVE or REQUEST_CHANGES. GitHub prevents authors approving their own PRs: if the connected identity is the author, record the agent's acceptance decision with a COMMENT instead. This is not a formal GitHub approval and cannot satisfy required-review protections.
6. Merge only with current passing checks, no unresolved change requests, no conflicts and the expected head SHA. Never bypass protections or change permissions to make a merge succeed.

The heartbeat runs while the Codex app can execute local automations and GitHub authorization remains available. It is not an always-on hosted service. If execution or authentication is unavailable, reviews wait until those conditions return.

Fork-specific CI has read-only repository permissions and runs untrusted PR code without credentials. Upstream workflows are gated to the upstream repository because their artifact history and comment destinations belong there.
