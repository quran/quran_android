# Quran mobile design system

This contract connects the Stitch visual direction to shared Compose components. It prioritizes resuming reading, clear Arabic text, a small ayah action menu, and focused memorization. `design-preview.html` is a self-contained component study, not a screenshot or evidence of native app behavior.

## Product rationale

A warm paper background and restrained forest-green actions support long reading sessions. White surfaces distinguish settings and practice controls; reading remains continuous rather than a stack of decorative cards. Gold is decorative and never the sole status cue or small text color on paper. Dark mode uses complete semantic colors rather than merely darkening the background.

The interaction references in [READER_PRODUCT_NOTES.md](READER_PRODUCT_NOTES.md) identify resume reading, saved ayat, translation settings, and repetition as established Quran-app priorities. Current official references include [Quran.com](https://quran.com), [Quran for Android](https://quran.com/apps), [Ayat](https://quran.ksu.edu.sa/), and [Muslim Pro](https://www.muslimpro.com/holy-quran-app/). These inform navigation and controls; their content, brand assets and artwork are not copied. Browser inspection was unavailable for this pass; official page text and the earlier product research were used.

## Tokens

`design-tokens.json` records the exact light/dark colors, typography, spacing and radii agreed with the component implementer. Its CSS equivalents are embedded in the preview. Shared Kotlin tokens remain the runtime authority; update both artifacts when they change.

| Role | Light | Dark |
| --- | --- | --- |
| Primary / foreground | #173E2F | #A9D5BC |
| On primary | #FFFFFF | #0D3522 |
| Background | #F6F2E9 | #0E1914 |
| Surface | #FFFFFF | #16261D |
| On surface | #173E2F | #E7EEE9 |
| Surface variant | #ECE8DF | #23372B |
| On surface variant | #46584D | #C3D1C6 |
| Secondary | #73561D | #E5C177 |
| Outline | #748276 | #718779 |

Spacing uses 0/4/8/12/16/24/32/48dp; radii use 4/8/12/20/28dp. The minimum action target is 48dp. Display is 40/48sp, headlines 30/40 and 26/36, titles 22/30 and 18/26, body 16/24 and 14/22, label 14/20, caption 12/18. Arabic reading is serif at 28/48sp, or 34/56sp for the larger mode. No generated font sample is treated as scripture; corpus-compatible licensed font and diacritic rendering need native validation.

## Components and screen structure

- ScreenWrapper supplies Scaffold, title, optional back action, action slot, bottom bar, snackbar and floating action slot. Content consumes provided padding and native safe insets.
- Primary, outlined and text buttons expose text, callback, modifier, enabled and loading state. Icon actions require a spoken description and the same minimum target.
- Text styles come from one typography contract; screen titles, subtitles, body, captions and Arabic source text have distinct purposes.
- Lists use predictable row spacing and dividers. Surah numbers and ayah references support scanning; saved/selected state also has a semantic announcement.
- The ayah action sheet presents mark-as-read, bookmark, memorize and study with a dismiss affordance. Native back dismisses transient UI before navigating.
- Settings group language, children mode, translation edition and local progress. Memorization keeps reveal/hide, repeat count and the learner's explicit confirmation close to the verse.

The HTML preview demonstrates these component states with a mock phone, language and theme switches, simple library/reader/practice/settings views and an action sheet. Its sample progress is local demonstration state only. It does not download text, audio, infer memorization, or connect AI.

## Accessibility and localization

Use logical start/end layout. UI language changes navigation direction; Quran source text always remains RTL and each translation retains its own direction. EN and AR labels are real shared string resources with matching format placeholders. Dynamic text belongs in resources, while canonical text and source metadata remain unchanged.

Provide visible focus outlines, native role and selected-state semantics, adequate text contrast, scalable type and 48dp targets. Pair color with text or icon status. Do not truncate ayat or clip Arabic diacritics. Respect reduced motion. Test narrow screens, font scaling, VoiceOver/TalkBack traversal, modal focus and real dark mode on each platform.

## Verification boundary

The source-only review checks palette consistency, typography hierarchy, spacing rhythm, component contracts, responsive intent, full dark tokens, restrained motion, accessibility intent, reading density and feedback states. Native screenshots, measured contrast, Arabic font rendering, keyboard/focus behavior and platform tests remain required before claiming these ten dimensions pass. This document assigns no unobserved visual score. A successful build alone does not prove visual quality.

The preview contains explicit placeholders and no Quran quotations or invented translations. Existing verified source content, attributions and deferred AI status are preserved.


## Form and selection components

`QuranChoiceChip` represents a single labelled selected choice with Material selected semantics and a 48dp minimum hit area. `QuranSettingSwitch` combines its title, description and switch into one labelled toggle action; the whole row is touchable and announces the checked state. Settings uses these for interface language and children mode.

`QuranTextField` shares labelled, single-line input, keyboard options, error and supporting-text behavior across search and manual Qibla coordinates. `QuranDialog` shares title and action slots for the translation picker. Each component lives in a separate file. Scalable text, wrapping selection rows, and standard platform focus semantics take precedence over fixed-height layouts.
