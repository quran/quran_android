package com.quran.labs.androidquran.hybrid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HubIndexSegment
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.hub.HybridIndexAdapter
import com.quran.labs.androidquran.hybrid.hub.HybridIndexRow
import com.quran.labs.androidquran.hybrid.hub.SuraIndexItem
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.reader.TafsirDisplayState
import com.quran.labs.androidquran.hybrid.reader.TafsirLoadState
import com.quran.labs.androidquran.hybrid.reader.TafsirSourceItem
import com.quran.labs.androidquran.hybrid.search.AyahSearchHit
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchRow
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run12OptimizationFixesTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @After
  fun tearDown() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
  }

  @Test
  fun `AyahCardLayout has 4 child Views, exposes refLabel and pageLabel in contentDescription and AccessibilityNodeInfo without middle dot, and draws on Canvas`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val hit = AyahSearchHit(
      id = 42L,
      sura = 55,
      ayah = 1,
      page = 531,
      suraName = "الرحمن",
      text = "الرَّحْمَٰنُ",
      highlightRanges = emptyList(),
      isArabic = true,
      ayahRefLabel = "سورة الرحمن : ١",
      pageLabel = "ص ٥٣١"
    )
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)
    adapter.updatePalette(palette)
    adapter.submitList(listOf(HybridSearchRow.AyahCard(hit, isBookmarked = false)))

    val rv = RecyclerView(activity)
    val holder = adapter.onCreateViewHolder(rv, HybridSearchAdapter.TYPE_AYAH)
    adapter.onBindViewHolder(holder, 0)

    val card = holder.itemView as ViewGroup
    assertThat(card.childCount).isEqualTo(4)
    assertThat(card.getChildAt(0).id).isEqualTo(R.id.verseText)
    assertThat(card.getChildAt(1).tag).isEqualTo(hit.listenTag)
    assertThat(card.getChildAt(2).tag).isEqualTo(hit.bookmarkTag)
    assertThat(card.getChildAt(3).tag).isEqualTo(hit.openPageTag)

    val cd = card.contentDescription.toString()
    assertThat(cd).contains("سورة الرحمن : ١")
    assertThat(cd).contains("ص ٥٣١")
    assertThat(cd).doesNotContain("\u00B7")

    val info = AccessibilityNodeInfo.obtain()
    card.onInitializeAccessibilityNodeInfo(info)
    val a11yText = info.text.toString()
    assertThat(a11yText).startsWith("ص ٥٣١")
    assertThat(a11yText).contains("سورة الرحمن : ١")
    assertThat(a11yText).doesNotContain("\u00B7")
    info.recycle()

    card.measure(
      View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    assertThat(card.measuredHeight).isGreaterThan(0)
    card.draw(Canvas())
  }

  @Test
  fun `FloatingBottomNavView static pill hardware layer defaults off and toggles cleanly`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val nav = FloatingBottomNavView(activity)
    val pillContainer = nav.getChildAt(0)
    assertThat(pillContainer.layerType).isEqualTo(View.LAYER_TYPE_NONE)

    nav.setStaticPillLayerEnabled(true)
    assertThat(pillContainer.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)

    nav.unbind()
    assertThat(pillContainer.layerType).isEqualTo(View.LAYER_TYPE_NONE)
  }

  @Test
  fun `HybridHubView keeps inactive tabs INVISIBLE with a11y gating and IndexAdapter uses LightweightPressColorDrawable`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run12_hub_test", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()

    try {
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = com.quran.labs.androidquran.data.QuranDisplayData(quranInfo),
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

      val mainContainer = hub.getChildAt(0) as FrameLayout
      val contentHost = mainContainer.getChildAt(0) as FrameLayout
      val searchChild = (0 until contentHost.childCount)
        .map { contentHost.getChildAt(it) }
        .first { it is NativeHybridSearchView } as NativeHybridSearchView

      // Inactive search shell stays View.INVISIBLE with a11y/focus hidden and unbound
      assertThat(hub.indexRecyclerView.visibility).isEqualTo(View.VISIBLE)
      assertThat(searchChild.visibility).isEqualTo(View.INVISIBLE)
      assertThat(searchChild.alpha).isEqualTo(1f)
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()
      assertThat(searchChild.importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
      assertThat(searchChild.descendantFocusability).isEqualTo(ViewGroup.FOCUS_BLOCK_DESCENDANTS)

      // Switching to SEARCH makes searchChild VISIBLE/accessible/focusable and hides indexRecyclerView
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.SEARCH), showPaletteSheet = false)
      assertThat(searchChild.visibility).isEqualTo(View.VISIBLE)
      assertThat(searchChild.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
      assertThat(searchChild.descendantFocusability).isEqualTo(ViewGroup.FOCUS_AFTER_DESCENDANTS)
      assertThat(hub.indexRecyclerView.visibility).isEqualTo(View.INVISIBLE)
      assertThat(hub.indexRecyclerView.importantForAccessibility)
        .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
      assertThat(hub.indexRecyclerView.descendantFocusability).isEqualTo(ViewGroup.FOCUS_BLOCK_DESCENDANTS)

      // HybridIndexAdapter replaces RippleDrawable with LightweightPressColorDrawable
      val indexAdapter = HybridIndexAdapter(
        onContinueReading = {},
        onOpenWirdGoal = {},
        onSegmentSelected = {},
        onOpenPage = {}
      )
      indexAdapter.updatePalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
      indexAdapter.submitRows(
        listOf(
          HybridIndexRow.Hero(DailyWirdState(), "الفاتحة", 1),
          HybridIndexRow.SegmentBar(HubIndexSegment.SURAS),
          HybridIndexRow.SurahRow(SuraIndexItem(1, "الفاتحة", "مكية - ٧ آيات", 1, 1, true, 7))
        ),
        HubIndexSegment.SURAS
      )
      val heroHolder = indexAdapter.onCreateViewHolder(hub.indexRecyclerView, 1)
      indexAdapter.onBindViewHolder(heroHolder, 0)
      val suraHolder = indexAdapter.onCreateViewHolder(hub.indexRecyclerView, 3)
      indexAdapter.onBindViewHolder(suraHolder, 2)
      assertThat(suraHolder.itemView.background).isNotInstanceOf(RippleDrawable::class.java)
      assertThat(suraHolder.itemView.background)
        .isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridReaderOverlayView uses HybridBrightnessSliderView without SeekBar, flat tafsirChips, and View_INVISIBLE tab gating`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    overlay.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
    )
    overlay.layout(0, 0, 720, 1604)

    // Verify Tafsir tab uses flat GradientDrawable chips
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

    val sheet = overlay.getChildAt(overlay.childCount - 1) as ViewGroup
    val tabHost = sheet.getChildAt(2) as FrameLayout
    val tafsirRoot = (0 until tabHost.childCount)
      .map { tabHost.getChildAt(it) }
      .first { it is LinearLayout } as LinearLayout
    val sourceRow = ((tafsirRoot.getChildAt(1) as HorizontalScrollView).getChildAt(0) as LinearLayout)
    assertThat(sourceRow.childCount).isEqualTo(2)
    assertThat(sourceRow.getChildAt(0).background).isInstanceOf(GradientDrawable::class.java)
    assertThat(sourceRow.getChildAt(0).background)
      .isNotInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)

    // Switch back to Recitation tab and verify inactive Tafsir tab uses View.INVISIBLE + blocked a11y/focus
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.RECITATION
      )
    )
    assertThat(tafsirRoot.visibility).isEqualTo(View.INVISIBLE)
    assertThat(tafsirRoot.importantForAccessibility)
      .isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    assertThat(tafsirRoot.descendantFocusability).isEqualTo(ViewGroup.FOCUS_BLOCK_DESCENDANTS)

    // Open standalone Reader Appearance Sheet and verify zero SeekBar instances & HybridBrightnessSliderView works
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = false,
        isAppearanceSheetOpen = true
      )
    )
    val appearanceRoot = overlay.appearanceSheetCardForTest()!!
    assertThat(appearanceRoot.visibility).isEqualTo(View.VISIBLE)
    val allViews = ArrayList<View>()
    fun collect(v: View) {
      allViews.add(v)
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) collect(v.getChildAt(i))
      }
    }
    collect(appearanceRoot)
    assertThat(allViews.none { it is SeekBar }).isTrue()
    val sliders = allViews.filterIsInstance<HybridReaderOverlayView.HybridBrightnessSliderView>()
    assertThat(sliders).hasSize(2)

    val slider = sliders[0]
    var callbackProgress = -1
    var callbackFromUser = false
    slider.max = 100
    slider.progress = 20
    slider.onProgressChanged = { p, fromUser ->
      callbackProgress = p
      callbackFromUser = fromUser
    }
    slider.measure(
      View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY)
    )
    slider.layout(0, 0, 500, 64)
    slider.draw(Canvas())

    val info = AccessibilityNodeInfo.obtain()
    slider.onInitializeAccessibilityNodeInfo(info)
    assertThat(info.className.toString()).isEqualTo(SeekBar::class.java.name)
    assertThat(info.rangeInfo).isNotNull()
    assertThat(info.rangeInfo.current).isEqualTo(20f)
    info.recycle()

    val args = Bundle().apply {
      putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 75f)
    }
    assertThat(
      slider.performAccessibilityAction(
        AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
        args
      )
    ).isTrue()
    assertThat(slider.progress).isEqualTo(75)
    assertThat(callbackProgress).isEqualTo(75)
    assertThat(callbackFromUser).isTrue()

    val now = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 250f, 32f, 0)
    val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, 250f, 32f, 0)
    assertThat(slider.onTouchEvent(down)).isTrue()
    assertThat(slider.onTouchEvent(up)).isTrue()
    down.recycle()
    up.recycle()
    assertThat(slider.progress).isEqualTo(50)
  }
}
