package com.quran.labs.androidquran.hybrid.search

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridSearchEngineTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo
  private lateinit var quranDisplayData: QuranDisplayData
  private lateinit var translationsAdapter: FakeTranslationsDBAdapter
  private lateinit var bookmarksDao: FakeBookmarksDao
  private lateinit var recentSearchRepository: RecentSearchRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
    quranDisplayData = QuranDisplayData(quranInfo)
    translationsAdapter = FakeTranslationsDBAdapter()
    bookmarksDao = FakeBookmarksDao { sa -> quranInfo.getPageFromSuraAyah(sa.sura, sa.ayah) }
    val prefs = context.getSharedPreferences("test_hybrid_search_engine_recents", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    recentSearchRepository = RecentSearchRepository(prefs)
    FakeEngineProvider.reset()
  }

  @After
  fun tearDown() {
    FakeEngineProvider.reset()
  }

  @Test
  fun `SuraSearchIndex matches all 114 surahs by normalized Arabic, transliteration, and digits`() {
    val index = SuraSearchIndex(quranInfo, quranDisplayData)

    assertThat(index.search(context, "")).hasSize(114)
    assertThat(index.search(context, "الفاتحة").first().sura).isEqualTo(1)
    assertThat(index.search(context, "ال عمران").first().sura).isEqualTo(3)
    assertThat(index.search(context, "المائدة").first().sura).isEqualTo(5)
    assertThat(index.search(context, "kahf").first().sura).isEqualTo(18)
    assertThat(index.search(context, "18").first().sura).isEqualTo(18)
    assertThat(index.search(context, "١٨").first().sura).isEqualTo(18)
    assertThat(index.search(context, "سورة النبأ").first().sura).isEqualTo(78)

    // Space-separated & hyphenated Latin surah queries
    assertThat(index.search(context, "Al Fatihah").first().sura).isEqualTo(1)
    assertThat(index.search(context, "Al-Fatihah").first().sura).isEqualTo(1)
    assertThat(index.search(context, "Fatihah").first().sura).isEqualTo(1)
    assertThat(index.search(context, "alfatihah").first().sura).isEqualTo(1)
    assertThat(index.search(context, "Al Baqarah").first().sura).isEqualTo(2)
    assertThat(index.search(context, "Ali Imran").first().sura).isEqualTo(3)
    assertThat(index.search(context, "Al Kahf").first().sura).isEqualTo(18)
    assertThat(index.search(context, "Ta Ha").first().sura).isEqualTo(20)
    assertThat(index.search(context, "Ya Sin").first().sura).isEqualTo(36)
  }

  @Test
  fun `submitQuery unwraps CursorWrapper around RustSearchResultsCursor and records recent queries`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val rustCursor = RustSearchResultsCursor(
      columnNames = arrayOf("_id", "sura", "ayah", "text"),
      packedMatches = intArrayOf(0, 1, 1, 1),
      indexGeneration = 1
    )
    registerFakeProvider { uri, _ ->
      assertThat(uri).isEqualTo(QuranDataProvider.SEARCH_RAW_ARABIC_URI)
      CursorWrapper(rustCursor)
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS, recordRecent = true)

      val state = engine.uiState.value
      assertThat(state.ayahHits).hasSize(1)
      assertThat(state.ayahHits.single().sura).isEqualTo(1)
      assertThat(state.ayahHits.single().ayah).isEqualTo(1)
      assertThat(state.rustElapsedMs).isNotNull()
      assertThat(state.recentQueries).containsExactly("الله")
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `tafsir scope sets noTafsirInstalled when empty and returns tafsir hits when installed`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    registerFakeProvider { uri, args ->
      assertThat(uri).isEqualTo(QuranDataProvider.SEARCH_TAFSIR_URI)
      assertThat(args?.firstOrNull()).isEqualTo("الرحمن")
      MatrixCursor(QuranDataProvider.TAFSIR_COLUMNS).apply {
        addRow(arrayOf<Any>(1L, 1, 1, "تفسير <font color=\"-1\">الرحمن</font>", "التفسير الميسر", 10L, "ar"))
      }
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.submitQuery("الرحمن", scopeOverride = HybridSearchScope.TAFSIR)
      assertThat(engine.uiState.value.noTafsirInstalled).isTrue()
      assertThat(engine.uiState.value.tafsirHits).isEmpty()

      translationsAdapter.addTranslation(
        LocalTranslation(id = 10L, filename = "ar.muyassar.db", name = "الميسر", translator = "الميسر", languageCode = "ar")
      )
      engine.refreshCurrentSearch()

      val state = engine.uiState.value
      assertThat(state.noTafsirInstalled).isFalse()
      assertThat(state.tafsirHits).hasSize(1)
      val hit = state.tafsirHits.single()
      assertThat(hit.translatorName).isEqualTo("التفسير الميسر")
      assertThat(hit.text).isEqualTo("تفسير الرحمن")
      assertThat(hit.highlightRanges).containsExactly(HighlightSpanRange(6, 12))
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `toggleBookmark updates reactive bookmarkedKeys in uiState`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.toggleBookmark(sura = 1, ayah = 1)
      assertThat(engine.uiState.value.bookmarkedKeys).containsExactly(ayahBookmarkKey(1, 1))

      engine.toggleBookmark(sura = 1, ayah = 1)
      assertThat(engine.uiState.value.bookmarkedKeys).isEmpty()
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `tafsir hits with identical id sura ayah and translatorName produce distinct stableKeys`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    translationsAdapter.addTranslation(
      LocalTranslation(id = 10L, filename = "tafsir_a.db", name = "Tafsir A", translator = "مجمع الملك فهد", languageCode = "ar")
    )
    translationsAdapter.addTranslation(
      LocalTranslation(id = 11L, filename = "tafsir_b.db", name = "Tafsir B", translator = "مجمع الملك فهد", languageCode = "ar")
    )
    registerFakeProvider { uri, _ ->
      assertThat(uri).isEqualTo(QuranDataProvider.SEARCH_TAFSIR_URI)
      MatrixCursor(QuranDataProvider.TAFSIR_COLUMNS).apply {
        addRow(arrayOf<Any>(1L, 1, 1, "تفسير أول للرحمن", "مجمع الملك فهد", 10L, "ar"))
        addRow(arrayOf<Any>(1L, 1, 1, "تفسير ثان للرحمن", "مجمع الملك فهد", 11L, "ar"))
        addRow(arrayOf<Any>(1L, 1, 1, "تفسير مكرر", "مجمع الملك فهد", 11L, "ar"))
      }
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.submitQuery("الرحمن", scopeOverride = HybridSearchScope.TAFSIR)

      val hits = engine.uiState.value.tafsirHits
      assertThat(hits).hasSize(3)
      assertThat(hits.map { it.translationId }).containsExactly(10L, 11L, 11L).inOrder()
      assertThat(hits.map { it.stableKey }.toSet()).hasSize(3)
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `clearQuery in empty TAFSIR scope keeps noTafsirInstalled true and in SURAS scope restores 114 suras`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.onScopeSelected(HybridSearchScope.TAFSIR)
      assertThat(engine.uiState.value.noTafsirInstalled).isTrue()

      engine.submitQuery("الرحمن", scopeOverride = HybridSearchScope.TAFSIR)
      assertThat(engine.uiState.value.noTafsirInstalled).isTrue()

      engine.clearQuery()
      assertThat(engine.uiState.value.query).isEmpty()
      assertThat(engine.uiState.value.scope).isEqualTo(HybridSearchScope.TAFSIR)
      assertThat(engine.uiState.value.noTafsirInstalled).isTrue()

      engine.onScopeSelected(HybridSearchScope.SURAS)
      engine.onQueryTextChanged("Fatihah")
      assertThat(engine.uiState.value.suraHits).hasSize(1)
      engine.clearQuery()
      assertThat(engine.uiState.value.suraHits).hasSize(114)
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `ayah search windows initial 40 hits while preserving exact totalAyahHitCount and loads more incrementally`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val totalRows = 220
    val packed = IntArray(totalRows * 4)
    for (i in 0 until totalRows) {
      val base = i * 4
      packed[base] = i
      packed[base + 1] = i + 1
      packed[base + 2] = 2
      packed[base + 3] = (i % 286) + 1
    }
    val rustCursor = RustSearchResultsCursor(
      columnNames = arrayOf("_id", "sura", "ayah", "text"),
      packedMatches = packed,
      indexGeneration = 1
    )
    registerFakeProvider { _, _ -> rustCursor }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS, recordRecent = true)

      val initialState = engine.uiState.value
      assertThat(initialState.ayahHits).hasSize(HybridSearchEngine.INITIAL_WINDOW_SIZE)
      assertThat(HybridSearchEngine.INITIAL_WINDOW_SIZE).isEqualTo(40)
      assertThat(HybridSearchEngine.LOAD_MORE_BATCH_SIZE).isEqualTo(60)
      assertThat(HybridSearchEngine.LOAD_MORE_PREFETCH_THRESHOLD).isEqualTo(24)
      assertThat(initialState.totalAyahHitCount).isEqualTo(totalRows)
      assertThat(initialState.activeResultCount).isEqualTo(totalRows)
      assertThat(initialState.hasMoreAyahHits).isTrue()
      assertThat(initialState.ayahHits.first().ayahRefLabel).isNotEmpty()
      assertThat(initialState.ayahHits.first().pageLabel).isNotEmpty()
      assertThat(initialState.ayahHits.first().precomputedAnnotatedText).isNull()
      assertThat(initialState.ayahHits.first().precomputedNativeText).isNotNull()
      assertThat(initialState.recentQueries).containsExactly("الله")
      assertThat(engine.staticLayoutCacheCountForTest()).isAtMost(HybridSearchEngine.STATIC_LAYOUT_CACHE_SIZE)

      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(100)
      assertThat(engine.uiState.value.hasMoreAyahHits).isTrue()

      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(160)
      assertThat(engine.uiState.value.hasMoreAyahHits).isTrue()

      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(totalRows)
      assertThat(engine.uiState.value.hasMoreAyahHits).isFalse()

      engine.trimTransientWindow()
      assertThat(engine.uiState.value.ayahHits).hasSize(HybridSearchEngine.TRANSIENT_TRIM_WINDOW_SIZE)
      assertThat(engine.uiState.value.totalAyahHitCount).isEqualTo(totalRows)
      assertThat(engine.uiState.value.hasMoreAyahHits).isTrue()
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(0)

      engine.clearTransientResults()
      assertThat(engine.uiState.value.ayahHits).isEmpty()
      assertThat(engine.uiState.value.totalAyahHitCount).isEqualTo(0)
      assertThat(engine.uiState.value.hasMoreAyahHits).isFalse()
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(0)
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `tafsir search windows initial 40 hits while preserving exact totalTafsirHitCount and loads more incrementally`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val totalRows = 220
    translationsAdapter.addTranslation(
      LocalTranslation(id = 10L, filename = "ar_muyassar.db", name = "التفسير الميسر", translator = "الميسر", languageCode = "ar")
    )
    registerFakeProvider { uri, _ ->
      assertThat(uri).isEqualTo(QuranDataProvider.SEARCH_TAFSIR_URI)
      MatrixCursor(QuranDataProvider.TAFSIR_COLUMNS).apply {
        for (i in 0 until totalRows) {
          val sura = if (i < 150) 2 else 3
          val ayah = (i % 150) + 1
          addRow(
            arrayOf<Any>(
              (i + 1).toLong(),
              sura,
              ayah,
              "تفسير الآية رقم ${i + 1} وفيه ذكر <font color=\"-1\">اللَّهِ</font>",
              "الميسر",
              10L,
              "ar"
            )
          )
        }
      }
    }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = translationsAdapter,
        recentSearchRepository = recentSearchRepository,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.TAFSIR, recordRecent = true)

      val initialState = engine.uiState.value
      assertThat(initialState.tafsirHits).hasSize(HybridSearchEngine.INITIAL_WINDOW_SIZE)
      assertThat(initialState.totalTafsirHitCount).isEqualTo(totalRows)
      assertThat(initialState.activeResultCount).isEqualTo(totalRows)
      assertThat(initialState.hasMoreTafsirHits).isTrue()
      assertThat(initialState.tafsirHits.first().highlightRanges).isNotEmpty()

      engine.loadMoreTafsirHits()
      assertThat(engine.uiState.value.tafsirHits).hasSize(100)
      assertThat(engine.uiState.value.hasMoreTafsirHits).isTrue()

      engine.loadMoreTafsirHits()
      assertThat(engine.uiState.value.tafsirHits).hasSize(160)
      assertThat(engine.uiState.value.hasMoreTafsirHits).isTrue()

      engine.loadMoreTafsirHits()
      assertThat(engine.uiState.value.tafsirHits).hasSize(totalRows)
      assertThat(engine.uiState.value.hasMoreTafsirHits).isFalse()
      assertThat(engine.uiState.value.tafsirHits.map { it.stableKey }.toSet()).hasSize(totalRows)

      engine.trimTransientWindow()
      assertThat(engine.uiState.value.tafsirHits).hasSize(HybridSearchEngine.TRANSIENT_TRIM_WINDOW_SIZE)
      assertThat(engine.uiState.value.totalTafsirHitCount).isEqualTo(totalRows)
      assertThat(engine.uiState.value.activeResultCount).isEqualTo(totalRows)
      assertThat(engine.uiState.value.hasMoreTafsirHits).isTrue()

      engine.loadMoreTafsirHits()
      assertThat(engine.uiState.value.tafsirHits).hasSize(
        HybridSearchEngine.TRANSIENT_TRIM_WINDOW_SIZE + HybridSearchEngine.LOAD_MORE_BATCH_SIZE
      )
      assertThat(engine.uiState.value.hasMoreTafsirHits).isTrue()

      engine.clearTransientResults()
      assertThat(engine.uiState.value.tafsirHits).isEmpty()
      assertThat(engine.uiState.value.totalTafsirHitCount).isEqualTo(0)
      assertThat(engine.uiState.value.hasMoreTafsirHits).isFalse()
    } finally {
      engineScope.cancel()
    }
  }

  private fun registerFakeProvider(handler: (Uri, Array<out String>?) -> Cursor?) {
    FakeEngineProvider.queryHandler = handler
    Robolectric.buildContentProvider(FakeEngineProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)
  }

  class FakeEngineProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
    ): Cursor? = queryHandler(uri, selectionArgs)

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
      @Volatile
      var queryHandler: (Uri, Array<out String>?) -> Cursor? = { _, _ -> null }
      fun reset() {
        queryHandler = { _, _ -> null }
      }
    }
  }
}
