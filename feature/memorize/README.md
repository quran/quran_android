# Memorize (احفظ) and the prayer corrector (مصحح الصلاة)

Recite from memory with the mushaf page open: the words of the passage are hidden and appear as
they are recited correctly. A wrong word is marked (not shown); if the reciter is stuck, the next
word is given, one word at a time, in a reciter's voice. The same corrector works hands-free during
salah, with the screen off. A session goes on into the next suras (as in taraweeh) until the user
ends it.

A floating mic on the reading screen starts it (green; long press for salah), shows when it listens
(blue) and pauses, resumes or ends it. The words are hidden as soon as it starts, and the user may
begin anywhere from three pages before the page on screen to three after it: once the start is
found the words before it are shown again. Reciting something that isn't there marks the first word
on screen (and, while memorizing, gives it after the delay).

**Everything runs on the device.** Audio is processed in memory and dropped; nothing is logged or
sent, and nothing is recorded unless the user turns on "Record my recitation" (off by default).
Recordings stay in the app's no-backup storage and only leave the phone if the user exports them.
The only network use is the one-time download of the speech model and, if the user agrees (or turns
on automatic downloads), the hint reciter's sura audio through the app's normal audio downloads.

## How it works

```
microphone (16 kHz, VOICE_RECOGNITION) ─► speech engine (sherpa-onnx, streaming zipformer2-CTC,
phoneme output) ─► RecitationTracker (aligns the phonemes with the expected words) ─► HintScheduler
(when to give a word) ─► MemorizeState ─► page masks + spoken hints (PromptPlayer)
```

