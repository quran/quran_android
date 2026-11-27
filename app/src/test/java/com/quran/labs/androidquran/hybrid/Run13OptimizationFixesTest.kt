package com.quran.labs.androidquran.hybrid

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
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
import com.quran.labs.androidquran.hybrid.reader.AyahOverlayBounds
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.search.AyahSearchHit
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchAdapter
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.HybridSearchRow
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
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
class Run13OptimizationFixesTest {

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
  fun `NativeHybridSearchView setStaticSearchBarLayerEnabled defaults off, toggles HARDWARE, and resets on unbind`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val searchView = NativeHybridSearchView(activity)
    val searchBarContainer = searchView.getChildAt(0)
    assertThat(searchBarContainer.layerType).isEqualTo(View.LAYER_TYPE_NONE)

    searchView.setStaticSearchBarLayerEnabled(true)
    assertThat(searchBarContainer.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)

    searchView.setStaticSearchBarLayerEnabled(false)
    assertThat(searchBarContainer.layerType).isEqualTo(View.LAYER_TYPE_NONE)

    searchView.setStaticSearchBarLayerEnabled(true)
    assertThat(searchBarContainer.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    searchView.unbind()
    assertThat(searchBarContainer.layerType).isEqualTo(View.LAYER_TYPE_NONE)
  }

  @Test
  fun `HybridViewTokens attachApplePressFeedback delays scale 48ms, snaps on cancel, and springs on up`() {
    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val target = View(activity).apply { isClickable = true }
    activity.setContentView(target)
    HybridViewTokens.attachApplePressFeedback(target, 0.95f)

    // 1. Scroll intercept (< 48ms): ACTION_DOWN then ACTION_CANCEL before delay elapses -> zero animation
    val t0 = SystemClock.uptimeMillis()
    val down1 = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
    target.dispatchTouchEvent(down1)
    down1.recycle()

    Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isNotEqualTo(true)
    assertThat(target.scaleX).isEqualTo(1f)

    val cancel1 = MotionEvent.obtain(t0, t0 + 20, MotionEvent.ACTION_CANCEL, 50f, 50f, 0)
    target.dispatchTouchEvent(cancel1)
    cancel1.recycle()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isNotEqualTo(true)
    assertThat(target.scaleX).isEqualTo(1f)
    assertThat(target.scaleY).isEqualTo(1f)

    // 2. Move > touchSlop (< 48ms) cancels delayed press
    val slop = ViewConfiguration.get(activity).scaledTouchSlop
    val t1 = SystemClock.uptimeMillis()
    val down2 = MotionEvent.obtain(t1, t1, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
    val move2 = MotionEvent.obtain(t1, t1 + 10, MotionEvent.ACTION_MOVE, 50f, 50f + slop + 10f, 0)
    target.dispatchTouchEvent(down2)
    target.dispatchTouchEvent(move2)
    down2.recycle()
    move2.recycle()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isNotEqualTo(true)
    assertThat(target.scaleX).isEqualTo(1f)

    // 3. Cancel after >= 48ms snaps immediately with zero 180ms spring
    val t2 = SystemClock.uptimeMillis()
    val down3 = MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
    target.dispatchTouchEvent(down3)
    down3.recycle()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isEqualTo(true)

    val cancel3 = MotionEvent.obtain(t2, t2 + 60, MotionEvent.ACTION_CANCEL, 50f, 50f, 0)
    target.dispatchTouchEvent(cancel3)
    cancel3.recycle()
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isEqualTo(false)
    assertThat(target.scaleX).isEqualTo(1f)
    assertThat(target.scaleY).isEqualTo(1f)

    // 4. Real tap (ACTION_UP after >= 48ms) triggers spring release and settles back to 1f
    val t3 = SystemClock.uptimeMillis()
    val down4 = MotionEvent.obtain(t3, t3, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
    target.dispatchTouchEvent(down4)
    down4.recycle()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isEqualTo(true)

    val up4 = MotionEvent.obtain(t3, t3 + 60, MotionEvent.ACTION_UP, 50f, 50f, 0)
    target.dispatchTouchEvent(up4)
    up4.recycle()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(300, TimeUnit.MILLISECONDS)
    assertThat(target.getTag(R.id.hybrid_press_anim_active)).isEqualTo(false)
    assertThat(target.scaleX).isEqualTo(1f)
    assertThat(target.scaleY).isEqualTo(1f)
  }

  @Test
  fun `AyahCardLayout removes foreground press drawable and draws press state via dispatchSetPressed`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val hit = AyahSearchHit(
      id = 1L,
      sura = 1,
      ayah = 1,
      page = 1,
      suraName = "الفاتحة",
      text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
      highlightRanges = emptyList(),
      isArabic = true,
      ayahRefLabel = "سورة الفاتحة : ١",
      pageLabel = "ص ١"
    )
    val adapter = HybridSearchAdapter(
      onOpenPage = { _, _, _ -> },
      onPlayAyah = { _, _ -> },
      onToggleBookmark = { _, _, _ -> },
      onDownloadArabicDb = {},
      onOpenTranslationManager = {}
    )
    adapter.updatePalette(HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false))
    adapter.submitList(listOf(HybridSearchRow.AyahCard(hit, isBookmarked = false)))

    val rv = RecyclerView(activity)
    val holder = adapter.onCreateViewHolder(rv, HybridSearchAdapter.TYPE_AYAH)
    adapter.onBindViewHolder(holder, 0)
    val card = holder.itemView as ViewGroup

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      assertThat(card.foreground).isNull()
    }
    card.measure(
      View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    card.layout(0, 0, card.measuredWidth, card.measuredHeight)
    card.isPressed = true
    card.draw(Canvas())
    card.isPressed = false
    card.draw(Canvas())
  }

