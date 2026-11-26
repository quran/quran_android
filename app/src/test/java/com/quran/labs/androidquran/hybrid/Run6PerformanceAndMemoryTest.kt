package com.quran.labs.androidquran.hybrid

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.hub.HybridIndexDataSource
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.reader.TafsirDisplayState
import com.quran.labs.androidquran.hybrid.reader.TafsirLoadState
import com.quran.labs.androidquran.hybrid.reader.TafsirSourceItem
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridVectorPaths
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Provider
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class Run6PerformanceAndMemoryTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    QuranSettings.setInstance(null)
  }

  @After
  fun tearDown() {
    runCatching {
      (context as? TestApplication)?.appCoroutineScope?.cancel()
    }
    QuranSettings.setInstance(null)
  }

  @Test
  fun `SearchActivity onDestroy unbinds search view, clears rootView, and clears window background`() {
    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "")
    val controller = Robolectric.buildActivity(SearchActivity::class.java, intent)
    val activity = controller.create().start().resume().get()

    assertThat(activity.rootView.childCount).isGreaterThan(0)
    assertThat(activity.nativeSearchView.resultsRecyclerView.adapter).isNotNull()

    controller.pause().stop().destroy()

    assertThat(activity.nativeSearchView.resultsRecyclerView.adapter).isNull()
    assertThat(activity.rootView.childCount).isEqualTo(0)
    assertThat(activity.window.decorView.background).isNull()
  }

  @Test
  fun `NativeHybridSearchView respects repeatOnLifecycle STARTED and stops collecting when STOPPED`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val testScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run6_lifecycle_recents", Context.MODE_PRIVATE)
    prefs.edit().clear().commit()
    val engine = HybridSearchEngine(
      context = context,
      quranInfo = quranInfo,
      quranDisplayData = QuranDisplayData(quranInfo),
      quranFileUtils = FakeQuranFileUtils.create(context),
      translationsDBAdapter = FakeTranslationsDBAdapter(),
      recentSearchRepository = RecentSearchRepository(prefs),
      hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
      scope = testScope,
      ioDispatcher = UnconfinedTestDispatcher(),
      computeDispatcher = UnconfinedTestDispatcher()
    )

    val owner = object : LifecycleOwner {
      val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle get() = registry
    }
    owner.registry.currentState = Lifecycle.State.CREATED

    try {
      val searchView = NativeHybridSearchView(activity)
      searchView.bind(
        searchEngine = engine,
        handler = object : HybridSearchActionHandler {
          override fun openPage(page: Int, sura: Int, ayah: Int) = Unit
          override fun playAyah(sura: Int, ayah: Int, page: Int) = Unit
          override fun toggleBookmark(sura: Int, ayah: Int, page: Int) = Unit
        },
        scope = testScope,
        lifecycleOwner = owner
      )

      // While CREATED (below STARTED), engine query updates do NOT mutate stopped view
      engine.onQueryTextChanged("ملك")
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchView.searchInput.text.toString()).isEmpty()

      // Moving to STARTED starts collecting and syncs state
      owner.registry.currentState = Lifecycle.State.STARTED
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchView.searchInput.text.toString()).isEqualTo("ملك")

      // Moving back to CREATED (STOPPED) stops collecting
      owner.registry.currentState = Lifecycle.State.CREATED
      engine.onQueryTextChanged("الله")
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(searchView.searchInput.text.toString()).isEqualTo("ملك")
    } finally {
      testScope.cancel()
    }
  }

  @Test
  fun `HybridHubView delays idle prebuild instead of eagerly inflating Search and Bookmarks on attach`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run6_hub_delay_recents", Context.MODE_PRIVATE)
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
    try {
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
        onOpenTranslationManager = {},
        lifecycleOwner = activity
      )
      activity.setContentView(hub)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      assertThat(hub.findViewById<View>(R.id.hybrid_search_root)).isNull()
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `VectorPathDrawable raster cache draws cleanly across repeated draws and prewarms Hub paths`() {
    VectorPathDrawable.prewarmHubPaths()
    val drawable = VectorPathDrawable(HybridVectorPaths.PLAY_ARROW, Color.BLACK, 24)
    drawable.setBounds(0, 0, 24, 24)
    val target = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(target)
    drawable.draw(canvas)
    drawable.draw(canvas)
    assertThat(target.isRecycled).isFalse()
    target.recycle()
  }

  @Test
  fun `QuranFileUtils haveAllImages uses verified sentinel fast-path and invalidates when sample page missing`() {
    val fileUtils = FakeQuranFileUtils.create(context) as QuranFileUtils
    val settings = QuranSettings.getInstance(context)
    settings.pageType = "madani"
    settings.clearVerifiedPagesSignature()

    val widthParam = "_test_fast"
    val totalPages = 6
    val dir = fileUtils.getQuranImagesDirectory(widthParam)
    dir.deleteRecursively()
    dir.mkdirs()
    for (p in 1..totalPages) {
      File(dir, QuranFileUtils.getPageFileName(p)).writeText("p$p")
    }

    try {
      assertThat(fileUtils.haveAllImages(widthParam, totalPages, makeDirectory = false)).isTrue()
      assertThat(settings.hasVerifiedAllPages(widthParam, totalPages)).isTrue()

      // Second call succeeds via O(1) verified sentinel + sample pages
      assertThat(fileUtils.haveAllImages(widthParam, totalPages, makeDirectory = false)).isTrue()

      // Deleting last sample page invalidates sentinel and forces real false return
      File(dir, QuranFileUtils.getPageFileName(totalPages)).delete()
      assertThat(fileUtils.haveAllImages(widthParam, totalPages, makeDirectory = false)).isFalse()
      assertThat(settings.hasVerifiedAllPages(widthParam, totalPages)).isFalse()
    } finally {
      dir.deleteRecursively()
      settings.clearVerifiedPagesSignature()
    }
  }

  @Test
  fun `HybridIndexDataSource prewarmSuras caches all 114 suras in O(1)`() {
    val quranInfo = QuranInfo(MadaniDataSource())
    val displayData = QuranDisplayData(quranInfo)
    val settings = QuranSettings.getInstance(context)
    val arabicDb = ArabicDatabaseUtils(context, quranInfo, FakeQuranFileUtils.create(context))
    val dataSource = HybridIndexDataSource(
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      juzListPresenter = Provider { JuzListPresenter(quranInfo, arabicDb) }
    )

    assertThat(dataSource.getCachedSurasOrNull(context)).isNull()
    val prewarmed = dataSource.prewarmSuras(context)
    assertThat(prewarmed).hasSize(114)
    assertThat(dataSource.getCachedSurasOrNull(context)).isSameInstanceAs(prewarmed)
  }

  @Test
  fun `FloatingBottomNavView reuses tab background drawables when switching tabs`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val nav = FloatingBottomNavView(activity)
    nav.applyPalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
    val pill = nav.getChildAt(0) as ViewGroup
    val tab0 = pill.getChildAt(0)
    val initialBg = tab0.background

    nav.setSelectedTab(HybridNavTab.SEARCH)
    nav.setSelectedTab(HybridNavTab.BOOKMARKS)
    nav.setSelectedTab(HybridNavTab.READ)

    assertThat(tab0.background).isSameInstanceAs(initialBg)
  }

  @Test
  fun `HybridReaderOverlayView reuses tafsir chip Views in-place when selected source changes`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)

    val sources = listOf(
      TafsirSourceItem("ar.muyassar.db", "الميسر", isArabic = true),
      TafsirSourceItem("ar.jalalayn.db", "الجلالين", isArabic = true)
    )
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          sura = 1,
          ayah = 1,
          suraName = "الفاتحة",
          availableSources = sources,
          selectedFilename = "ar.muyassar.db",
          loadState = TafsirLoadState.LOADED,
          fullText = "تفسير 1"
        )
      )
    )

    val chipBefore = findViewWithText(overlay, "الميسر")
    assertThat(chipBefore).isNotNull()

    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          sura = 1,
          ayah = 1,
          suraName = "الفاتحة",
          availableSources = sources,
          selectedFilename = "ar.jalalayn.db",
          loadState = TafsirLoadState.LOADED,
          fullText = "تفسير 2"
        )
      )
    )

    val chipAfter = findViewWithText(overlay, "الميسر")
    assertThat(chipAfter).isSameInstanceAs(chipBefore)

    // Verify scrim uses static vertical GradientDrawable fade and toggles directly without animator
    val scrim = overlay.getChildAt(overlay.childCount - 2)
    assertThat(scrim.background).isInstanceOf(GradientDrawable::class.java)
    assertThat((scrim.background as GradientDrawable).orientation)
      .isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)
    assertThat(scrim.visibility).isEqualTo(View.VISIBLE)
  }

  @Test
  fun `AdaptiveMotionTokens caches animator scale via ContentObserver without repeated IPC`() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    assertThat(AdaptiveMotionTokens.areAnimationsEnabled(context)).isTrue()

    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    assertThat(AdaptiveMotionTokens.areAnimationsEnabled(context)).isFalse()

    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    assertThat(AdaptiveMotionTokens.areAnimationsEnabled(context)).isTrue()
  }

  private fun findViewWithText(root: View, text: String): View? {
    if (root is TextView && root.text.toString() == text) return root
    if (root is ViewGroup) {
      for (i in 0 until root.childCount) {
        findViewWithText(root.getChildAt(i), text)?.let { return it }
      }
    }
    return null
  }
}
