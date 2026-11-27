package com.quran.labs.androidquran.hybrid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.QuranLineByLinePresenter
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.reader.TafsirDisplayState
import com.quran.labs.androidquran.hybrid.reader.TafsirLoadState
import com.quran.labs.androidquran.hybrid.reader.TafsirSourceItem
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.PostDestroyMemoryReclaimer
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run14OptimizationFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    PostDestroyMemoryReclaimer.resetForTest()
    FakeSliceProvider.reset()
  }

  @After
  fun tearDown() {
    PostDestroyMemoryReclaimer.resetForTest()
    FakeSliceProvider.reset()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    @Suppress("DEPRECATION")
    ShadowChoreographer.setPostFrameCallbackDelay(1)
  }

  @Test
  fun `FloatingBottomNavView never toggles isFakeBoldText across tab switches`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val nav = FloatingBottomNavView(activity)
    nav.applyPalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))

    val pillContainer = nav.getChildAt(0) as ViewGroup
    val labels = (0 until pillContainer.childCount)
      .mapNotNull { pillContainer.getChildAt(it) as? LinearLayout }
      .map { it.getChildAt(1) as TextView }
    assertThat(labels).hasSize(4)

    for (tab in HybridNavTab.entries) {
      nav.setSelectedTab(tab)
      labels.forEach { label ->
        assertThat(label.paint.isFakeBoldText).isFalse()
      }
    }
  }

  @Test
  fun `HybridHubView prebuilds unbound INVISIBLE shells at 1500ms without offscreen alpha warmup`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run14_hub_post_draw_test", Context.MODE_PRIVATE)
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

      val mainContainer = hub.getChildAt(0) as FrameLayout
      val contentHost = mainContainer.getChildAt(0) as FrameLayout

      // Not built before 1500ms
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(1200, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isFalse()

      // Built at 1500ms as unbound INVISIBLE shells with alpha 1f
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isTrue()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isTrue()
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()

      val searchChild = (0 until contentHost.childCount)
        .map { contentHost.getChildAt(it) }
        .first { it is NativeHybridSearchView } as NativeHybridSearchView
      val bookmarksChild = (0 until contentHost.childCount)
        .map { contentHost.getChildAt(it) }
        .first { it !== hub.indexRecyclerView && it !== searchChild }

      assertThat(searchChild.visibility).isEqualTo(View.INVISIBLE)
      assertThat(bookmarksChild.visibility).isEqualTo(View.INVISIBLE)
      assertThat(searchChild.alpha).isEqualTo(1f)
      assertThat(bookmarksChild.alpha).isEqualTo(1f)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridReaderOverlayView uses transient HARDWARE layer during slide, staggers prebuilds after settle without 0_01f warmup, and removes fakeBold`() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
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
    val tabRail = sheet.getChildAt(1) as ViewGroup
    val tabHost = sheet.getChildAt(2) as FrameLayout

    // Open Study Drawer on RECITATION
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.RECITATION,
        tafsirState = TafsirDisplayState(loadState = TafsirLoadState.LOADING, sura = 1, ayah = 1)
      )
    )
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    for (i in 0 until tabRail.childCount) {
      val tv = tabRail.getChildAt(i) as TextView
      assertThat(tv.paint.isFakeBoldText).isFalse()
    }

    // Pre-draw enables transient LAYER_TYPE_HARDWARE strictly during slide
    sheet.viewTreeObserver.dispatchOnPreDraw()
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)

    // Animation finishes (~260ms) -> resets to LAYER_TYPE_NONE; staggered prebuilds wait +180ms/+300ms
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(overlay.hasTafsirTabBuiltForTest()).isFalse()

    // Complete post-open Tafsir view shell prebuild (+180ms); standalone Appearance Sheet stays lazy
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    assertThat(overlay.hasTafsirTabBuiltForTest()).isTrue()
    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isFalse()

    assertThat(tabHost.childCount).isEqualTo(2)
    val tabChildren = (0 until tabHost.childCount).map { tabHost.getChildAt(it) }
    val tafsirTab = tabChildren.first { it is LinearLayout }
    val recitationTab = tabChildren.first { it !== tafsirTab }

    assertThat(recitationTab.visibility).isEqualTo(View.VISIBLE)
    assertThat(tafsirTab.visibility).isEqualTo(View.INVISIBLE)
    assertThat(tafsirTab.alpha).isEqualTo(1f)

    // Switch to TAFSIR with sources and verify zero fakeBold on 2-tab rail
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          loadState = TafsirLoadState.LOADED,
          sura = 1,
          ayah = 1,
          suraName = "الفاتحة",
          availableSources = listOf(
            TafsirSourceItem("ar.muyassar", "التفسير الميسر", isArabic = true),
            TafsirSourceItem("ar.jalalayn", "الجلالين", isArabic = true)
          ),
          selectedFilename = "ar.muyassar",
          fullText = "تفسير"
        )
      )
    )
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(tabRail.childCount).isEqualTo(2)
    for (i in 0 until tabRail.childCount) {
      val tv = tabRail.getChildAt(i) as TextView
      assertThat(tv.paint.isFakeBoldText).isFalse()
    }

    // Close drawer uses transient LAYER_TYPE_HARDWARE during slide and restores LAYER_TYPE_NONE + INVISIBLE
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = false
      )
    )
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(sheet.visibility).isEqualTo(View.INVISIBLE)

    // Standalone Appearance Sheet contains HybridBrightnessSliderView and uses transient HARDWARE layer
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = false,
        isAppearanceSheetOpen = true
      )
    )
    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isTrue()
    val appCard = overlay.appearanceSheetCardForTest()!!
    fun containsSlider(v: View): Boolean {
      if (v is HybridReaderOverlayView.HybridBrightnessSliderView) return true
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
          if (containsSlider(v.getChildAt(i))) return true
        }
      }
      return false
    }
    assertThat(containsSlider(appCard)).isTrue()
    appCard.viewTreeObserver.dispatchOnPreDraw()
    assertThat(appCard.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    assertThat(appCard.layerType).isEqualTo(View.LAYER_TYPE_NONE)
  }

  @Test
  fun `QuranDataActivity onDestroy clears window background`() {
    val controller = Robolectric.buildActivity(QuranDataActivity::class.java)
    val activity = controller.create().get()
    activity.finish()
    controller.destroy()
    assertThat(activity.window.decorView.background).isNull()
  }

  @Test
  fun `StaticLayoutVerseView avoids requestLayout before layout, NativeHybridSearchView restores lean pool size 12, and reclaimer uses 450ms interval`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    var relayouts = 0
    val verseView = object : HybridSearchAdapter.StaticLayoutVerseView(activity) {
      override fun requestLayout() {
        relayouts++
        super.requestLayout()
      }
    }
    val paint = TextPaint().apply { textSize = 32f }
    val layout = StaticLayout.Builder.obtain("بِسْمِ اللَّهِ", 0, 12, paint, 400)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(6)
      .build()

    // While detached / not laid out (GapWorker/scrap bind), setPrebuiltContent must NOT call requestLayout()
    val relayoutsBeforeBind = relayouts
    verseView.setPrebuiltContent("بِسْمِ اللَّهِ", layout)
    assertThat(relayouts).isEqualTo(relayoutsBeforeBind)

    val searchView = NativeHybridSearchView(activity)
    assertThat(searchView.resultsRecyclerView.itemDecorationCount).isEqualTo(0)
    // Verify lean pool size accepts 12 TYPE_AYAH views and caps at 12
    val pool = searchView.resultsRecyclerView.recycledViewPool
    val adapter = searchView.resultsRecyclerView.adapter as HybridSearchAdapter
    adapter.updatePalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
    repeat(13) {
      pool.putRecycledView(adapter.createViewHolder(searchView.resultsRecyclerView, HybridSearchAdapter.TYPE_AYAH))
    }
    assertThat(pool.getRecycledViewCount(HybridSearchAdapter.TYPE_AYAH)).isEqualTo(12)

    // Verify MIN_GC_INTERVAL_MS restored to 450L
    assertThat(PostDestroyMemoryReclaimer.MIN_GC_INTERVAL_MS).isEqualTo(450L)
    val calls = AtomicInteger(0)
    var now = 1000L
    val latch1 = CountDownLatch(1)
    PostDestroyMemoryReclaimer.schedulePostDestroyCleanup(
      delayMs = 0L,
      nowProvider = { now },
      gcAction = {
        calls.incrementAndGet()
        latch1.countDown()
      }
    )
    assertThat(latch1.await(1, TimeUnit.SECONDS)).isTrue()
    now = 1460L // > 450ms
    val latch2 = CountDownLatch(1)
    PostDestroyMemoryReclaimer.schedulePostDestroyCleanup(
      delayMs = 0L,
      nowProvider = { now },
      gcAction = {
        calls.incrementAndGet()
        latch2.countDown()
      }
    )
    assertThat(latch2.await(1, TimeUnit.SECONDS)).isTrue()
    assertThat(calls.get()).isEqualTo(2)
  }

  @Test
  fun `HybridSearchEngine progressive prebuild populates 120 layouts directly from PendingAyahSource without extra AyahSearchHits`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val totalRows = 250
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
    val prefs = context.getSharedPreferences("run14_search_prebuild_test", Context.MODE_PRIVATE)
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

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS)
      assertThat(engine.uiState.value.ayahHits).hasSize(40)
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(120)
      assertThat(engine.refLayoutCacheCountForTest()).isEqualTo(120)
      assertThat(engine.pagePillLayoutCacheCountForTest()).isGreaterThan(0)
      assertThat(engine.getCachedStaticLayout(2, 120)).isNotNull()
      assertThat(engine.getCachedRefStaticLayout(2, 120)).isNotNull()
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `QuranLineByLinePresenter keeps scratch bitmap pooled across rapid flips and clears on trim registry`() {
    QuranLineByLinePresenter.clearScratchBitmapPool()
    try {
      val first = QuranLineByLinePresenter.acquireScratchBitmap(64, 64)
      QuranLineByLinePresenter.releaseScratchBitmap(first)
      assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isTrue()

      Thread.sleep(550L)
      assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isTrue()

      val reused = QuranLineByLinePresenter.acquireScratchBitmap(64, 64)
      assertThat(reused).isSameInstanceAs(first)

      QuranLineByLinePresenter.releaseScratchBitmap(reused)
      BitmapPoolTrimRegistry.clearAll()
      assertThat(QuranLineByLinePresenter.hasPooledScratchBitmapForTest()).isFalse()
      assertThat(reused.isRecycled).isTrue()
    } finally {
      QuranLineByLinePresenter.clearScratchBitmapPool()
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
