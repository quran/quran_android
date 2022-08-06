package com.quran.data.dao

import com.quran.data.model.QuranText

interface TranslationsDao {
  suspend fun allAyahs(): List<QuranText>
}
