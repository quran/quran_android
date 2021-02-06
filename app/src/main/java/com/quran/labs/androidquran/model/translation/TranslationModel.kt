package com.quran.labs.androidquran.model.translation

import android.content.Context
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.di.ActivityScope
import javax.inject.Inject
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.data.model.VerseRange
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.data.pageinfo.mapper.IdentityAyahMapper
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.DatabaseHandler.TextType
import io.reactivex.Single

@ActivityScope
class TranslationModel @Inject internal constructor(
  private val appContext: Context,
  private val quranFileUtils: QuranFileUtils,
  private val ayahMapper: AyahMapper
) {

  fun getArabicFromDatabase(verses: VerseRange): Single<List<QuranText>> {
    return getVersesFromDatabase(
        verses,
        QuranDataProvider.QURAN_ARABIC_DATABASE,
        TextType.ARABIC,
        shouldMap = false
    )
  }

  fun getTranslationFromDatabase(verses: VerseRange, db: String): Single<List<QuranText>> {
    return getVersesFromDatabase(verses, db, TextType.TRANSLATION, shouldMap = true)
  }

  private fun getVersesFromDatabase(
    verses: VerseRange,
    database: String,
    @TextType type: Int,
    shouldMap: Boolean = false
  ): Single<List<QuranText>> {
    return Single.fromCallable {
      val databaseHandler = DatabaseHandler.getDatabaseHandler(appContext, database, quranFileUtils)

      if (shouldMap) {
        val mappedRange = ayahMapper.mapRange(verses)
        val data = databaseHandler.getVerses(mappedRange, type)
        ayahMapper.mapKufiData(verses, data)
      } else {
        databaseHandler.getVerses(verses, type)
      }
    }
  }
}
