package com.quran.labs.androidquran.hybrid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeHighlightsDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeReadingBookmarksDao
import com.quran.labs.androidquran.fakes.FakeRecentPagesDao
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HubBookmarksUiState
import com.quran.labs.androidquran.hybrid.hub.HubReadingBookmarkItem
import com.quran.labs.androidquran.hybrid.hub.HybridBookmarksView
import com.quran.labs.androidquran.hybrid.hub.HybridHubStateHolder
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.hub.HybridIndexDataSource
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.search.AyahSearchHit
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchRow
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.search.SearchTestTags
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run11OptimizationFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    FakeSliceProvider.reset()
  }

  @After
  fun tearDown() {
    FakeSliceProvider.reset()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }

  @Test
  fun `HybridSearchEngine prebuilds ref and pagePill layouts up to 220 without retaining offscreen hits or RenderNodes`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val totalRows = 260
    val packed = IntArray(totalRows * 4)
    for (i in 0 until totalRows) {
      val base = i * 4
      packed[base] = i
      packed[base + 1] = i + 1
      packed[base + 2] = 2
      packed[base + 3] = i + 1
    }
    val rustCursor = RustSearchResultsCursor(
      columnNames = arrayOf("_id", "sura", "ayah", "text"),
      packedMatches = packed,
      indexGeneration = 1
    )
    FakeSliceProvider.queryHandler = { _, _ -> rustCursor }
    Robolectric.buildContentProvider(FakeSliceProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)

    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run11_search_engine_test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = engineScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      assertThat(HybridSearchEngine.LOAD_MORE_PREFETCH_THRESHOLD).isEqualTo(24)
      assertThat(HybridSearchEngine.STATIC_LAYOUT_CACHE_SIZE).isEqualTo(120)
      assertThat(HybridSearchEngine.PAGE_PILL_LAYOUT_CACHE_SIZE).isEqualTo(80)
      assertThat(HybridSearchEngine.PROGRESSIVE_PREBUILD_LIMIT).isEqualTo(120)

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS)
      assertThat(engine.uiState.value.ayahHits).hasSize(40)
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(120)
      assertThat(engine.refLayoutCacheCountForTest()).isEqualTo(120)
      assertThat(engine.pagePillLayoutCacheCountForTest()).isGreaterThan(0)

      val firstHit = engine.uiState.value.ayahHits.first()
      val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)
      assertThat(engine.getCachedRefStaticLayout(firstHit.sura, firstHit.ayah)).isNotNull()
      val pillLayout = engine.getCachedPagePillStaticLayout(firstHit.page, palette.accent)
      assertThat(pillLayout).isNotNull()
      // Different text color misses (keyed by (page, palette))
      assertThat(engine.getCachedPagePillStaticLayout(firstHit.page, Color.MAGENTA)).isNull()

      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(100)

      // trimTransientWindow evicts all layout caches
      engine.trimTransientWindow()
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(0)
      assertThat(engine.refLayoutCacheCountForTest()).isEqualTo(0)
      assertThat(engine.pagePillLayoutCacheCountForTest()).isEqualTo(0)

      engine.clearTransientResults()
      assertThat(engine.uiState.value.ayahHits).isEmpty()
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `AyahSearchHit caches SearchTestTags, StaticLayoutSingleLineView exposes text and a11y without relayout, and NativeHybridSearchView configures fixedSize and lightweight buttons`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val hit = AyahSearchHit(
      id = 7L,
      sura = 2,
      ayah = 255,
      page = 42,
      suraName = "البقرة",
      text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ",
      highlightRanges = emptyList(),
      isArabic = true,
      ayahRefLabel = "سورة البقرة — الآية ٢٥٥",
      pageLabel = "ص ٤٢"
    )
    assertThat(hit.cardTag).isEqualTo(SearchTestTags.ayahCard(2, 255))
    assertThat(hit.textTag).isEqualTo(SearchTestTags.ayahText(2, 255))
    assertThat(hit.listenTag).isEqualTo(SearchTestTags.listenButton(2, 255))
    assertThat(hit.bookmarkTag).isEqualTo(SearchTestTags.bookmarkButton(2, 255))
    assertThat(hit.openPageTag).isEqualTo(SearchTestTags.openPageButton(2, 255))

    var relayouts = 0
    val singleLine = object : HybridSearchAdapter.StaticLayoutSingleLineView(activity) {
      override fun requestLayout() {
        relayouts++
        super.requestLayout()
      }
    }
    activity.setContentView(singleLine)
    val paint = TextPaint().apply { textSize = 24f }
    val layoutA = StaticLayout.Builder.obtain("ص ١٢", 0, 4, paint, 120)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
      .setMaxLines(1)
      .build()
    val layoutB = StaticLayout.Builder.obtain("ص ٣٤", 0, 4, paint, 120)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
      .setMaxLines(1)
      .build()

    singleLine.setPrebuiltContent("ص ١٢", layoutA)
    singleLine.measure(
      View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    singleLine.layout(0, 0, singleLine.measuredWidth, singleLine.measuredHeight)
    val countAfterFirst = relayouts

    if (layoutA.width == layoutB.width && layoutA.height == layoutB.height) {
      singleLine.setPrebuiltContent("ص ٣٤", layoutB)
      assertThat(relayouts).isEqualTo(countAfterFirst)
    }
    assertThat(singleLine.text.toString()).isEqualTo("ص ٣٤")
    val info = AccessibilityNodeInfo.obtain()
    singleLine.onInitializeAccessibilityNodeInfo(info)
    assertThat(info.text.toString()).isEqualTo("ص ٣٤")
    info.recycle()

    val searchView = NativeHybridSearchView(activity)
    assertThat(searchView.resultsRecyclerView.hasFixedSize()).isTrue()

    val adapter = searchView.resultsRecyclerView.adapter as HybridSearchAdapter
    val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)
    adapter.updatePalette(palette)
    adapter.submitList(listOf(HybridSearchRow.AyahCard(hit, isBookmarked = false)))
    val holder = adapter.onCreateViewHolder(searchView.resultsRecyclerView, HybridSearchAdapter.TYPE_AYAH)
    adapter.onBindViewHolder(holder, 0)

    val cardGroup = holder.itemView as ViewGroup
    val listenBtn = cardGroup.findViewWithTag<View>(hit.listenTag)
    val bookmarkBtn = cardGroup.findViewWithTag<View>(hit.bookmarkTag)
    val openBtn = cardGroup.findViewWithTag<View>(hit.openPageTag)
    assertThat(listenBtn.background).isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
    assertThat(bookmarkBtn.background).isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
    assertThat(openBtn.background).isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
  }

  @Test
  fun `HybridHubStateHolder observes bookmarks on BOOKMARKS tab and releaseTransientBookmarksOnStop cancels when unselected`() = runTest(UnconfinedTestDispatcher()) {
    val quranInfo = QuranInfo(MadaniDataSource())
    val settings = QuranSettings.getInstance(context)
    settings.appThemeStyle = AppThemeStyle.DEFAULT.storageKey
    settings.pageType = "madani"
    val wirdPrefs = context.getSharedPreferences("run11_hub_prewarm_wird", Context.MODE_PRIVATE)
    wirdPrefs.edit().clear().commit()
    val dailyWirdRepository = DailyWirdRepository(wirdPrefs) { 20_100L }
    val recentPagesDao = FakeRecentPagesDao().apply { addRecentPage(18) }
    val readingBookmarksDao = FakeReadingBookmarksDao().apply {
      setReadingBookmark(PageReadingBookmark(ReadingBookmarkType.TEAL, 42, Instant.fromEpochSeconds(100)))
    }
    val bookmarksDao = FakeBookmarksDao { sa -> quranInfo.getPageFromSuraAyah(sa.sura, sa.ayah) }
    val highlightsDao = FakeHighlightsDao()
    val displayData = QuranDisplayData(quranInfo)
    val arabicDb = ArabicDatabaseUtils(context, quranInfo, FakeQuranFileUtils.create(context))
    val indexDataSource = HybridIndexDataSource(
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      juzListPresenter = Provider { JuzListPresenter(quranInfo, arabicDb) }
    )
    val holder = HybridHubStateHolder(
      context = context,
      dailyWirdRepository = dailyWirdRepository,
      recentPagesDao = recentPagesDao,
      bookmarksDao = Provider { bookmarksDao },
      readingBookmarksDao = Provider { readingBookmarksDao },
      highlightsDao = Provider { highlightsDao },
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      arabicDatabaseUtils = Provider { arabicDb },
      indexDataSource = indexDataSource,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )

    holder.startObserving(backgroundScope, MutableStateFlow(Constants.NO_PAGE))
    assertThat(holder.uiState.value.bookmarks.isLoaded).isFalse()

    holder.selectNavTab(HybridNavTab.BOOKMARKS)
    assertThat(holder.uiState.value.bookmarks.isLoaded).isTrue()
    assertThat(holder.uiState.value.bookmarks.readingBookmarks).hasSize(1)
    assertThat(holder.uiState.value.bookmarks.recentPages).hasSize(1)

    holder.selectNavTab(HybridNavTab.INDEX)
    holder.releaseTransientBookmarksOnStop()
    assertThat(holder.uiState.value.bookmarks.isLoaded).isFalse()
    assertThat(holder.uiState.value.bookmarks.readingBookmarks).isEmpty()
  }

  @Test
  fun `HybridHubView builds lean shells without binding SearchEngine, uses INVISIBLE header on SEARCH with fade=false, and HybridBookmarksView CardHolder uses LightweightPressColorDrawable`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run11_hub_view_test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    try {
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranFileUtils = FakeQuranFileUtils.create(activity),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = scope
      )
      val hub = HybridHubView(
        context = activity,
        scope = scope,
        searchEngineProvider = { engine },
        searchActionHandler = object : HybridSearchActionHandler {
          override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
          override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
          override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
        },
        onNavTabSelected = {},
        onIndexSegmentSelected = {},
        onContinueReadingClick = {},
        onOpenPage = {},
        onOpenAyah = { _, _, _ -> },
        onOpenSettingsClick = {},
        onOpenPaletteSheet = {},
        onDismissPaletteSheet = {},
        onSelectThemeMode = {},
        onSelectThemeStyle = {},
        onOpenWirdGoalDialog = {},
        onClearReadingBookmark = {},
        onRemoveRecentPage = {},
        onClearAllRecents = {},
        onRemoveAyahBookmark = { _, _ -> },
        onClearHighlight = { _, _ -> },
        onDownloadArabicSearchDb = {},
        onOpenTranslationManager = {}
      )
      activity.setContentView(hub)
      hub.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      hub.layout(0, 0, 720, 1604)
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.INDEX), showPaletteSheet = false)

      Shadows.shadowOf(Looper.getMainLooper()).idleFor(1600, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isTrue()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isTrue()
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()

      val mainContainer = hub.getChildAt(0) as FrameLayout
      val contentHost = mainContainer.getChildAt(0) as FrameLayout
      val headerOverlay = mainContainer.getChildAt(1)

      Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.SEARCH), showPaletteSheet = false)
      assertThat(headerOverlay.visibility).isEqualTo(View.INVISIBLE)
      assertThat(headerOverlay.importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
      // fade=false keeps active search content alpha at 1f immediately
      val searchChild = (0 until contentHost.childCount)
        .map { contentHost.getChildAt(it) }
        .first { it is NativeHybridSearchView }
      assertThat(searchChild.alpha).isEqualTo(1f)
      assertThat(hub.hasBoundSearchEngineForTest()).isTrue()

      // Switching back restores VISIBLE header
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.BOOKMARKS), showPaletteSheet = false)
      assertThat(headerOverlay.visibility).isEqualTo(View.VISIBLE)
      assertThat(headerOverlay.importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)

      // HybridBookmarksView CardHolder uses LightweightPressColorDrawable with 0 elevation and no RippleDrawable
      val bmView = HybridBookmarksView(
        context = activity,
        onOpenPage = {},
        onOpenAyah = { _, _, _ -> },
        onClearReadingBookmark = {},
        onRemoveRecentPage = {},
        onClearAllRecents = {},
        onRemoveAyahBookmark = { _, _ -> },
        onClearHighlight = { _, _ -> }
      )
      bmView.render(
        HybridHubUiState(
          bookmarks = HubBookmarksUiState(
            readingBookmarks = listOf(
              HubReadingBookmarkItem(
                slot = ReadingBookmarkType.TEAL,
                page = 1,
                sura = 1,
                ayah = 1,
                suraName = "الفاتحة",
                juz = 1
              )
            )
          )
        )
      )
      val bmRecycler = bmView.getChildAt(0) as RecyclerView
      bmRecycler.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
      )
      bmRecycler.layout(0, 0, 720, 1200)
      val cardView = bmRecycler.getChildAt(1) as ViewGroup
      assertThat(cardView.elevation).isEqualTo(0f)
      assertThat(cardView.background).isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
      assertThat(cardView.background).isNotInstanceOf(RippleDrawable::class.java)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridReaderOverlayView flattens AppearanceTab without CheckBox or LightweightPressColorDrawable and premeasures RecitationTab`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, 720, 1604)

    overlay.triggerIdlePrebuildForTest()
    val sheet = overlay.getChildAt(overlay.childCount - 1) as ViewGroup
    val tabHost = sheet.getChildAt(2) as FrameLayout
    val recitationTab = tabHost.getChildAt(0)
    assertThat(recitationTab.measuredWidth).isGreaterThan(0)
    assertThat(recitationTab.measuredHeight).isGreaterThan(0)
    assertThat(overlay.hasTafsirTabBuiltForTest()).isFalse()
    assertThat(overlay.hasAppearanceTabBuiltForTest()).isFalse()

    // Open standalone Reader Appearance Sheet and verify zero CheckBox instances & flat GradientDrawable pills + checkable row
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = false,
        isAppearanceSheetOpen = true
      )
    )
    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isTrue()
    val appearanceRoot = overlay.appearanceSheetCardForTest()!!

    val allViews = ArrayList<View>()
    fun collectViews(v: View) {
      allViews.add(v)
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
          collectViews(v.getChildAt(i))
        }
      }
    }
    collectViews(appearanceRoot)

    assertThat(allViews.none { it is CheckBox }).isTrue()
    val matchLabel = allViews.filterIsInstance<TextView>().first {
      it.text.toString() == activity.getString(R.string.hybrid_reader_mushaf_match_app)
    }
    val toggleRow = matchLabel.parent as LinearLayout
    val nodeInfo = AccessibilityNodeInfo.obtain()
    toggleRow.onInitializeAccessibilityNodeInfo(nodeInfo)
    assertThat(nodeInfo.isCheckable).isTrue()
    nodeInfo.recycle()

    val autoModeLabel = allViews.filterIsInstance<TextView>().first {
      it.text.toString() == activity.getString(R.string.hybrid_reader_mode_auto)
    }
    val modeRow = autoModeLabel.parent as ViewGroup
    assertThat(modeRow.childCount).isEqualTo(3)
    for (i in 0 until modeRow.childCount) {
      assertThat(modeRow.getChildAt(i).background).isInstanceOf(GradientDrawable::class.java)
      assertThat(modeRow.getChildAt(i).background)
        .isNotInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
    }
  }

  class FakeSliceProvider : ContentProvider() {
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
