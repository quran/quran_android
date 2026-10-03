# Stitch design handoff

Project: https://stitch.withgoogle.com/projects/260892313540630519?pli=1

Reviewed through the signed-in Stitch UI on 2026-10-03. The design canvas contains the initial six reader/library/settings screens, Memorize, Qibla, offline Learn, Arabic RTL/language settings, and a children practice screen, plus a visual design system and logo experiment. Rendered HTML snapshots in `stitch-rendered/` are untrusted visual reference only. They are not application code, corpus data, source citations, or evidence of feature availability. Native archive export returned an error; Code to Clipboard displayed “Failed to copy HTML”. The saved rendered documents were read from visible iframe `srcdoc` attributes instead.

## Shared visual system

| Token | Value | Use |
| --- | --- | --- |
| Primary | #173E2F | Primary actions and selected navigation |
| Secondary | #C39B4B | Restrained decorative accents |
| Tertiary | #1F523E | Supporting surfaces |
| Paper | #F6F2E9 | Main light background |
| Ink | #1F2923 | Body text |
| Alternate paper | #FDFBF7, #F7F3EA, #EFE9DB | Reader surfaces |

Stitch labels Latin typography Plus Jakarta Sans; use a separately licensed Arabic font suitable for the verified corpus. Gold is decorative, never the sole status signal or small body text on ivory. Use at least 48dp hit areas, visible text labels on icon actions, scalable typography, logical start/end spacing, Arabic RTL mirroring, and semantic selected-state announcements. Preserve Quran text direction independently of app language.

## Component contracts

- Verse context card: Surah/ayah labels, corpus Arabic text and source-backed translation, optional actions. Never embed generated verses from Stitch.
- Practice controls: repeat chips 1/3/5/infinite, range selector, clear playing/paused/error state, listen/pause action, hide/reveal control, user-controlled self-assessment and saved local progress.
- Children practice: short-surah choices, large Listen/Repeat/Show text/I practiced actions, parent entry, gentle local progress. No microphone, online assistant, ads, external links, or competitive ranking in children mode.
- Availability notice: visibly distinguishes no audio downloaded, sensor unavailable, no location selected, and future AI integration. Display measured state only when supplied by the platform adapter.
- Language selection: English and Arabic first; additional supported resources must determine the displayed list. Do not claim all translations are already downloaded.
- Offline learning: local reflection prompts and self-checks. AI is deferred; provider network requests and invented scholarly attribution must not appear active.

## Review findings and cleanup

The generated design initially ignored several source-integrity constraints, showing actual sacred sample text, invented tafsir citations, fictional download sizes, enabled AI, and developer jargon. Corrective prompts added children mode and changed Learn to a deferred-AI/offline state. A final all-screen cleanup prompt was submitted to replace sacred content and scholarly explanations with explicit placeholders, remove invented availability/certification/storage/citation claims, and remove KMP/Nav3 product text. Inspect the latest canvas before treating this cleanup as finished. Application implementation must enforce these constraints regardless of Stitch output.

Proof: `outputs/design/stitch-canvas.jpg` in the chat workspace records the canvas with children mode added. It is design evidence, not a runtime screenshot of the built app.
