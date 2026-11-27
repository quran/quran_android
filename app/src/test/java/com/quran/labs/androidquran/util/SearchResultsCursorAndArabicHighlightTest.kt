package com.quran.labs.androidquran.util

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SparseArray
import androidx.core.text.HtmlCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.ArabicSearcher
import com.quran.common.search.DefaultSearcher
import com.quran.common.search.RustSearchResultsCursor
import com.quran.common.search.SearchResultsCursor
import com.quran.common.search.arabic.ArabicCompiledQuery
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.flow.Flow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SearchResultsCursorAndArabicHighlightTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    SearchResultFormatter.clearCache()
  }

  @After
  fun tearDown() {
    SearchResultFormatter.clearCache()
  }

  @Test
  fun `SearchResultsCursor dynamically grows and exposes unboxed columns accurately`() {
    val columns = arrayOf("_id", "sura", "ayah", "text")
    val builder = SearchResultsCursor.Builder(columns, initialCapacity = 8)
    for (i in 1..25) {
      builder.addRow(i, (i % 114) + 1, i * 2, "verse $i")
    }
    builder.build().use { cursor ->
      assertThat(cursor.count).isEqualTo(25)
      assertThat(cursor.columnNames).isEqualTo(columns)
      assertThat(cursor.moveToPosition(24)).isTrue()
      assertThat(cursor.getInt(0)).isEqualTo(25)
      assertThat(cursor.getLong(0)).isEqualTo(25L)
      assertThat(cursor.getInt(1)).isEqualTo(26)
      assertThat(cursor.getInt(2)).isEqualTo(50)
      assertThat(cursor.getString(3)).isEqualTo("verse 25")
      assertThat(cursor.isNull(3)).isFalse()
    }
  }

  @Test
  fun `RustSearchResultsCursor exposes packed ids suras ayahs and verse indices accurately`() {
    val columns = arrayOf("_id", "sura", "ayah", "text")
    val packed = intArrayOf(
      // [verseIndex, rowId, sura, ayah]
      0, 1, 1, 1,
      254, 262, 2, 255
    )
    RustSearchResultsCursor(columns, packed, indexGeneration = 7).use { cursor ->
      assertThat(cursor.count).isEqualTo(2)
      assertThat(cursor.indexGeneration).isEqualTo(7)
      assertThat(cursor.columnNames).isEqualTo(columns)

      assertThat(cursor.moveToFirst()).isTrue()
      assertThat(cursor.getCurrentVerseIndex()).isEqualTo(0)
      assertThat(cursor.getInt(0)).isEqualTo(1)
      assertThat(cursor.getLong(0)).isEqualTo(1L)
      assertThat(cursor.getInt(1)).isEqualTo(1)
      assertThat(cursor.getInt(2)).isEqualTo(1)
      assertThat(cursor.getString(0)).isEqualTo("1")
      assertThat(cursor.isNull(3)).isTrue()

      assertThat(cursor.moveToNext()).isTrue()
      assertThat(cursor.getCurrentVerseIndex()).isEqualTo(254)
      assertThat(cursor.getInt(0)).isEqualTo(262)
      assertThat(cursor.getInt(1)).isEqualTo(2)
      assertThat(cursor.getInt(2)).isEqualTo(255)
    }
  }

  @Test
  fun `formatArabic matches HtmlCompat color spans exactly and invalidates cache on query or color change`() {
    val colorA = -13388315
    val colorB = -16711936
    val rawText = "الله لا إله إلا هو الحي القيوم والله عليم"
    val htmlEquivalent =
      "<font color=\"$colorA\">الله</font> لا إله إلا هو الحي القيوم و<font color=\"$colorA\">الله</font> عليم"

    val compiled = ArabicCompiledQuery.compile("الله")!!
    val actual = SearchResultFormatter.formatArabic(rawText, compiled, colorA) as Spanned
    val expected = HtmlCompat.fromHtml(htmlEquivalent, HtmlCompat.FROM_HTML_MODE_COMPACT)

    assertThat(actual.toString()).isEqualTo(expected.toString())
    val actualSpans = actual.getSpans(0, actual.length, ForegroundColorSpan::class.java)
      .map { Triple(actual.getSpanStart(it), actual.getSpanEnd(it), it.foregroundColor) }
    val expectedSpans = expected.getSpans(0, expected.length, ForegroundColorSpan::class.java)
      .map { Triple(expected.getSpanStart(it), expected.getSpanEnd(it), it.foregroundColor) }
    assertThat(actualSpans).isEqualTo(expectedSpans)

    // Cached repeat returns same instance
    assertThat(SearchResultFormatter.formatArabic(rawText, compiled, colorA)).isSameInstanceAs(actual)
    assertThat(SearchResultFormatter.arabicCacheSize()).isEqualTo(1)

    // Changing highlight color evicts and re-highlights with new color
    val recolored = SearchResultFormatter.formatArabic(rawText, compiled, colorB) as Spanned
    val recoloredSpans = recolored.getSpans(0, recolored.length, ForegroundColorSpan::class.java)
    assertThat(recoloredSpans.map { it.foregroundColor }).containsExactly(colorB, colorB)

    // HTML input automatically delegates to format()
    val htmlInput = "بسم <font color=\"$colorA\">الله</font>"
    val formattedHtml = SearchResultFormatter.formatArabic(htmlInput, compiled, colorA)
    assertThat(formattedHtml.toString()).isEqualTo("بسم الله")
  }

  @Test
  fun `ArabicSearcher runQueryRaw returns full unformatted matches without suggestions cap`() {
    val db = SQLiteDatabase.create(null)
    try {
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      for (i in 1..12) {
        db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (3, $i, 'إن الله مع الصابرين $i')")
      }
      val searcher = ArabicSearcher(
        defaultSearcher = DefaultSearcher("<font color=\"-1\">", "</font>", "..."),
        matchStart = "<font color=\"-1\">",
        matchEnd = "</font>"
      )
      val columns = arrayOf("_id", "sura", "ayah", "text")
      val sql = searcher.getQuery(true, false, "verses", "rowid as _id, sura, ayah", "text")
      val likeArg = searcher.processSearchText("الله", false)

      searcher.runQueryRaw(db, sql, likeArg, "الله", columns).use { cursor ->
        assertThat(cursor.count).isEqualTo(12)
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(3)).isEqualTo("إن الله مع الصابرين 1")
        assertThat(cursor.getString(3)).doesNotContain("<font")
      }
    } finally {
      db.close()
    }
  }

  @Test
  fun `QuranDataProvider skips translationsDBAdapter completely for Arabic queries`() {
    var translationsDbTouched = false
    val throwingTranslationsAdapter = object : TranslationsDBAdapter {
      override fun getTranslations(): Flow<List<LocalTranslation>> {
        translationsDbTouched = true
        error("translationsDBAdapter must not be queried for Arabic search")
      }
      override suspend fun translationsHash(): SparseArray<LocalTranslation> = SparseArray()
      override suspend fun writeTranslationUpdates(updates: List<com.quran.labs.androidquran.dao.translation.TranslationItem>) = true
      override suspend fun deleteTranslationByFileName(filename: String) = Unit
    }

    val fileUtils = FakeQuranFileUtils.create(context)
    val dbDir = fileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val arabicDb = File(dbDir, QuranDataProvider.QURAN_ARABIC_DATABASE).apply { writeText("stub") }
    try {
      val provider = object : QuranDataProvider() {
        override fun search(query: String, databaseName: String, wantSnippets: Boolean): Cursor {
          return MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
            addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
          }
        }
      }
      provider.attachInfo(context, ProviderInfo().apply { authority = "com.quran.test" })
      provider.quranFileUtils = fileUtils
      provider.translationsDBAdapter = throwingTranslationsAdapter
      provider.quranInfo = QuranInfo(MadaniDataSource())
      provider.quranDisplayData = QuranDisplayData(provider.quranInfo)

      provider.search("الله").use { cursor ->
        assertThat(cursor).isNotNull()
        assertThat(cursor!!.count).isEqualTo(1)
      }
      assertThat(translationsDbTouched).isFalse()
    } finally {
      arabicDb.delete()
    }
  }
}
