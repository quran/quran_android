package com.quran.labs.androidquran.hybrid.search

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorIndexOutOfBoundsException
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustArabicSearchEngine
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.ui.buildHybridHighlightedSpan
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridSearchAdversarialTest {

  private lateinit var context: Context
  private lateinit var scope: CoroutineScope
  private lateinit var engine: HybridSearchEngine

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("adv_search_recents", Context.MODE_PRIVATE).apply {
      edit().clear().commit()
    }
    engine = HybridSearchEngine(
      context = context,
      quranInfo = quranInfo,
      quranDisplayData = QuranDisplayData(quranInfo),
      quranFileUtils = FakeQuranFileUtils.create(context),
      translationsDBAdapter = FakeTranslationsDBAdapter(),
      recentSearchRepository = RecentSearchRepository(prefs),
      hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
      scope = scope,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )
  }

  @After
  fun tearDown() {
    FakeAdvSearchProvider.reset()
    scope.cancel()
  }

  @Test
  fun `HybridSearchHighlighter and buildHybridHighlightedSpan ignore out-of-bounds, empty, and inverted ranges`() {
    val text = "بسم الله"
    val filtered = HybridSearchHighlighter.toHighlightRanges(
      packed = intArrayOf(-5, 2, 100, 200, 5, 3, 4, 4, 4, 8),
      textLength = text.length
    )
    assertThat(filtered).containsExactly(HighlightSpanRange(4, 8))

    val annotated = HybridSearchHighlighter.buildHighlightedAnnotatedString(
      text = text,
      ranges = listOf(
        HighlightSpanRange(-5, 2),
        HighlightSpanRange(100, 200),
        HighlightSpanRange(5, 3),
        HighlightSpanRange(4, 4),
        HighlightSpanRange(4, 8)
      ),
      highlightColor = Color(0xFF9A6518),
      highlightBackgroundColor = Color(0x289A6518)
    )
    assertThat(annotated.text).isEqualTo(text)
    assertThat(annotated.spanStyles).hasSize(1)
    assertThat(annotated.spanStyles.single().start).isEqualTo(4)
    assertThat(annotated.spanStyles.single().end).isEqualTo(8)

    val spanned = buildHybridHighlightedSpan(
      text = text,
      ranges = listOf(
        HighlightSpanRange(5, 3),
        HighlightSpanRange(4, 4),
        HighlightSpanRange(4, 8)
      ),
      highlightColor = 0xFF9A6518.toInt(),
      highlightBackgroundColor = 0x289A6518
    ) as android.text.Spanned
    assertThat(spanned.toString()).isEqualTo(text)
    assertThat(spanned.getSpans(0, text.length, Any::class.java)).isNotEmpty()
  }

  @Test
  fun `ensureLoaded rejects malformed table names`() {
    context.cacheDir.mkdirs()
    val dbFile = File(context.cacheDir, "adv_rust.db").apply { delete() }
    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE verses(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 1, 'بسم الله')")

      assertThat(RustArabicSearchEngine.ensureLoaded(db, "verses; DROP TABLE verses--")).isEqualTo(-1)
      assertThat(RustArabicSearchEngine.ensureLoaded(db, "")).isEqualTo(-1)
      assertThat(RustArabicSearchEngine.ensureLoaded(db, "1bad")).isEqualTo(-1)
      assertThat(RustArabicSearchEngine.ensureLoaded(db, "verses ")).isEqualTo(-1)

      db.rawQuery("SELECT COUNT(*) FROM verses", null).use { c ->
        assertThat(c.moveToFirst()).isTrue()
        assertThat(c.getInt(0)).isEqualTo(1)
      }
    } finally {
      db.close()
      dbFile.delete()
    }
  }

  @Test
  fun `RustSearchResultsCursor rejects unpositioned access and OOB columns`() {
    RustSearchResultsCursor(arrayOf("_id", "sura", "ayah", "text"), intArrayOf(), indexGeneration = 1).use { emptyCursor ->
      assertThat(emptyCursor.count).isEqualTo(0)
      assertThat(emptyCursor.moveToFirst()).isFalse()
    }

    RustSearchResultsCursor(
      arrayOf("_id", "sura", "ayah", "text"),
      intArrayOf(7, 42, 2, 255),
      indexGeneration = 9
    ).use { cursor ->
      assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getInt(0) }
      assertThrows(CursorIndexOutOfBoundsException::class.java) { cursor.getCurrentVerseIndex() }
      assertThat(cursor.moveToFirst()).isTrue()
      assertThat(cursor.getCurrentVerseIndex()).isEqualTo(7)
      assertThat(cursor.getInt(0)).isEqualTo(42)
      assertThat(cursor.getInt(1)).isEqualTo(2)
      assertThat(cursor.getInt(2)).isEqualTo(255)
      assertThrows(IndexOutOfBoundsException::class.java) { cursor.getInt(-1) }
      assertThrows(IndexOutOfBoundsException::class.java) { cursor.getString(4) }
      assertThat(cursor.getString(3)).isNull()
      assertThat(cursor.isNull(3)).isTrue()
    }
  }

  @Test
  fun `NativeHybridSearchView handles adversarial queries, format specifiers, unbind, detach, and rebind`() {
    FakeAdvSearchProvider.queryHandler = { _, _ -> MatrixCursor(arrayOf("_id", "sura", "ayah", "text")) }
    Robolectric.buildContentProvider(FakeAdvSearchProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)

    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val container = FrameLayout(activity)
    activity.setContentView(container)

    val view = NativeHybridSearchView(activity)
    container.addView(view)

    val handler = object : HybridSearchActionHandler {
      override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
      override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
      override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
    }

    view.bind(engine, handler, scope, activity)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // Format specifiers (%1$s, %n), ZWJ, tatweel, quotes, and huge queries must not crash empty-state rendering
    for (q in listOf("%1\$s %n %%", "ال\u0640ل\u200Dه", "\"((NEAR/5))\"", "س".repeat(1200), "")) {
      engine.submitQuery(q, scopeOverride = HybridSearchScope.AYAHS, recordRecent = false)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    view.releaseFocusAndInput()
    view.unbind()
    container.removeView(view)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // Rebind after detach must work cleanly
    container.addView(view)
    view.bind(engine, handler, scope, activity)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
    view.unbind()
  }

  @Test
  fun `searchTafsir survives FTS4 special chars`() {
    val fileUtils = FakeQuranFileUtils.create(context)
    val dbDir = fileUtils.getQuranDatabaseDirectory().apply { mkdirs() }
    val dbName = "adv_fts4.db"
    val dbFile = File(dbDir, dbName).apply { delete() }
    DatabaseHandler.clearDatabaseHandlerIfExists(dbName)

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL("CREATE TABLE properties(property TEXT, value INTEGER)")
      db.execSQL("INSERT INTO properties(property, value) VALUES ('schema_version', 2)")
      db.execSQL("CREATE VIRTUAL TABLE verses USING fts4(sura INTEGER, ayah INTEGER, text TEXT)")
      db.execSQL("INSERT INTO verses(sura, ayah, text) VALUES (1, 1, 'mercy near grace')")
      db.close()

      val provider = object : QuranDataProvider() {}
      provider.attachInfo(context, ProviderInfo().apply { authority = QuranDataProvider.SEARCH_URI.authority!! })
      provider.quranInfo = QuranInfo(MadaniDataSource())
      provider.quranDisplayData = QuranDisplayData(provider.quranInfo)
      provider.quranFileUtils = fileUtils
      provider.translationsDBAdapter = FakeTranslationsDBAdapter()

      val translations = listOf(LocalTranslation(id = 1L, filename = dbName, name = "Adv", languageCode = "en"))

      for (q in listOf("\"mercy", "(mercy)", "mercy*", "^mercy", "NEAR/5", "\"\"\"\"", "()[]{}")) {
        provider.searchTafsir(q, translations)?.close()
      }
    } finally {
      if (db.isOpen) db.close()
      DatabaseHandler.clearDatabaseHandlerIfExists(dbName)
      dbFile.delete()
    }
  }

  class FakeAdvSearchProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
      uri: android.net.Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
    ): Cursor? = queryHandler(uri, selectionArgs)

    override fun getType(uri: android.net.Uri): String? = null
    override fun insert(uri: android.net.Uri, values: ContentValues?): android.net.Uri? = null
    override fun delete(uri: android.net.Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
      uri: android.net.Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
      @Volatile
      var queryHandler: (android.net.Uri, Array<out String>?) -> Cursor? = { _, _ -> null }
      fun reset() {
        queryHandler = { _, _ -> null }
      }
    }
  }
}
