package com.quran.labs.androidquran.hybrid.ui

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.hub.HybridPaletteSheetView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class HybridViewLifecycleAdversarialTest {

  @Test
  fun `HybridHubView releaseTransientOnStop cancels pending idle prebuild and unbind is idempotent`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val root = FrameLayout(activity)
    activity.setContentView(root)

    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val quranDisplayData = QuranDisplayData(quranInfo)
    val recentPrefs = activity.getSharedPreferences("adv_hub_recent", Context.MODE_PRIVATE)
      .apply { edit().clear().commit() }
    val searchEngine = HybridSearchEngine(
      context = activity,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      quranFileUtils = FakeQuranFileUtils.create(activity),
      translationsDBAdapter = FakeTranslationsDBAdapter(),
      recentSearchRepository = RecentSearchRepository(recentPrefs),
      hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
      scope = scope,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )
    val handler = object : HybridSearchActionHandler {
      override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
      override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
      override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
    }

    val hub = HybridHubView(
      context = activity,
      scope = scope,
      searchEngineProvider = { searchEngine },
      searchActionHandler = handler,
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
    try {
      root.addView(hub)
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.INDEX), showPaletteSheet = false)
      hub.scheduleIdlePrebuildIfNeeded()
      hub.releaseTransientOnStop()
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(2_000L, TimeUnit.MILLISECONDS)

      assertThat(hub.hasPrebuiltSearchShellForTest()).isFalse()
      assertThat(hub.hasPrebuiltBookmarksShellForTest()).isFalse()

      // Unbind and detach twice must be completely idempotent
      hub.unbind()
      root.removeView(hub)
      hub.unbind()
      assertThat(root.childCount).isEqualTo(0)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridHubView keeps active Search tab focusable and editable across releaseTransientOnStop and onActivityResume`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val root = FrameLayout(activity)
    activity.setContentView(root)

    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val quranDisplayData = QuranDisplayData(quranInfo)
    val recentPrefs = activity.getSharedPreferences("adv_hub_search_refocus", Context.MODE_PRIVATE)
      .apply { edit().clear().commit() }
    val searchEngine = HybridSearchEngine(
      context = activity,
      quranInfo = quranInfo,
      quranDisplayData = quranDisplayData,
      quranFileUtils = FakeQuranFileUtils.create(activity),
      translationsDBAdapter = FakeTranslationsDBAdapter(),
      recentSearchRepository = RecentSearchRepository(recentPrefs),
      hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
      scope = scope,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )
    val handler = object : HybridSearchActionHandler {
      override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
      override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
      override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
    }

    val hub = HybridHubView(
      context = activity,
      scope = scope,
      searchEngineProvider = { searchEngine },
      searchActionHandler = handler,
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
    try {
      root.addView(hub)
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.SEARCH), showPaletteSheet = false)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      val sv = hub.searchViewForTest()!!
      assertThat(sv.searchInput.isFocusable).isTrue()
      assertThat(sv.searchInput.isFocusableInTouchMode).isTrue()

      // Simulate navigating to PagerActivity (onStop) and returning via Back (onResume)
      hub.releaseTransientOnStop()
      assertThat(hub.hasPrebuiltSearchShellForTest()).isTrue()
      assertThat(sv.searchInput.isFocusable).isTrue()
      assertThat(sv.searchInput.isFocusableInTouchMode).isTrue()

      hub.onActivityResume()
      sv.searchInput.setText("الله")
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchEngine.uiState.value.query).isEqualTo("الله")

      // Defensive recovery: even if releaseFocusAndInput() ran, onActivityResume() restores focus & watcher
      sv.releaseFocusAndInput()
      assertThat(sv.searchInput.isFocusable).isFalse()
      hub.onActivityResume()
      assertThat(sv.searchInput.isEnabled).isTrue()
      assertThat(sv.searchInput.isFocusable).isTrue()
      assertThat(sv.searchInput.isFocusableInTouchMode).isTrue()

      sv.searchInput.setText("الرحيم")
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchEngine.uiState.value.query).isEqualTo("الرحيم")

      hub.unbind()
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `FloatingBottomNavView, HybridPaletteSheetView, HybridReaderOverlayView, and HybridBrightnessSliderView handle detach and touch bounds`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val root = FrameLayout(activity)
    activity.setContentView(root)

    val nav = FloatingBottomNavView(activity)
    root.addView(nav)
    for (tab in HybridNavTab.entries) {
      nav.setSelectedTab(tab)
    }
    nav.unbind()
    root.removeView(nav)

    val paletteSheet = HybridPaletteSheetView(
      context = activity,
      onSelectThemeMode = {},
      onSelectThemeStyle = {},
      onDismiss = {}
    )
    root.addView(paletteSheet)
    paletteSheet.update(
      palette = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = true),
      themeMode = "auto",
      themeStyle = AppThemeStyle.GREEN,
      navBottomInsetPx = 0
    )
    paletteSheet.setSheetVisible(true)
    root.removeView(paletteSheet) // detach while visible / animating
    paletteSheet.setSheetVisible(false)

    val overlay = HybridReaderOverlayView(activity)
    root.addView(overlay)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraNumber = 1,
        suraName = "الفاتحة",
        juzNumber = 1,
        isChromeVisible = true,
        isStudyDrawerOpen = true,
        selectedStart = SuraAyah(1, 1),
        selectedEnd = SuraAyah(2, 1), // cross-sura range
        selectedSuraName = "الفاتحة",
        playbackSpeed = 1.5f,
        repeatCount = -1
      )
    )
    val badTouch = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, -10f, -10f, 0)
    overlay.dispatchTouchEvent(badTouch)
    badTouch.recycle()
    overlay.unbind()
    root.removeView(overlay)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    // HybridBrightnessSliderView LTR/RTL extreme touch clamping
    val slider = HybridReaderOverlayView.HybridBrightnessSliderView(activity).apply {
      max = 255
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      measure(
        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY)
      )
      layout(0, 0, 300, 48)
    }
    val rtlLeftTouch = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, -50f, 24f, 0)
    slider.onTouchEvent(rtlLeftTouch)
    rtlLeftTouch.recycle()
    assertThat(slider.progress).isEqualTo(255)

    val rtlRightTouch = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 500f, 24f, 0)
    slider.onTouchEvent(rtlRightTouch)
    rtlRightTouch.recycle()
    assertThat(slider.progress).isEqualTo(0)

    assertThat(root.childCount).isEqualTo(0)
  }

  @Test
  fun `FloatingBottomNavView, RubElHizbView, and HybridPaletteSheetView survive rapid switching across all 10 duo-tone palettes`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val nav = FloatingBottomNavView(activity)
    val medallion = RubElHizbView(activity).apply { setNumber(114) }
    val sheet = HybridPaletteSheetView(
      context = activity,
      onSelectThemeMode = {},
      onSelectThemeStyle = {},
      onDismiss = {}
    )
    for (style in AppThemeStyle.entries) {
      for (isDark in listOf(false, true)) {
        val palette = HybridViewPalette.from(style, isDark)
        nav.applyPalette(palette)
        medallion.applyPalette(palette)
        sheet.update(palette, themeMode = if (isDark) "dark" else "light", themeStyle = style, navBottomInsetPx = 0)
        assertThat(medallion.medallionBorderColorForTest).isEqualTo(palette.editorialAccent)
        assertThat(medallion.medallionFillColorForTest).isEqualTo(palette.medallionFill)
      }
    }
    nav.unbind()
  }
}
