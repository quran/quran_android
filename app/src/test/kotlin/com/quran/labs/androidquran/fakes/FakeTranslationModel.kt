package com.quran.labs.androidquran.fakes

import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.model.translation.TranslationModel

/**
 * Fake implementation of TranslationModel for testing.
 *
 * Provides configurable responses for Arabic and translation text retrieval.
 * Used by BaseTranslationPresenterTest.
 */
class FakeTranslationModel : TranslationModel {

  private val arabicTexts = mutableMapOf<VerseRange, List<QuranText>>()
  private val translationTexts = mutableMapOf<Pair<VerseRange, String>, List<QuranText>>()
  private var arabicError: Exception? = null
  private val translationErrors = mutableMapOf<String, Exception>()

  fun setArabicText(verses: VerseRange, text: List<QuranText>) {
    arabicTexts[verses] = text
  }

  fun setTranslationText(verses: VerseRange, database: String, text: List<QuranText>) {
    translationTexts[verses to database] = text
  }

  fun setArabicError(e: Exception) {
    arabicError = e
  }

  fun setTranslationError(db: String, e: Exception) {
    translationErrors[db] = e
  }

  fun clearAll() {
    arabicTexts.clear()
    translationTexts.clear()
    arabicError = null
    translationErrors.clear()
  }

  override suspend fun getArabicFromDatabase(verses: VerseRange): List<QuranText> {
    arabicError?.let { throw it }
    return arabicTexts[verses] ?: emptyList()
  }

  override suspend fun getTranslationFromDatabase(verses: VerseRange, db: String): List<QuranText> {
    translationErrors[db]?.let { throw it }
    return translationTexts[verses to db] ?: emptyList()
  }
}
