package com.quran.labs.androidquran.model.translation

import android.content.Context
import android.database.Cursor
import androidx.annotation.VisibleForTesting
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.database.DatabaseHandler.Companion.getDatabaseHandler
import com.quran.labs.androidquran.database.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.mobile.di.qualifier.ApplicationContext
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ArabicDatabaseUtils @Inject internal constructor(
  @param:ApplicationContext private val appContext: Context,
  private val quranInfo: QuranInfo,
  quranFileUtils: QuranFileUtils
) {
  private val quranFileUtils: QuranFileUtils
  private var arabicDatabaseHandler: DatabaseHandler?

  init {
    arabicDatabaseHandler = getArabicDatabaseHandler()
    this.quranFileUtils = quranFileUtils
  }

  // open only for testing
  @VisibleForTesting
  internal open fun getArabicDatabaseHandler(): DatabaseHandler? {
    if (arabicDatabaseHandler == null) {
      try {
        arabicDatabaseHandler = getDatabaseHandler(
          appContext.applicationContext, QuranDataProvider.QURAN_ARABIC_DATABASE, quranFileUtils
        )
      } catch (e: Exception) {
        // ignore
      }
    }
    return arabicDatabaseHandler
  }

  fun getVerses(start: SuraAyah, end: SuraAyah): Single<List<QuranText>> {
    return Single.fromCallable<List<QuranText>> {
      val verses: MutableList<QuranText> = ArrayList()
      var cursor: Cursor? = null
      try {
        val arabicDatabaseHandler = getArabicDatabaseHandler()
        cursor = arabicDatabaseHandler?.getVerses(
          start.sura, start.ayah,
          end.sura, end.ayah, QuranFileConstants.ARABIC_SHARE_TABLE
        )
        while (cursor?.moveToNext() == true) {
          val sura = cursor.getInt(1)
          val ayah = cursor.getInt(2)
          val extra =
            if (!QuranFileConstants.ARABIC_SHARE_TEXT_HAS_BASMALLAH && ayah == 1 && sura != 9 && sura != 1) {
              AR_BASMALLAH + " "
            } else {
              ""
            }

          val verse = QuranText(
            sura, ayah,
            extra + cursor.getString(3),
            null
          )
          verses.add(verse)
        }
      } catch (e: Exception) {
        // no op
      } finally {
        closeCursor(cursor)
      }
      verses
    }.subscribeOn(Schedulers.io())
  }

  fun hydrateAyahText(bookmarks: MutableList<Bookmark>): List<Bookmark> {
    val ayahIds = bookmarks.filter { !it.isPageBookmark() }
      .map { quranInfo.getAyahId(it.sura!!, it.ayah!!) }

    return if (ayahIds.isEmpty()) bookmarks else mergeBookmarksWithAyahText(
      bookmarks,
      getAyahTextForAyat(ayahIds)
    )
  }

  open fun getAyahTextForAyat(ayat: List<Int>): Map<Int, String> {
    val result: MutableMap<Int, String> = HashMap(ayat.size)
    val arabicDatabaseHandler = getArabicDatabaseHandler()
    if (arabicDatabaseHandler != null) {
      var cursor: Cursor? = null
      try {
        cursor = arabicDatabaseHandler.getVersesByIds(ayat)
        while (cursor!!.moveToNext()) {
          val id = cursor.getInt(0)
          val sura = cursor.getInt(1)
          val ayah = cursor.getInt(2)
          val text = cursor.getString(3)
          result[id] =
            getFirstFewWordsFromAyah(sura, ayah, text)
        }
      } finally {
        closeCursor(cursor)
      }
    }
    return result
  }

  private fun mergeBookmarksWithAyahText(
    bookmarks: MutableList<Bookmark>, ayahMap: Map<Int, String>
  ): List<Bookmark> {
    val result: MutableList<Bookmark>
    if (ayahMap.isEmpty()) {
      result = bookmarks
    } else {
      result = ArrayList(bookmarks.size)
      var i = 0
      val bookmarksSize = bookmarks.size
      while (i < bookmarksSize) {
        val bookmark = bookmarks[i]
        var toAdd: Bookmark
        if (bookmark.isPageBookmark()) {
          toAdd = bookmark
        } else {
          val ayahText = ayahMap[quranInfo.getAyahId(
            bookmark.sura!!,
            bookmark.ayah!!
          )]
          toAdd = if (ayahText == null) bookmark else bookmark.withAyahText(ayahText)
        }
        result.add(toAdd)
        i++
      }
    }
    return result
  }

  companion object {
    const val AR_BASMALLAH: String = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
    const val AR_BASMALLAH_IN_TEXT: String = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَـٰنِ ٱلرَّحِیمِ"

    @VisibleForTesting
    const val NUMBER_OF_WORDS: Int = 4

    fun getFirstFewWordsFromAyah(sura: Int, ayah: Int, text: String): String {
      val ayahText = getAyahWithoutBasmallah(sura, ayah, text)
      var start = 0
      var i = 0
      while (i < NUMBER_OF_WORDS && start > -1) {
        start = ayahText.indexOf(' ', start + 1)
        i++
      }
      return if (start > 0) ayahText.substring(0, start) else ayahText
    }

    /**
     * Get the actual ayahText from the given ayahText. This is important because, currently, the
     * arabic database (quran.ar.db) has the first verse from each sura as "[basmallah] ayah" - this
     * method just returns ayah without basmallah.
     *
     * @param sura     the sura number
     * @param ayah     the ayah number
     * @param ayahText the ayah text
     * @return the ayah without the basmallah
     */
    fun getAyahWithoutBasmallah(sura: Int, ayah: Int, ayahText: String): String {
      // note that ayahText.startsWith check is always true for now - but it's explicitly here so
      // that if we update quran.ar.db one day to fix this issue and older clients get a new copy of
      // the database, their code continues to work as before.
      if (ayah == 1 && sura != 9 && sura != 1) {
        if (ayahText.startsWith(AR_BASMALLAH_IN_TEXT)) {
          return ayahText.substring(AR_BASMALLAH_IN_TEXT.length + 1)
        } else if (ayahText.startsWith(AR_BASMALLAH)) {
          return ayahText.substring(AR_BASMALLAH.length + 1)
        }
      }
      return ayahText
    }
  }
}
