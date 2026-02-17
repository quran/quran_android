package com.quran.labs.androidquran.model.translation

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.source.DisplaySize
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ArabicDatabaseUtilsTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  // DatabaseHandler is not open and has a private constructor; keep as mock
  @Mock
  lateinit var arabicHandler: DatabaseHandler

  // QuranFileUtils constructed with real Robolectric context + FakePageProvider.
  // Its methods are never invoked: getArabicDatabaseHandler() and getAyahTextForAyat()
  // are both overridden in the anonymous subclass used by each test.
  private val quranFileUtils: QuranFileUtils by lazy {
    val fakePageProvider = FakePageProvider()
    // QuranScreenInfo requires a Display object; Robolectric has no replacement shadow for
    // DisplayManager.getDisplay(), so the deprecated WindowManager.defaultDisplay is used.
    // Can be removed once QuranScreenInfo is refactored to not require Display.
    @Suppress("DEPRECATION")
    val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val pageSizeCalculator = fakePageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    val quranScreenInfo = QuranScreenInfo(context, display, pageSizeCalculator)
    QuranFileUtils(context, fakePageProvider, quranScreenInfo)
  }

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this@ArabicDatabaseUtilsTest)
  }

  @Test
  fun testHydrateAyahText() {
    val arabicDatabaseUtils = getArabicDatabaseUtils()

    val bookmarks = mutableListOf(
      Bookmark(1, 1, 1, 1),
      Bookmark(2, null, null, 3),
      Bookmark(3, 114, 6, 604),
    )

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

    val bookmarks = mutableListOf(Bookmark(1, null, null, 3))

    val result = arabicDatabaseUtils.hydrateAyahText(bookmarks)
    assertThat(result).hasSize(1)
    assertThat(result[0].ayahText).isNull()
    assertThat(result).isSameInstanceAs(bookmarks)
  }

  private fun getArabicDatabaseUtils(): ArabicDatabaseUtils {
    return object : ArabicDatabaseUtils(context,
      QuranInfo(MadaniDataSource()),
      quranFileUtils) {

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