  @Test
  fun `HybridReaderOverlayView hides closed drawer and inactive wrappers with View_INVISIBLE`() {
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
    val tabHost = sheet.getChildAt(2) as FrameLayout
    val recitationTab = tabHost.getChildAt(0)

    // Closed drawer must be INVISIBLE so UIAutomator exposes zero drawer tabs
    assertThat(sheet.visibility).isEqualTo(View.INVISIBLE)
    assertThat(sheet.isShown).isFalse()
    assertThat(recitationTab.isShown).isFalse()

    // Select an ayah so ayahBarWrapper becomes visible, then open Study Drawer with TAFSIR tab
    val bounds = AyahOverlayBounds(100f, 300f, 500f, 360f, 120f, 360f, 480f, 420f)
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        selectedSuraName = "سورة الفاتحة",
        selectedStart = SuraAyah(1, 1),
        selectedEnd = SuraAyah(1, 1),
        ayahOverlayBounds = bounds,
        isStudyDrawerOpen = false
      )
    )
    val audioCapsuleOuter = overlay.getChildAt(1)
    val ayahBarWrapper = overlay.getChildAt(2)
    assertThat(audioCapsuleOuter.visibility).isEqualTo(View.VISIBLE)
    assertThat(ayahBarWrapper.visibility).isEqualTo(View.VISIBLE)

    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraName = "الفاتحة",
        selectedSuraName = "سورة الفاتحة",
        selectedStart = SuraAyah(1, 1),
        selectedEnd = SuraAyah(1, 1),
        ayahOverlayBounds = bounds,
        isStudyDrawerOpen = true,
        selectedStudyTab = HybridReaderContract.StudyTab.TAFSIR
      )
    )

    assertThat(sheet.visibility).isEqualTo(View.VISIBLE)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(recitationTab.visibility).isEqualTo(View.INVISIBLE)
    val tafsirTab = tabHost.getChildAt(1)
    assertThat(tafsirTab.visibility).isEqualTo(View.VISIBLE)

    sheet.viewTreeObserver.dispatchOnPreDraw()
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_HARDWARE)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)

    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(audioCapsuleOuter.visibility).isEqualTo(View.INVISIBLE)
    assertThat(ayahBarWrapper.visibility).isEqualTo(View.INVISIBLE)
  }

  @Test
  fun `HybridHubView builds unbound INVISIBLE shells at 1500ms without SearchEngine or layer allocation`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = context.getSharedPreferences("run13_hub_coalesce_test", Context.MODE_PRIVATE)
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

      val mainContainer = hub.getChildAt(0) as FrameLayout
      val contentHost = mainContainer.getChildAt(0) as FrameLayout
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
      assertThat(hub.hasBoundSearchEngineForTest()).isFalse()
      assertThat(searchChild.getChildAt(0).layerType).isEqualTo(View.LAYER_TYPE_NONE)
    } finally {
      scope.cancel()
    }
  }
}
