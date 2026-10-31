# Quran (React Native)

A personal, offline Quran reader built with Expo / React Native. It replaces the
old Android (Kotlin) UI in this repo with a simpler, easier-to-read design.

## What it does

- **Last read marker** – tap any verse → *Mark as last read*, or use the gold
  button at the bottom of the reader to mark the verse you're on. The big card on
  the home screen always takes you back there. The app also quietly remembers
  where you last scrolled ("Last opened"), separate from your own marker.
- **Tags** – tap a verse → *Save to a tag*. Pick one or more tags (create new
  ones right there), add an optional note. Browse them under the **Saved** tab;
  long-press a tag to rename / recolour / delete it.
- **Surahs / Juz** lists with Madani page numbers, filter, and search
  (surah names, `2:255`, `page 50`, or any word in the translation).
- Arabic (Uthmani, Amiri Quran font) with English or Urdu translation and
  optional transliteration. Adjustable text sizes. Light, sepia and dark themes.
- Fully offline. Data is stored on the phone; use **Settings → Export backup**
  before reinstalling, and **Restore from backup** to bring it back.

## Run it on your phone

```bash
cd quran-app
npm install
npx expo start
```

Scan the QR code with the **Expo Go** app (Android / iOS).

## Build an installable APK

```bash
npx eas-cli@latest build -p android --profile preview
```

(needs a free Expo account; it builds in the cloud and gives you an APK link). Or,
with Android Studio installed locally: `npx expo run:android --variant release`.

## Project layout

- `src/app/` – screens (Expo Router): home, reader `read/[sura]`, tag `tag/[id]`, search, settings
- `src/components/` – shared UI, verse actions, tag picker/editor
- `src/state/store.ts` – saved data (last read, tags, saved verses, settings) persisted with AsyncStorage
- `src/data/` – bundled Quran text + helpers. Regenerate with `npm run build-data`
  (reads the `quran-json` package and the Madani page tables in `../pages/data/madani`).

Text: The Noble Qur'an Encyclopedia (quranenc.com) via `quran-json`, CC BY-SA 4.0.
Transliteration: tanzil.net.
