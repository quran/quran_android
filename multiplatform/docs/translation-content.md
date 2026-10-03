# Translation content

The app reads translation editions from the QuranEnc API. QuranEnc's republishing terms allow the app to display its translation content when it keeps the source text unchanged, credits QuranEnc and the publisher/translator, shows the source version, retains the source transcript information, reports translation notes to QuranEnc, keeps editions current, and avoids inappropriate advertising.

The app treats translations as human translations of the meanings. It keeps them separate from the canonical Arabic text, validates verse numbering before caching, displays the edition title, translator, version and source, and refreshes the catalog and surah content when online. Previously validated responses remain readable offline. Translation caches are keyed by edition version so a newly published version does not reuse old verse text.

The first release does not submit user notes to QuranEnc. API requests contain only the selected public edition and surah number; the app does not send account details, location, bookmarks or reading progress.

Sources:

- [QuranEnc API documentation and republication terms](https://quranenc.com/en/home/api)
- [QuranEnc translation catalog](https://quranenc.com/api/v1/translations/list/en?localization=en)
- [Tanzil Quran text license](https://tanzil.net/docs/Text_License)
