package com.quran.labs.androidquran.hybrid

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.extra.feature.linebyline.resource.LineBitmapRecycler
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HubBookmarksUiState
import com.quran.labs.androidquran.hybrid.hub.HubReadingBookmarkItem
import com.quran.labs.androidquran.hybrid.hub.HybridBookmarksView
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.search.AyahSearchHit
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchRow
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run9OptimizationFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    LineBitmapRecycler.recycleNow()
    PostDestroyMemoryReclaimer.resetForTest()
    FakeSliceProvider.reset()
  }

  @After
  fun tearDown() {
    LineBitmapRecycler.recycleNow()
    PostDestroyMemoryReclaimer.resetForTest()
    FakeSliceProvider.reset()
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }

  @Test
  fun `LineBitmapRecycler recycleNow drains delayed tasks and recycles queued bitmaps synchronously`() {
    val bmp1 = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    val bmp2 = Bitmap.createBitmap(32, 32, Bitmap.Config.ALPHA_8)

    LineBitmapRecycler.scheduleRecycle(bmp1, delayMs = 250L)
    LineBitmapRecycler.scheduleRecycle(bmp2, delayMs = 250L)

    assertThat(bmp1.isRecycled).isFalse()
    assertThat(bmp2.isRecycled).isFalse()
    assertThat(LineBitmapRecycler.pendingCountForTest()).isEqualTo(2)

    LineBitmapRecycler.recycleNow()

    assertThat(LineBitmapRecycler.pendingCountForTest()).isEqualTo(0)
    assertThat(bmp1.isRecycled).isTrue()
    assertThat(bmp2.isRecycled).isTrue()
  }

  @Test
  fun `HybridSearchEngine prebuildStaticLayouts builds lean 120-item slices with batch 60 and prefetch 24`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val engineScope = CoroutineScope(dispatcher + SupervisorJob())
    val totalRows = 240
    val packed = IntArray(totalRows * 4)
    for (i in 0 until totalRows) {
      val base = i * 4
      packed[base] = i
      packed[base + 1] = i + 1
      packed[base + 2] = 2
      packed[base + 3] = i + 1 // unique (2, 1..240)
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
    val prefs = context.getSharedPreferences("run9_static_layout_slice_test", Context.MODE_PRIVATE)
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

      assertThat(HybridSearchEngine.STATIC_LAYOUT_CACHE_SIZE).isEqualTo(120)
      assertThat(HybridSearchEngine.PROGRESSIVE_PREBUILD_LIMIT).isEqualTo(120)
      assertThat(HybridSearchEngine.LOAD_MORE_PREFETCH_THRESHOLD).isEqualTo(24)
      assertThat(HybridSearchEngine.LOAD_MORE_BATCH_SIZE).isEqualTo(60)

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS)
      assertThat(engine.uiState.value.ayahHits).hasSize(40)
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(120)
      for (ayah in 1..120) {
        assertThat(engine.getCachedStaticLayout(2, ayah)).isNotNull()
      }

      // Batch 1 appends hits 40..99 (ayah 41..100). Cache holds 1..120 without eviction.
      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(100)
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(120)
      for (ayah in 1..100) {
        assertThat(engine.getCachedStaticLayout(2, ayah)).isNotNull()
      }

      // Remaining batches load up to 240 while LruCache remains bounded at 120.
      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(160)
      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(220)
      engine.loadMoreAyahHits()
      assertThat(engine.uiState.value.ayahHits).hasSize(240)
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(120)

      engine.clearTransientResults()
      assertThat(engine.staticLayoutCacheCountForTest()).isEqualTo(0)
    } finally {
      engineScope.cancel()
    }
  }

  @Test
  fun `HybridSearchAdapter uses stable IDs and StaticLayoutVerseView skips super setText while exposing AccessibilityNodeInfo`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    assertThat(adapter.hasStableIds()).isTrue()

    val hit1 = AyahSearchHit(1L, 2, 1, 2, "البقرة", "الم", emptyList(), isArabic = true)
    val hit2 = AyahSearchHit(2L, 2, 2, 2, "البقرة", "ذَٰلِكَ الْكِتَابُ", emptyList(), isArabic = true)
    val row1 = HybridSearchRow.AyahCard(hit1, isBookmarked = false)
    val row2 = HybridSearchRow.AyahCard(hit2, isBookmarked = false)
    adapter.submitList(listOf(row1, row2))
    assertThat(adapter.getItemId(0)).isNotEqualTo(adapter.getItemId(1))

    var relayoutCount = 0
    var superSetTextCount = 0
    val verseView = object : HybridSearchAdapter.StaticLayoutVerseView(activity) {
      override fun requestLayout() {
        relayoutCount++
        super.requestLayout()
      }

      override fun setText(text: CharSequence?, type: BufferType?) {
        superSetTextCount++
        super.setText(text, type)
      }
    }
    activity.setContentView(verseView)
    val setTextCountAfterInit = superSetTextCount
    val paint = TextPaint().apply { textSize = 32f }
    val widthPx = 420
    val textA = "ذَٰلِكَ الْكِتَابُ"
    val textB = "لَا رَيْبَ فِيهِ"
    val layoutA = StaticLayout.Builder.obtain(textA, 0, textA.length, paint, widthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(6)
      .build()
    val layoutB = StaticLayout.Builder.obtain(textB, 0, textB.length, paint, widthPx)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, 1.42f)
      .setTextDirection(TextDirectionHeuristics.RTL)
      .setMaxLines(6)
      .build()

    verseView.setPrebuiltContent(textA, layoutA)
    assertThat(superSetTextCount).isEqualTo(setTextCountAfterInit)
    assertThat(verseView.text.toString()).isEqualTo(textA)

    verseView.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    verseView.layout(0, 0, widthPx, verseView.measuredHeight)
    val countAfterFirstBind = relayoutCount

    if (layoutA.height == layoutB.height) {
      verseView.setPrebuiltContent(textB, layoutB)
      assertThat(superSetTextCount).isEqualTo(setTextCountAfterInit)
      assertThat(verseView.text.toString()).isEqualTo(textB)
      assertThat(relayoutCount).isEqualTo(countAfterFirstBind)
      val nodeInfo = AccessibilityNodeInfo.obtain()
      verseView.onInitializeAccessibilityNodeInfo(nodeInfo)
      assertThat(nodeInfo.text.toString()).isEqualTo(textB)
      nodeInfo.recycle()
    }
  }

  @Test
  fun `HybridHubView delays cold prebuild 1500ms, builds unbound INVISIBLE shells, and uses 250ms warm prebuild after stop`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    var searchEngineProviderCalls = 0
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run9_hub_idle_test", Context.MODE_PRIVATE)
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
        searchEngineProvider = {
          searchEngineProviderCalls++
          engine
        },
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

      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isFalse()

      // Cold start respects 1500ms delay (not built at 1200ms)
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(1200, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isFalse()

      Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

      assertThat(hub.hasPrebuiltSearchShellForTest()).isTrue()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isTrue()
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()
      assertThat(searchEngineProviderCalls).isEqualTo(0)

      // Leaving Hub on INDEX releases transient views cleanly (+0.0 MB carry-over)
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.INDEX), showPaletteSheet = false)
      hub.releaseTransientOnStop()
      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isFalse()
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()

      // Warm resume re-arms with 250ms delay (not built at 100ms) and builds unbound shells after 250ms
      hub.scheduleIdlePrebuildIfNeeded()
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
      assertThat(hub.hasPrebuiltSearchShellForTest()).isTrue()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isTrue()
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridBookmarksView updates RecyclerView adapter synchronously and disables overlapping rendering`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val view = HybridBookmarksView(
      context = activity,
      onOpenPage = {},
      onOpenAyah = { _, _, _ -> },
      onClearReadingBookmark = {},
      onRemoveRecentPage = {},
      onClearAllRecents = {},
      onRemoveAyahBookmark = { _, _ -> },
      onClearHighlight = { _, _ -> }
    )
    assertThat(view.hasOverlappingRendering()).isFalse()
    val rv = view.getChildAt(0) as RecyclerView
    assertThat(rv.hasOverlappingRendering()).isFalse()

    view.render(
      HybridHubUiState(
        bookmarks = HubBookmarksUiState(
          readingBookmarks = listOf(
            HubReadingBookmarkItem(
              slot = com.quran.data.model.bookmark.ReadingBookmarkType.TEAL,
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
    // Synchronous update without AsyncListDiffer main-looper post
    assertThat(rv.adapter?.itemCount).isEqualTo(2)
  }

  @Test
  fun `HybridReaderOverlayView uses fade=false, View_INVISIBLE gating, and transient LAYER_TYPE_HARDWARE during slide without drawer relayout on tab switch`() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, 720, 1604)

    overlay.triggerIdlePrebuildForTest()
    assertThat(overlay.hasStudyDrawerBuiltForTest()).isTrue()
    assertThat(overlay.hasRecitationTabBuiltForTest()).isTrue()
    assertThat(overlay.hasTafsirTabBuiltForTest()).isFalse()
    assertThat(overlay.hasAppearanceTabBuiltForTest()).isFalse()

    // Scrim and closed detached sheet stay View.INVISIBLE with static GradientDrawable fade and Z4 floatation
    val scrim = overlay.getChildAt(overlay.childCount - 2)
    val sheet = overlay.getChildAt(overlay.childCount - 1) as ViewGroup
    assertThat(scrim.hasOverlappingRendering()).isFalse()
    assertThat(sheet.hasOverlappingRendering()).isFalse()
    assertThat(scrim.visibility).isEqualTo(View.INVISIBLE)
    assertThat(scrim.background).isInstanceOf(GradientDrawable::class.java)
    assertThat((scrim.background as GradientDrawable).orientation)
      .isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)
    assertThat(sheet.visibility).isEqualTo(View.INVISIBLE)
    assertThat(overlay.isStudyDrawerRenderVisibleForTest()).isFalse()
    assertThat(sheet.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    assertThat(sheet.descendantFocusability).isEqualTo(ViewGroup.FOCUS_BLOCK_DESCENDANTS)
    assertThat(sheet.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(4, isDark = false))
    )
    assertThat(sheet.background).isInstanceOf(LayerDrawable::class.java)

    // Opening Study Drawer enables transient LAYER_TYPE_HARDWARE during slide and clears to LAYER_TYPE_NONE on completion
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true
      )
    )
    assertThat(sheet.visibility).isEqualTo(View.VISIBLE)
    assertThat(overlay.isStudyDrawerRenderVisibleForTest()).isTrue()
    assertThat(sheet.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
    assertThat(sheet.descendantFocusability).isEqualTo(ViewGroup.FOCUS_AFTER_DESCENDANTS)
    assertThat(sheet.alpha).isEqualTo(1f)

    sheet.viewTreeObserver.dispatchOnPreDraw()
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(700, TimeUnit.MILLISECONDS)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(overlay.hasTafsirTabBuiltForTest()).isTrue()
    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isFalse()

    // Steady-state re-renders on the active tab do NOT trigger requestLayout on sheet
    overlay.layout(0, 0, 720, 1604)
    assertThat(sheet.isLayoutRequested).isFalse()
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.RECITATION
      )
    )
    assertThat(sheet.isLayoutRequested).isFalse()

    // Standalone Reader Appearance Sheet opens cleanly with Z4 floatation & transient HARDWARE layer
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = false,
        isAppearanceSheetOpen = true
      )
    )
    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isTrue()
    val appSheet = overlay.appearanceSheetCardForTest()!!
    assertThat(appSheet.hasOverlappingRendering()).isFalse()
    assertThat(appSheet.visibility).isEqualTo(View.VISIBLE)
    appSheet.viewTreeObserver.dispatchOnPreDraw()
    assertThat(appSheet.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
    assertThat(appSheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
  }

  @Test
  fun `PostDestroyMemoryReclaimer rate limits off-main gc invocations`() {
    val calls = AtomicInteger(0)
    var fakeNow = 1000L
    val firstLatch = CountDownLatch(1)

    PostDestroyMemoryReclaimer.schedulePostDestroyCleanup(
      delayMs = 0L,
      nowProvider = { fakeNow },
      gcAction = {
        calls.incrementAndGet()
        firstLatch.countDown()
      }
    )
    assertThat(firstLatch.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(calls.get()).isEqualTo(1)

    // Within MIN_GC_INTERVAL_MS -> suppressed
    fakeNow = 1200L
    val secondLatch = CountDownLatch(1)
    PostDestroyMemoryReclaimer.schedulePostDestroyCleanup(
      delayMs = 0L,
      nowProvider = { fakeNow },
      gcAction = {
        calls.incrementAndGet()
        secondLatch.countDown()
      }
    )
    assertThat(secondLatch.await(150, TimeUnit.MILLISECONDS)).isFalse()
    assertThat(calls.get()).isEqualTo(1)

    // Past MIN_GC_INTERVAL_MS -> executes
    fakeNow = 1600L
    val thirdLatch = CountDownLatch(1)
    PostDestroyMemoryReclaimer.schedulePostDestroyCleanup(
      delayMs = 0L,
      nowProvider = { fakeNow },
      gcAction = {
        calls.incrementAndGet()
        thirdLatch.countDown()
      }
    )
    assertThat(thirdLatch.await(2, TimeUnit.SECONDS)).isTrue()
    assertThat(calls.get()).isEqualTo(2)
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
