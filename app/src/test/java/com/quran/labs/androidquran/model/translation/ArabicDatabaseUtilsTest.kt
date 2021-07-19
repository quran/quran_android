package com.quran.labs.androidquran.model.translation

import android.content.Context

import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.pageinfo.common.MadaniDataSource
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.util.QuranFileUtils

import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import com.google.common.truth.Truth.assertThat

import java.util.ArrayList

class ArabicDatabaseUtilsTest {

  @Mock
  lateinit var context: Context

  @Mock
  lateinit var arabicHandler: DatabaseHandler

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this@ArabicDatabaseUtilsTest)
  }

  @Test
  fun testHydrateAyahText() {
    val arabicDatabaseUtils = getArabicDatabaseUtils()

    val bookmarks = ArrayList<Bookmark>(3)
    bookmarks.add(Bookmark(1, 1, 1, 1))
    bookmarks.add(Bookmark(2, null, null, 3))
    bookmarks.add(Bookmark(3, 114, 6, 604))

    val result = arabicDatabaseUtils.hydrateAyahText(bookmarks)
    assertThat(result).hasSize(3)

    assertThat(result[0].ayahText).isNotEmpty()
    assertThat(result[1].ayahText).isNull()
    assertThat(result[2].ayahText).isNotEmpty()

    assertThat(result).isNotSameInstanceAs(bookmarks)
  }

  @Test
  fun testHydrateAyahTextEmpty() {
    val arabicDatabaseUtils = getArabicDatabaseUtils()

    val bookmarks = ArrayList<Bookmark>(1)
    bookmarks.add(Bookmark(1, null, null, 3))

    val result = arabicDatabaseUtils.hydrateAyahText(bookmarks)
    assertThat(result).hasSize(1)
    assertThat(result[0].ayahText).isNull()
    assertThat(result).isSameInstanceAs(bookmarks)
  }

  private fun getArabicDatabaseUtils(): ArabicDatabaseUtils {
    return object : ArabicDatabaseUtils(context,
      QuranInfo(MadaniDataSource()),
      mock(QuranFileUtils::class.java)) {

      override fun getArabicDatabaseHandler(): DatabaseHandler {
        return arabicHandler
      }

      override fun getAyahTextForAyat(ayat: List<Int>): Map<Int, String> {
        return ayat.map { ayahId -> ayahId to "verse $ayahId" }.toMap()
      }
    }
  }

  @Test
  fun testGetFirstFewWordsFromAyah() {
    val total = ArabicDatabaseUtils.NUMBER_OF_WORDS
    for (i in 1 until total) {
      val text = makeText(i)
      assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, text)).isSameInstanceAs(text)
    }

    val veryLongString = makeText(100)
    assertThat(ArabicDatabaseUtils.getFirstFewWordsFromAyah(4, 1, veryLongString))
      .isEqualTo(makeText(4))
  }

  private fun makeText(words: Int): String {
    return (0 until words).joinToString(" ") { "word$it" }
  }

  @Test
  fun testGetAyahWithoutBasmallah() {
    val basmallah = ArabicDatabaseUtils.AR_BASMALLAH

    val original = "$basmallah first ayah"
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(1, 1, original)).isSameInstanceAs(original)
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(9, 1, original)).isSameInstanceAs(original)
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 4, original)).isSameInstanceAs(original)

    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 1, original)).isEqualTo("first ayah")
    assertThat(ArabicDatabaseUtils.getAyahWithoutBasmallah(4, 1, "first ayah")).isEqualTo("first ayah")
  }

}
