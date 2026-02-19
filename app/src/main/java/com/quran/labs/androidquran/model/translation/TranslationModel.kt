package com.quran.labs.androidquran.model.translation

import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange

interface TranslationModel {
  suspend fun getArabicFromDatabase(verses: VerseRange): List<QuranText>
  suspend fun getTranslationFromDatabase(verses: VerseRange, db: String): List<QuranText>
}
