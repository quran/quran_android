# Quran reader product direction

The reader is designed around established Quran-app behavior, not a generic dashboard. The goal is to let a person open the app and resume reading within one tap.

## Interaction model

1. The library opens on the saved reading position and exposes Surah search and saved ayat.
2. Reading uses a continuous verse list: Arabic comes first, followed by the optional selected translation.
3. One compact action opens the ayah menu. Mark-as-read, bookmark, memorize and study actions remain available without making every ayah row visually busy.
4. Memorization remains a dedicated flow: reveal/hide, a finite or learner-controlled repeat target, playback, and an explicit learner confirmation.
5. Qibla stays separate from Quran reading, with manual coordinates available when sensors or permission are unavailable.

## Reference patterns

- Quran for Android offers Madani pages, gapless audio, bookmarks, search, customizable repeats and translations/tafsir. This app keeps the same core priorities while the Madani page renderer and hosted reciter downloads remain future migration work.
- Ayat supports direct Surah/Ayah, Juz and page navigation, selectable Mushaf editions, repeated ayah audio and children memorization. The new app starts with direct Surah/Ayah and a dedicated children practice mode; Juz/page and certified Mushaf editions need their own canonical layout/content packages before they can be represented honestly.
- Muslim Pro makes recents, reading history, bookmarks/notes and verse quick actions easy to reach. The app uses the same resume/bookmark/ayah-action patterns with locally persisted progress.
- Greentech separates reading from deeper study tools and keeps saved passages and last-read positions easy to return to. The app follows that split: the verse menu enters practice or study rather than embedding a dense tool panel into the reader.

## Sources consulted on 2026-10-03

- [Quran for Android on Google Play](https://play.google.com/store/apps/details?id=com.quran.labs.androidquran)
- [Ayat on Google Play](https://play.google.com/store/apps/details?id=sa.edu.ksu.Ayat)
- [Muslim Pro Quran features](https://www.muslimpro.com/holy-quran-app/)
- [Greentech Al Quran on Google Play](https://play.google.com/store/apps/details?hl=en&id=com.greentech.quran)

Feature lists describe third-party products. They are product references only and do not establish rights to their content, code, recitations, translations, artwork, or trademarks.
