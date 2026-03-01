package com.quran.labs.androidquran.model.translation

import android.content.Context
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.DatabaseUtils
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.common.search.SearchTextUtil
import com.quran.mobile.voicesearch.IndexedVerse
import com.quran.mobile.voicesearch.QuranVerseProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.quran.data.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
@SingleIn(AppScope::class)
class QuranVerseProviderImpl @Inject constructor(
  @ApplicationContext private val appContext: Context,
  private val quranFileUtils: QuranFileUtils
) : QuranVerseProvider {

  private var cachedVerses: List<IndexedVerse>? = null

  override suspend fun getAllVerses(): List<IndexedVerse> {
    cachedVerses?.let { return it }

    return withContext(Dispatchers.IO) {
      val verses = mutableListOf<IndexedVerse>()
      try {
        val handler = DatabaseHandler.getDatabaseHandler(
          appContext,
          QuranDataProvider.QURAN_ARABIC_DATABASE,
          quranFileUtils
        )

        var cursor: android.database.Cursor? = null
        try {
          cursor = handler.getVerses(1, 1, 114, 6, QuranFileConstants.ARABIC_SHARE_TABLE)
          while (cursor?.moveToNext() == true) {
            val sura = cursor.getInt(1)
            val ayah = cursor.getInt(2)
            val rawText = cursor.getString(3)
            val cleanText = ArabicDatabaseUtils.getAyahWithoutBasmallah(sura, ayah, rawText)
            val normalized = SearchTextUtil.normalizeArabic(cleanText)
            verses.add(
              IndexedVerse(
                sura = sura,
                ayah = ayah,
                rawText = cleanText,
                normalizedText = normalized,
                words = SearchTextUtil.tokenizeArabic(normalized)
              )
            )
          }
        } finally {
          DatabaseUtils.closeCursor(cursor)
        }
      } catch (e: Exception) {
        // Database not available yet - return empty list
      }

      cachedVerses = verses
      verses
    }
  }
}
