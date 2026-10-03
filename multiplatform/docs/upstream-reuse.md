# Upstream reuse and adaptation

Upstream: https://github.com/quran/quran_android. Keep its source and GNU GPL version 3 license intact in the repository. This KMP app is an adjacent migration slice, not a replacement of every upstream capability.

The existing code was inspected through its indexed code graph and source snippets:

| Upstream source | Observed behavior | Reuse boundary |
|---|---|---|
| `app/.../presenter/audio/service/AudioQueue.kt` | Verse/range repetitions, previous/next verse, bounds, basmallah flag, local audio path and extension lookup | Port deterministic queue decisions to common domain. Keep file resolution in data adapters. Existing repeat values mean additional repeats, whereas new `RepeatSession` uses total completed recitations. Adapt explicitly. |
| `app/.../service/AudioService.kt:makeOrResetExoPlayer` | Media3 ExoPlayer, audio-only renderer, back buffer, local/network data source, network wake mode, audio-focus attributes and media-session activation | Android now adapts this boundary with a service-owned Media3 ExoPlayer, MediaSession and MediaController, plus audio-focus and becoming-noisy handling. These Android classes cannot compile in common KMP or run on iOS. |
| `app/.../service/AudioService.kt:onPlayerCompleted` | Completion advances `AudioQueue`, handles gapless seek timings and basmallah before stopping | Preserve behavior and add regression coverage when migrating the full reciter/downloader stack. Local per-verse recording playback is a narrower first slice. |
| `app/.../ui/QuranActivity.kt:launchTranslationActivity` | Android Intent launches `TranslationManagerActivity` | Navigation 3 destinations replace Activity launches in shared UI. Existing Android-only translation management remains in the legacy app. |
| `common/data/.../QuranInfo.kt:getNumberOfAyahs` | Chapter count lookup and invalid chapter sentinel | Canonical validated `VerseId` and `QuranCanon` in `core:model` enforce the same chapter/verse boundaries with explicit errors. |

Media service migration is incremental. The shared `AudioPlayer` port receives a selected local media URI, invokes completion only after media playback ends, and offers play/pause/release. Android uses `QuranPlaybackService` (`MediaSessionService`) to own ExoPlayer and a session, with foreground-service permissions, MediaController access, audio-focus handling, headphone-disconnect pausing and task-removal cleanup. Media3 supplies notification/session controls; native playback and actual completion were exercised in Android emulator instrumentation. Real lock-screen and hardware behavior still require device verification. iOS uses AVAudioPlayer and a retained delegate, activating its playback audio session. Downloads, gapless reciter metadata and basmallah behavior remain migration work; service integration does not imply full legacy recitation parity.

The Qibla UI now has native live heading adapters with manual fallback: Android rotation-vector orientation corrected to true north through magnetic declination, and iOS Core Location true heading with optional when-in-use permission. Unreliable readings are suppressed and listeners stop when the screen leaves the foreground. Emulator compilation cannot establish physical compass accuracy.

Keep legacy data identifiers stable for migration. Quran source text requires corpus checksums, verse counts and provenance. New visual components can be reused across features without rewriting sacred text or altering recitation identifiers.