* `:feature:memorize:tracker` — pure Kotlin, no Android: `PhonemeNormalizer`, `RecitationTracker`
  (weighted semi-global alignment; repetition, re-sync after misheard words, recovery when the user
  skips ahead, phrases that aren't part of the passage such as isti'adha/basmala/takbir),
  `SalahFlow` (Al-Fatiha and prayer phrases are ignored; hints only while the passage is recited;
  the passage continues in the next rak'ah), `HintScheduler` (mark → wait → one word → wait → ...).
  The tracker is a port of the reference implementation used to benchmark the models; the golden
  tests in `src/test/resources/golden` keep the two identical.
* `:feature:memorize:engine-sherpa` — the only module with native code (sherpa-onnx AAR, fetched by
  `tools/memorize/fetch_sherpa_onnx.sh`, SHA-256 pinned).
* `:feature:memorize` — plugs into the app's recitation slot (`common:recitation`) instead of
  `:feature:recitation`'s stubs: session controller, microphone foreground service, page masks,
  hint audio, settings, model download/import.

When a word is given (`HintScheduler`, delay T from the settings, 4 s by default):

| | on screen | in salah (spoken hints) |
|---|---|---|
| wrong word | marked at once; a word T later | a word T later, once the reciter pauses (not over them) |
| stuck mid-ayah | after T without progress and 2 s of silence | same |
| stuck between ayat (before an ayah, or on its last word) | after T | after 2 × T (a pause there is often ruku; the takbir disarms hints) |
| the same word wrong again | at the reciter's next pause, without waiting for T | same |
| still stuck after 3 single words | the rest of the ayah if no longer than the line, else the rest of the line | same |
| no answer after 10 hints | stops until the reciter recites a word | same |

Pages: every page type is the same text in its own font, so which glyphs are words is worked out
for the page type in use from its own glyph boxes (`GlyphTypes`: the text gives the words and the
hizb/sajdah markers, the font's shortest glyphs are its waqf marks — this reproduces the madani
pages' types exactly for all 6,236 ayat at every width). Marks are backgrounds under the words
(red: mistake, teal: hinted, amber: skipped) so colored (tajweed) mushafs keep their colors.

Recordings (opt-in): a recording per sura or per session, named by where it starts and ends, played
back from the audio bar or the floating mic (the page follows), and exported from Settings → My
recordings as a .zip: the audio (16 kHz AAC), `alignment.json` (every word with its time from the
recognizer, whether it was recited, hinted or missed, and its phonemes) and a NeMo `manifest.jsonl`
of the ayat recited entirely (see `RecordingFormat`).

In salah any phrase of ruku/sujud (takbir, tasmi', tahmid, tasbeeh, tasleem) ends the passage of the
rak'ah; hints resume when the passage continues after the next Al-Fatiha. A pause is 0.8 s of
silence or 1.5 s without anything new recognized (loud mosques are never silent). The speech
stream is restarted only in real silence: never in a held madd.

The pages are images, so words are hidden by clipping their glyphs out (`HighlightType.Mode.HIDE`).
The madani ayahinfo databases have no glyph types; `MadaniGlyphTypeResolver` supplies them from
`assets/memorize/glyph_types_madani_v1.txt` and the app derives word-level glyphs
(`AyahCoordinates.wordGlyphCoordinates`) that are only used for word keys and hiding, so existing
highlights are unchanged.

## Bundled data (regenerate with `tools/memorize/`)

| asset | script | notes |
|---|---|---|
| `memorize/glyph_types_madani_v1.txt` | `build_glyph_types.py` | glyph type per position for all 6,236 ayat; identical for every ayahinfo width |
| `memorize/lexicon_v1.db` | `build_lexicon.py --compact` | pronunciation of all 77,433 words (in context and alone) + prayer phrases, from [quran-transcript](https://github.com/obadx/quran-transcript) (MIT) |
| `memorize/timings/husary_murattal_wbw_v1.db` | `align_timing_pack.py` | word timings of the app's Husary sura files, by forced alignment |
| `memorize/timings/husary_mujawwad_wbw_v1.db` | `align_timing_pack.py` + `merge_timing_packs.py` | same for Husary Mujawwad |
| `memorize/timings/husary_muallim_wbw_v1.db` | `align_timing_pack.py` + `merge_timing_packs.py --truncated 4:33` | same for Husary Muallim; the upstream 004.mp3 stops in 4:34, so hints after 4:33 are shown without audio |

Word numbering everywhere is the page's glyph word numbering (`WordGlyph.wordPosition`).

## Speech model

One model: Quran-Lab's streaming Zipformer2-CTC phoneme recognizer **zipformer_p-arabic-v3.1**, int8
(69 MB) — <https://huggingface.co/Quran-Lab/zipformer_p-arabic-v3>
(`zipformer_p_arabic_v3.1.int8.onnx` and `tokens.txt` at commit `3da514fc`; the repository is
gated). The app downloads the byte-identical files once from an ungated mirror,
<https://huggingface.co/Saboorhsn/quran-stt-onnx> (`quran-stt-int8.onnx`, pinned to commit
`51ec9bcd` and checked by SHA-256 in `SpeechModelSpec`), or imports them for a setup without
internet (Settings → Recitation model → Import files: pick `quran-stt-int8.onnx` and `tokens.txt`
one at a time, or the .zip they came in; files are recognized by their SHA-256, whatever their
name). Settings → Download for offline use gets the model and the hint voice for the whole Quran at
once.

License: Quran-Lab No-Profit License 1.2, included verbatim in [MODEL_LICENSE](MODEL_LICENSE)
(Copyright (c) 2026 Muno459 / Quran-Lab). It allows free use, adaptation and redistribution, forbids
charging for the model or for any feature it powers, and its access terms ask applications to state
that automatic feedback can be wrong and does not replace a qualified teacher (shown before the
first session). The engine interface is model-agnostic; another model can be swapped in.

### Optional: the double-check

The setting is Settings → Double-check before correcting. It is off by default and needs a 92 MB
download. Before a hint is spoken, a second, larger model listens again to the last 8 seconds the
user said. It does the same a second after a mistake, before the mark is shown. If the word was said
after all, nothing is corrected. The word must be in that model's transcript, within a quarter of
its letters, right after the word before it. A dropped hint counts the word as recited.

* **What it changes.** In the lab (`REPORT.md` §15):
  * false hints in mosque prayers: −28 %, and −43 % while the passage is being recited;
  * clips from users' phones (tlog) with false mistake marks: −57 %;
  * reveal recall is unchanged;
  * in 400 real stalls, the hint was never wrongly dropped.
* **The model.** muhdur's Tilawi FastConformer, <https://huggingface.co/muhdur/tilawi-fastconformer-quran>
  (CC-BY-4.0), pinned to commit `e9448a0e` and checked by SHA-256 (`DoubleCheckModel.SPEC`).
  * The app downloads `fastconformer_full_mixed.onnx`, `vocab.json` and `quran.json`.
  * It runs on the sherpa-onnx already in the app, so the APK grows by nothing.
  * It runs in a process of its own (`DoubleCheckService`). With sherpa-onnx 1.13.8, a second model
    in the speech engine's process corrupts the engine's state. On a Mi 11 the session went deaf
    for 20–45 s after a check.
  * `CheckGraph` adapts the graph once, on the phone: it adds three nodes for the input sherpa
    gives, and the weights are untouched. The result is byte-identical to the lab's conversion with
    onnx (`bench/tilawi_rename.py`) and is checked by its SHA-256.
* **The Quran text.** `quran.json` holds the Tanzil Quran text (CC BY 3.0, verbatim). Its
  plain-letter form is only used to compare with what the model writes, and is never shown. The
  comparison rules (`CheckText`) reproduce the lab's tables for all 1,025 pieces and 77,433 words.
* **Which phones.** The model needs about 300 MB more memory while the user recites.
  * The setting recommends itself on phones with about 6 GB of memory or more and a 64-bit
    processor.
  * Once it has run, the time a check takes on the phone decides: it is not recommended above
    1.5 s, because hints would come late.
  * Where it is recommended, it is also offered with the recitation model's first download and in
    Download for offline use.

## Tests

* `./gradlew :feature:memorize:tracker:test` — tracker golden parity, salah flow, hint scheduler.
* `./gradlew :feature:memorize:testReleaseUnitTest` — page masks, word timings, and the session
  controller with a scripted engine (hints, salah disarming, start/stop races, start failures).
* `./gradlew :app:testMadaniDebugUnitTest --tests '*WordGlyphs*'` — derived word glyphs.
* `tools/memorize/run_device_benchmark.sh testlab|adb [engine|session] [MODEL,VERSION ...]` — on real
  phones (Firebase Test Lab or USB): `engine` = speed and memory of the recognizer at 1/2/4 threads,
  `session` = whole memorization sessions fed in real time with 26 clips — Husary and 8 other
  professional reciters and styles (QUD, CC-BY-4.0), non-professional readers incl. real wording
  mistakes (sobolev210, MIT) and non-native readers of 8 countries (RetaSy) — measuring per clip and
  per group how much is revealed, mistakes, hints and how soon after each word is said it appears.
  The model and the audio are staged into the test APKs by `tools/memorize/stage_test_clips.py`
  (`build/memorize-testlab`, never committed).

  Results (Test Lab, 2026-10-22):

  | phone | engine, 2 threads | model load | session: revealed / hints / mistakes | reveal latency p50 / p90 / max |
  |---|---|---|---|---|
  | Galaxy A10 (2019 low end, Android 10) | 0.42 × real time | 7.3 s | 60 / 0 / 0 of 60 words | 0.34 / 0.63 / 2.6 s |
  | Galaxy S21 (Snapdragon 888, as the Mi 11) | 0.07 × real time | 2.1 s | 60 / 0 / 0 | 0.16 / 0.46 / 2.5 s |
  | virtual Arm phone, Android 14 and 16 KB-page Android 16 | 0.05–0.07 | 1.1–2.0 s | 60 / 0 / 0 | 0.13–0.15 / 0.44–0.46 / 2.5 s |

  Native memory ~105–150 MB. The microphone starts before the model has loaded, so nothing said
  during those seconds is lost: on the A10 listening starts after 0.2 s and the words said while the
  model loads appear once it's ready (latency p50 / p90 / max 0.43 / 2.65 / 6.3 s, still 60/60).
  A Robo crawl of the app found no crash.

### Diagnostics

When a session doesn't follow, it can say why. `adb shell setprop log.tag.MemorizeTrace DEBUG`
turns on its trace from the next session start, and `adb logcat -s MemorizeTrace` shows it. The
trace includes:

* what the recognizer heard, and when the user is speaking or silent;
* every start search: the confident words and the best run found;
* salah phases (Al-Fatiha, passage, between rak'ahs);
* recognized words, mistakes and phrases;
* the hint scheduler's decisions;
* a status line every 10 s.

It is off by default and logs nothing otherwise. A session trace contains what was recited, as
text, so it is only for development.

## Known limitations

* Hafs, madani pages only. Recognition degrades on heavily reverberant/noisy audio. On real prayer
  recordings from mosques (evaluation T6, 3 hours) passages are followed with 97.6 % recall; false
  spoken hints per 10 minutes: 0 (Masjid an-Nabawi, qiyam), 1–2 (Masjid al-Haram, UK mosques),
  3 (Indonesian/Pakistani imams), 5 (local mosques with loudspeakers). Where loudspeakers garble the
  ruku/sujud phrases (Al-Aqsa) hints can come between rak'ahs; at most 10 in a row.
* Salah mode follows the passage the user picked; reciting a different sura is treated as mistakes.
* Hints need the hint reciter's sura audio to be downloaded; otherwise they are only shown.
