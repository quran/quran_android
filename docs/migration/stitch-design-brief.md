# Quran mobile redesign brief

Create a coherent native mobile Quran reading app for Android and iOS, based on the reading features of quran/quran_android.

Design six connected screens:

1. Home/library: Continue Reading and Surah/Juz list, search and offline indicator.
2. Distraction-free Mushaf reader: page navigation, ayah selection and compact controls.
3. Translation and tafsir: verse context, provider selector and adjustable type size.
4. Recitation player: reciter selector, verse repeat/range and downloaded audio states.
5. Bookmarks and history: saved verses/pages, last reading position and empty states.
6. Settings/downloads: appearance, language, accessibility and content management.

Visual direction: warm ivory paper, forest green, restrained brass accents, generous spacing and excellent Arabic typography. Subtle geometric detail may appear outside sacred text. Use a consistent reusable component system suitable for Compose Multiplatform.

Bottom navigation: Read, Listen, Library, Settings. Provide English LTR and Arabic RTL variants plus light and dark reading modes. Support 48dp touch targets, accessible contrast, large text and screen readers. Show loading, empty, error, unavailable-content and download states.

Use explicitly labeled placeholders for Quran text and licensed Mushaf artwork. Do not fabricate Quranic text. Avoid ads, feeds and gamification. Prioritize reading and respectful presentation.

Export design references and visual tokens. Generated HTML will be translated into Compose components rather than used as the app runtime.
