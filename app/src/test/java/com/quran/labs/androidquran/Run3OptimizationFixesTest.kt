package com.quran.labs.androidquran

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.search.RustSearchResultsCursor
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.drawing.R as DrawingR
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.extra.feature.linebyline.resource.ImageBitmapUtil
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HubIndexSegment
import com.quran.labs.androidquran.hybrid.hub.HybridIndexAdapter
import com.quran.labs.androidquran.hybrid.hub.HybridIndexRow
import com.quran.labs.androidquran.hybrid.hub.SuraIndexItem
import com.quran.labs.androidquran.hybrid.reader.AyahOverlayBounds
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.hybrid.search.AyahSearchHit
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchRow
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.search.SearchTestTags
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run3OptimizationFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    BitmapPoolTrimRegistry.clearAll()
    FakeSearchProvider.reset()
  }

  @After
  fun tearDown() {
    BitmapPoolTrimRegistry.clearAll()
    FakeSearchProvider.reset()
  }

  @Test
  fun `ImageBitmapUtil clears cached bitmaps via BitmapPoolTrimRegistry and schedules recycle`() {
    val util = ImageBitmapUtil()
    val hdrBitmap = util.alpha8Bitmap(context, DrawingR.drawable.chapter_hdr)
    assertThat(hdrBitmap.isRecycled).isFalse()
    assertThat(util.getCachedAlpha8Bitmap(DrawingR.drawable.chapter_hdr)).isSameInstanceAs(hdrBitmap)

    BitmapPoolTrimRegistry.clearAll()
    assertThat(util.getCachedAlpha8Bitmap(DrawingR.drawable.chapter_hdr)).isNull()

    val deadline = System.currentTimeMillis() + 1500L
    while (!hdrBitmap.isRecycled && System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
    }
    assertThat(hdrBitmap.isRecycled).isTrue()
  }

  @Test
  @Config(qualifiers = "ar-w411dp-h915dp-xhdpi")
  fun `HybridReaderOverlayView floating ayah bar renders all 5 buttons unclipped and tracks caret on edge clamp`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)

    val widthPx = 720
    val heightPx = 1604
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, widthPx, heightPx)

    // Edge-clamped ayah near left edge (x = 20..80)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        selectedSuraName = "سورة الفاتحة",
        selectedStart = SuraAyah(1, 7),
        selectedEnd = SuraAyah(1, 7),
        ayahOverlayBounds = AyahOverlayBounds(
          firstLineLeft = 20f,
          firstLineTop = 500f,
          firstLineRight = 80f,
          firstLineBottom = 550f,
          lastLineLeft = 20f,
          lastLineTop = 500f,
          lastLineRight = 80f,
          lastLineBottom = 550f
        )
      )
    )
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, widthPx, heightPx)

    val expectedLabels = listOf(
      context.getString(R.string.hybrid_reader_action_listen),
      context.getString(R.string.hybrid_reader_action_tafsir),
      context.getString(R.string.hybrid_reader_action_bookmark),
      context.getString(R.string.hybrid_reader_action_copy),
      context.getString(R.string.hybrid_reader_action_share)
    )
    val allTexts = collectTextViews(overlay)
    expectedLabels.forEach { label ->
      val tv = allTexts.firstOrNull { it.text.toString() == label }
      assertThat(tv).isNotNull()
      assertThat(isEffectivelyVisible(tv!!, overlay)).isTrue()
      assertThat(tv.width).isGreaterThan(0)
    }

    // Verify ayahBarWrapper stayed inside [12dp .. widthPx - 12dp] and caret shifted toward anchorMidX (50f)
    val shareBtnParent = allTexts.first { it.text.toString() == expectedLabels.last() }.parent as View
    val actionPill = shareBtnParent.parent as View
    val wrapper = actionPill.parent as ViewGroup
    assertThat(wrapper.left).isAtLeast(1)
    assertThat(wrapper.right).isAtMost(widthPx)
    assertThat(actionPill.width).isGreaterThan(0)
    val visibleCarets = (0 until wrapper.childCount)
      .map { wrapper.getChildAt(it) }
      .filter { it.visibility == View.VISIBLE && it !== actionPill && it !is TextView }
    assertThat(visibleCarets).hasSize(1)
    assertThat(visibleCarets.single().translationX).isLessThan(0f)
  }

  @Test
  fun `HybridIndexAdapter submitRows uses prefix append fast path cleanly`() {
    val adapter = HybridIndexAdapter(
      onContinueReading = {},
      onOpenWirdGoal = {},
      onSegmentSelected = {},
      onOpenPage = {}
    )
    val initialSuras = (1..12).map { s ->
      HybridIndexRow.SurahRow(SuraIndexItem(s, "سورة $s", "مكية", s, 1, isMakki = true, ayahCount = 7))
    }
    val allSuras = (1..114).map { s ->
      HybridIndexRow.SurahRow(SuraIndexItem(s, "سورة $s", "مكية", s, 1, isMakki = true, ayahCount = 7))
    }
    val hero = HybridIndexRow.Hero(DailyWirdState(), "الفاتحة", 1)
    val seg = HybridIndexRow.SegmentBar(HubIndexSegment.SURAS)

    adapter.submitRows(listOf(hero, seg) + initialSuras, HubIndexSegment.SURAS)
    assertThat(adapter.itemCount).isEqualTo(14)

    adapter.submitRows(listOf(hero, seg) + allSuras, HubIndexSegment.SURAS)
    assertThat(adapter.itemCount).isEqualTo(116)

    // Verify Hero-only update fast path + daily_wird_progress ID on HeroViewHolder
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    adapter.updatePalette(HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false))
    val parent = android.widget.FrameLayout(activity)
    val heroVh = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
    adapter.onBindViewHolder(heroVh, 0)
    val wirdProgress = heroVh.itemView.findViewById<TextView>(R.id.daily_wird_progress)
    assertThat(wirdProgress).isNotNull()

    val updatedHero = hero.copy(dailyWird = DailyWirdState(pagesReadToday = 3, goalPages = 15))
    adapter.submitRows(listOf(updatedHero, seg) + allSuras, HubIndexSegment.SURAS)
    adapter.onBindViewHolder(heroVh, 0)
    assertThat(wirdProgress.text.toString()).contains(QuranUtils.getLocalizedNumber(15))
  }

  @Test
  @Config(qualifiers = "ar-w411dp-h915dp-xhdpi")
  fun `HybridReaderOverlayView places ayah bar BELOW_AYAH below Ayah 7 above audio capsule and keeps study drawer scrim transparent`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)

    val widthPx = 720
    val heightPx = 1604
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, widthPx, heightPx)

    // Simulate Ayah 7 ending at y=1205 with audio capsule at bottom (~1388+)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        selectedSuraName = "سورة الفاتحة",
        selectedStart = SuraAyah(1, 7),
        selectedEnd = SuraAyah(1, 7),
        ayahOverlayBounds = AyahOverlayBounds(
          firstLineLeft = 100f,
          firstLineTop = 1080f,
          firstLineRight = 620f,
          firstLineBottom = 1135f,
          lastLineLeft = 180f,
          lastLineTop = 1145f,
          lastLineRight = 540f,
          lastLineBottom = 1205f
        )
      )
    )
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, widthPx, heightPx)

    val expectedListen = context.getString(R.string.hybrid_reader_action_listen)
    val allTexts = collectTextViews(overlay)
    val listenTv = allTexts.first { it.text.toString() == expectedListen }
    val actionPill = (listenTv.parent as View).parent as View
    val wrapper = actionPill.parent as ViewGroup

    // Verify BELOW_AYAH placement: wrapper sits below 1205px with zero overlap on Ayah 7
    assertThat(wrapper.top).isGreaterThan(1205)
    assertThat(wrapper.childCount).isEqualTo(4)
    // Order for BELOW_AYAH: [ayahCaretTop (VISIBLE), ayahActionPill, ayahSelectionTagPill, ayahCaretBottom (GONE)]
    val caretTop = wrapper.getChildAt(0)
    val pillChild = wrapper.getChildAt(1)
    val tagChild = wrapper.getChildAt(2)
    val caretBottom = wrapper.getChildAt(3)
    assertThat(caretTop.visibility).isEqualTo(View.VISIBLE)
    assertThat(pillChild).isSameInstanceAs(actionPill)
    assertThat(tagChild).isInstanceOf(TextView::class.java)
    assertThat(tagChild.visibility).isEqualTo(View.VISIBLE)
    assertThat(caretBottom.visibility).isEqualTo(View.GONE)

    // Verify Study Drawer scrim uses static vertical GradientDrawable fade (0 FBO, 0 blur)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true
      )
    )
    val scrim = overlay.getChildAt(overlay.childCount - 2)
    assertThat(scrim.background).isInstanceOf(GradientDrawable::class.java)
    assertThat((scrim.background as GradientDrawable).orientation)
      .isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)
  }

  @Test
  fun `HybridSearchAdapter AyahViewHolder flattens layout and preserves verseText id and action tags`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    adapter.updatePalette(palette)
    val hit = AyahSearchHit(
      id = 1L,
      sura = 1,
      ayah = 1,
      page = 1,
      suraName = "الفاتحة",
      text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      highlightRanges = emptyList(),
      isArabic = true
    )
    adapter.submitList(listOf(HybridSearchRow.AyahCard(hit, isBookmarked = false)))
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val parent = android.widget.FrameLayout(activity)
    val vh = adapter.onCreateViewHolder(parent, HybridSearchAdapter.TYPE_AYAH)
    adapter.onBindViewHolder(vh, 0)
    vh.itemView.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
    )
    vh.itemView.layout(0, 0, vh.itemView.measuredWidth, vh.itemView.measuredHeight)

    assertThat(vh.itemView.findViewById<TextView>(R.id.verseText)).isNotNull()
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.listenButton(1, 1))).isNotNull()
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.bookmarkButton(1, 1))).isNotNull()
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.openPageButton(1, 1))).isNotNull()
    // Ensure direct children of flat 4-child card contain only verseText
    // and 3 compound-drawable TextView action buttons (card bg, ref, page pill, and dividers drawn in onDraw)
    val rootGroup = vh.itemView as ViewGroup
    assertThat(rootGroup.childCount).isEqualTo(4)
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.listenButton(1, 1))).isInstanceOf(TextView::class.java)
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.bookmarkButton(1, 1))).isInstanceOf(TextView::class.java)
    assertThat(vh.itemView.findViewWithTag<View>(SearchTestTags.openPageButton(1, 1))).isInstanceOf(TextView::class.java)
  }

  @Test
  @Config(qualifiers = "ar-w411dp-h915dp-xhdpi")
  fun `HybridReaderOverlayView floating ayah bar fits all 5 buttons inside 720px even at 1_4x fontScale`() {
    RuntimeEnvironment.setFontScale(1.4f)
    try {
      val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
      val overlay = HybridReaderOverlayView(activity)
      activity.setContentView(overlay)

      val widthPx = 720
      val heightPx = 1604
      overlay.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
      )
      overlay.layout(0, 0, widthPx, heightPx)

      overlay.render(
        HybridReaderUiState(
          currentPage = 4,
          suraName = "البقرة",
          selectedSuraName = "سورة البقرة",
          selectedStart = SuraAyah(2, 22),
          selectedEnd = SuraAyah(2, 22),
          ayahOverlayBounds = AyahOverlayBounds(
            firstLineLeft = 200f,
            firstLineTop = 450f,
            firstLineRight = 520f,
            firstLineBottom = 500f,
            lastLineLeft = 200f,
            lastLineTop = 450f,
            lastLineRight = 520f,
            lastLineBottom = 500f
          )
        )
      )
      overlay.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
      )
      overlay.layout(0, 0, widthPx, heightPx)

      val expectedLabels = listOf(
        context.getString(R.string.hybrid_reader_action_listen),
        context.getString(R.string.hybrid_reader_action_tafsir),
        context.getString(R.string.hybrid_reader_action_bookmark),
        context.getString(R.string.hybrid_reader_action_copy),
        context.getString(R.string.hybrid_reader_action_share)
      )
      val allTexts = collectTextViews(overlay)
      expectedLabels.forEach { label ->
        val tv = allTexts.firstOrNull { it.text.toString() == label }
        assertThat(tv).isNotNull()
        assertThat(isEffectivelyVisible(tv!!, overlay)).isTrue()
        assertThat(tv.width).isGreaterThan(0)
      }

      val shareBtnParent = allTexts.first { it.text.toString() == expectedLabels.last() }.parent as View
      val actionPill = shareBtnParent.parent as View
      val wrapper = actionPill.parent as ViewGroup
      assertThat(wrapper.left).isAtLeast(1)
      assertThat(wrapper.right).isAtMost(widthPx)
    } finally {
      RuntimeEnvironment.setFontScale(1.0f)
    }
  }

  @Test
  @Config(qualifiers = "ar")
  fun `NativeHybridSearchView keeps recent search chips visible even when query is non-blank`() = runTest {
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val testScope = CoroutineScope(dispatcher + SupervisorJob())
    FakeSearchProvider.queryHandler = { uri, _ ->
      if (uri == QuranDataProvider.SEARCH_RAW_ARABIC_URI) {
        CursorWrapper(
          RustSearchResultsCursor(
            columnNames = arrayOf("_id", "sura", "ayah", "text"),
            packedMatches = intArrayOf(0, 1, 1, 1),
            indexGeneration = 1
          )
        )
      } else {
        null
      }
    }
    Robolectric.buildContentProvider(FakeSearchProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)

    val quranInfo = QuranInfo(MadaniDataSource())
    val quranDisplayData = QuranDisplayData(quranInfo)
    val prefs = context.getSharedPreferences("run3_recent_chip_test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val recentRepo = RecentSearchRepository(prefs)
    val bookmarksDao = FakeBookmarksDao { sa -> quranInfo.getPageFromSuraAyah(sa.sura, sa.ayah) }

    try {
      val engine = HybridSearchEngine(
        context = context,
        quranInfo = quranInfo,
        quranDisplayData = quranDisplayData,
        quranFileUtils = FakeQuranFileUtils.create(context),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = recentRepo,
        hybridBookmarkToggler = HybridBookmarkToggler(bookmarksDao),
        scope = testScope,
        ioDispatcher = dispatcher,
        computeDispatcher = dispatcher
      )

      val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
      val searchView = NativeHybridSearchView(activity).apply {
        applyPalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
        bind(
          searchEngine = engine,
          handler = object : HybridSearchActionHandler {
            override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
            override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
            override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
          },
          scope = testScope
        )
      }
      activity.setContentView(searchView)

      engine.submitQuery("الله", scopeOverride = HybridSearchScope.AYAHS, recordRecent = true)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      assertThat(engine.uiState.value.query).isEqualTo("الله")
      assertThat(engine.uiState.value.recentQueries).contains("الله")

      val chipView = searchView.findViewById<View>(R.id.recent_chip)
      assertThat(chipView).isNotNull()
      assertThat(chipView.contentDescription.toString()).isEqualTo("بحث سابق: الله")
      assertThat(isEffectivelyVisible(chipView, searchView)).isTrue()

      val chipTexts = collectTextViews(chipView).map { it.text.toString() }
      assertThat(chipTexts).contains("الله")
    } finally {
      testScope.cancel()
    }
  }

  private fun isEffectivelyVisible(view: View, root: View): Boolean {
    var current: View? = view
    while (current != null) {
      if (current.visibility != View.VISIBLE) return false
      if (current === root) return true
      current = current.parent as? View
    }
    return true
  }

  private fun collectTextViews(view: View): List<TextView> {
    val result = mutableListOf<TextView>()
    if (view is TextView) {
      result.add(view)
    } else if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        result.addAll(collectTextViews(view.getChildAt(i)))
      }
    }
    return result
  }

  class FakeSearchProvider : ContentProvider() {
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
