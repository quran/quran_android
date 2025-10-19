package com.quran.labs.androidquran.presenter.data

import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JuzListPresenter @Inject constructor(
  private val quranInfo: QuranInfo,
  private val arabicDatabaseUtils: ArabicDatabaseUtils
) {

  suspend fun quarters(): List<String> {
    return withContext(Dispatchers.IO) {
      val ayahIds = quranInfo.quarters.map { juz ->
        quranInfo.getAyahId(juz.sura, juz.ayah)
      }
      val results = arabicDatabaseUtils.getAyahTextForAyat(ayahIds)
      ayahIds.map { id -> results[id] ?: "" }
    }
  }
}
