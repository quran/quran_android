package com.quran.mobile.voicesearch

/**
 * Provider interface for loading Arabic Quran verse text.
 * Implemented in the app module where database access is available.
 */
interface QuranVerseProvider {
  suspend fun getAllVerses(): List<IndexedVerse>
}
