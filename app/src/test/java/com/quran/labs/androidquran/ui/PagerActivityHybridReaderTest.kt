package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.SelectionRectangle
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdRepository
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.reader.AyahActionPlacement
import com.quran.labs.androidquran.hybrid.reader.AyahBarPlacementDirection
import com.quran.labs.androidquran.hybrid.reader.AyahOverlayBounds
import com.quran.labs.androidquran.hybrid.reader.FloatingAyahActionBar
import com.quran.labs.androidquran.hybrid.reader.HybridReaderController
import com.quran.labs.androidquran.hybrid.reader.HybridReaderIntentParser
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlay
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.TAG_FLOATING_AYAH_CARET
import com.quran.labs.androidquran.hybrid.reader.TafsirLookupHelper
import com.quran.labs.androidquran.hybrid.reader.computeFloatingAyahBarPlacement
import com.quran.labs.androidquran.hybrid.reader.computeSelectedItemOverlayBounds
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.labs.androidquran.view.SlidingUpPanelLayout
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.page.common.toolbar.AyahToolBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class PagerActivityHybridReaderTest {

  private lateinit var quranInfo: QuranInfo
  private lateinit var context: Context

  @Before
  fun setUp() {
    quranInfo = QuranInfo(MadaniDataSource())
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `HybridReaderIntentParser resolves page from highlight sura and ayah when page extra omitted`() {
    val intent = HybridReaderContract.applyToIntent(
      intent = Intent(),
      sura = 2,
      ayah = 255,
      initialStudyTab = HybridReaderContract.StudyTab.TAFSIR,
      autoPlayAyah = true
    )

    val parsed = HybridReaderIntentParser.parse(intent, quranInfo)
    assertThat(parsed.hasExplicitPage).isFalse()
    assertThat(parsed.page).isEqualTo(42) // Ayat al-Kursi is on Madani page 42
    assertThat(parsed.highlightSura).isEqualTo(2)
    assertThat(parsed.highlightAyah).isEqualTo(255)
    assertThat(parsed.initialStudyTab).isEqualTo(HybridReaderContract.StudyTab.TAFSIR)
    assertThat(parsed.autoPlayAyah).isTrue()
  }

  @Test
  fun `HybridReaderIntentParser respects explicit page extra and handles null intent safely`() {
    val emptyParsed = HybridReaderIntentParser.parse(null, quranInfo)
    assertThat(emptyParsed.page).isEqualTo(1)
    assertThat(emptyParsed.highlightSura).isNull()
    assertThat(emptyParsed.initialStudyTab).isNull()
    assertThat(emptyParsed.autoPlayAyah).isFalse()

    val explicitIntent = HybridReaderContract.applyToIntent(
      intent = Intent().putExtra("page", 604),
      initialStudyTab = HybridReaderContract.StudyTab.APPEARANCE
    )
    val explicitParsed = HybridReaderIntentParser.parse(explicitIntent, quranInfo)
    assertThat(explicitParsed.hasExplicitPage).isTrue()
    assertThat(explicitParsed.page).isEqualTo(604)
    assertThat(explicitParsed.initialStudyTab).isEqualTo(HybridReaderContract.StudyTab.APPEARANCE)
  }

  @Test
  fun `quran_page_activity_slider places hybrid_reader_overlay before overlay while keeping sliding panel contract`() {
    context.setTheme(R.style.QuranToolBar_Overlay)
    val root = LayoutInflater.from(context)
      .inflate(R.layout.quran_page_activity_slider, null) as SlidingUpPanelLayout

    assertThat(root.childCount).isEqualTo(4)
    // Child index 1 must stay ayah_action_panel_layout (@id/sliding_layout) for SlidingUpPanelLayout
    assertThat(root.getChildAt(1).id).isEqualTo(R.id.sliding_layout)

    val hybridOverlay = root.findViewById<HybridReaderOverlayView>(R.id.hybrid_reader_overlay)
    val modalOverlay = root.findViewById<View>(R.id.overlay)
    assertThat(hybridOverlay).isNotNull()
    assertThat(root.findViewById<View>(R.id.mushaf_clip_container)).isNotNull()
    assertThat(modalOverlay).isNotNull()

    val hybridIdx = (root as ViewGroup).indexOfChild(hybridOverlay)
    val modalIdx = root.indexOfChild(modalOverlay)
    assertThat(hybridIdx).isGreaterThan(0)
    assertThat(hybridIdx).isLessThan(modalIdx)

    // Legacy toolbar & spinner remain present in XML for AppThemeProposalsTest but hidden by default
    val toolbarArea = root.findViewById<View>(R.id.toolbar_area)
    assertThat(toolbarArea.visibility).isEqualTo(View.GONE)
    assertThat(root.findViewById<View>(R.id.toolbar)).isNotNull()
    assertThat(root.findViewById<View>(R.id.spinner)).isNotNull()
  }

  @Test
  fun `HybridReaderOverlay composes and measures cleanly in Calm Beige Light and Calm Green Dark across both modes`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    try {
      val settings = QuranSettings.getInstance(context)
      val qari = Qari(
        id = 1,
        nameResource = R.string.app_name,
        url = "https://example.com/",
        path = "husary",
        hasGaplessAlternative = false,
        db = null
      )
      val controller = HybridReaderController(
        appContext = context,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranSettings = settings,
        dailyWirdRepository = DailyWirdRepository(
          context.getSharedPreferences("test_overlay_wird", Context.MODE_PRIVATE)
        ) { 20_100L },
        audioStatusRepository = AudioStatusRepository(),
        currentQariManager = CurrentQariManager(context, QariUtil(FakePageProvider(listOf(qari)))),
        downloadInfoStreams = DownloadInfoStreams(),
        timingRepository = TimingRepository(),
        bookmarksDao = FakeBookmarksDao(),
        tafsirLookupHelper = TafsirLookupHelper(
          FakeTranslationsDBAdapter(),
          FakeTranslationModel(),
          TranslationUtil(quranInfo),
          ioDispatcher = Dispatchers.Main.immediate
        ),
        scope = scope,
        ioDispatcher = Dispatchers.Main.immediate
      )
      controller.updateCurrentPage(1)
      controller.onAyahSelectionChanged(
        SuraAyah(1, 7),
        SuraAyah(1, 7),
        AyahOverlayBounds(100f, 300f, 500f, 360f, 120f, 360f, 480f, 420f)
      )

      val composeView = ComposeView(activity)
      composeView.setContent {
        HybridReaderOverlay(
          controller = controller,
          onBackToHub = {},
          onJumpPage = {},
          onTogglePageBookmark = {},
          onAyahListen = {},
          onAyahTafsir = {},
          onAyahBookmark = {},
          onAyahCopy = { _, _ -> },
          onAyahShare = { _, _ -> },
          onPlayPause = {},
          onSkipNext = {},
          onSkipPrev = {},
          onSpeedChanged = {},
          onRepeatChanged = {},
          onShowQariList = {},
          onCancelDownload = {},
          onAllowMobileDownload = {},
          onOpenTafsirManager = {},
          onTafsirStepAyah = {},
          onAppThemeStyleSelected = {},
          onAppThemeModeSelected = {},
          onPageAppearanceUpdated = {},
          onMushafNudgeChanged = {}
        )
      }
      activity.setContentView(composeView)

      // Measure Screen 3 (Calm Reading Mode with Floating Ayah Bar) in Beige Light
      controller.selectAppThemeStyle(AppThemeStyle.BEIGE)
      composeView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      composeView.layout(0, 0, 720, 1604)
      assertThat(composeView.width).isEqualTo(720)
      assertThat(composeView.height).isEqualTo(1604)

      // Measure Screen 4 (Study Drawer all 3 tabs) in Green Dark
      controller.selectAppThemeStyle(AppThemeStyle.GREEN)
      for (tab in HybridReaderContract.StudyTab.entries) {
        controller.openStudyDrawer(tab)
        composeView.measure(
          View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, 720, 1604)
      }
    } finally {
      scope.cancel()
      AppThemeStyle.select(AppThemeStyle.DEFAULT)
    }
  }

  @Test
  fun `HybridReaderOverlayView native overlay measures and hides inactive tabs in Tafsir mode`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    try {
      val settings = QuranSettings.getInstance(context)
      val qari = Qari(
        id = 1,
        nameResource = R.string.app_name,
        url = "https://example.com/",
        path = "husary",
        hasGaplessAlternative = false,
        db = null
      )
      val controller = HybridReaderController(
        appContext = context,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranSettings = settings,
        dailyWirdRepository = DailyWirdRepository(
          context.getSharedPreferences("test_native_overlay_wird", Context.MODE_PRIVATE)
        ) { 20_100L },
        audioStatusRepository = AudioStatusRepository(),
        currentQariManager = CurrentQariManager(context, QariUtil(FakePageProvider(listOf(qari)))),
        downloadInfoStreams = DownloadInfoStreams(),
        timingRepository = TimingRepository(),
        bookmarksDao = FakeBookmarksDao(),
        tafsirLookupHelper = TafsirLookupHelper(
          FakeTranslationsDBAdapter(),
          FakeTranslationModel(),
          TranslationUtil(quranInfo),
          ioDispatcher = Dispatchers.Main.immediate
        ),
        scope = scope,
        ioDispatcher = Dispatchers.Main.immediate
      )
      controller.updateCurrentPage(1)
      controller.onAyahSelectionChanged(
        SuraAyah(1, 7),
        SuraAyah(1, 7),
        AyahOverlayBounds(100f, 300f, 500f, 360f, 120f, 360f, 480f, 420f)
      )

      val overlayView = HybridReaderOverlayView(activity)
      overlayView.bind(
        readerController = controller,
        scope = scope,
        onBackToHub = {},
        onJumpPage = {},
        onTogglePageBookmark = {},
        onAyahListen = {},
        onAyahTafsir = {},
        onAyahBookmark = {},
        onAyahCopy = { _, _ -> },
        onAyahShare = { _, _ -> },
        onPlayPause = {},
        onSkipNext = {},
        onSkipPrev = {},
        onSpeedChanged = {},
        onRepeatChanged = {},
        onShowQariList = {},
        onCancelDownload = {},
        onAllowMobileDownload = {},
        onOpenTafsirManager = {},
        onTafsirStepAyah = {},
        onAppThemeStyleSelected = {},
        onAppThemeModeSelected = {},
        onPageAppearanceUpdated = {},
        onMushafNudgeChanged = {}
      )
      activity.setContentView(overlayView)

      controller.selectAppThemeStyle(AppThemeStyle.BEIGE)
      overlayView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      overlayView.layout(0, 0, 720, 1604)
      assertThat(overlayView.width).isEqualTo(720)
      assertThat(overlayView.height).isEqualTo(1604)

      controller.selectAppThemeStyle(AppThemeStyle.GREEN)
      controller.openStudyDrawer(HybridReaderContract.StudyTab.TAFSIR)
      overlayView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      overlayView.layout(0, 0, 720, 1604)
      assertThat(overlayView.findViewById<View>(R.id.tafsir_empty_cta).visibility)
        .isEqualTo(View.VISIBLE)
    } finally {
      scope.cancel()
      AppThemeStyle.select(AppThemeStyle.DEFAULT)
    }
  }

  @Test
  fun `FloatingAyahActionBar places caret at physical left-origin X without RTL mirroring under LayoutDirection_Rtl`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val density = Density(2.0f)
    val cases = listOf(
      Triple(AyahBarPlacementDirection.BELOW_AYAH, 132f, 120f),
      Triple(AyahBarPlacementDirection.BELOW_AYAH, 562f, 550f),
      Triple(AyahBarPlacementDirection.ABOVE_AYAH, 132f, 120f),
      Triple(AyahBarPlacementDirection.ABOVE_AYAH, 562f, 550f)
    )

    for ((direction, caretCenterXPx, expectedCaretLeftPx) in cases) {
      val composeView = ComposeView(activity)
      val placement = AyahActionPlacement(
        direction = direction,
        topPx = 300f,
        caretCenterXPx = caretCenterXPx,
        pillLeftPx = 36f,
        pillWidthPx = 648f,
        showSelectionTag = true
      )
      composeView.setContent {
        CompositionLocalProvider(
          LocalLayoutDirection provides LayoutDirection.Rtl,
          LocalDensity provides density
        ) {
          QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
            FloatingAyahActionBar(
              placement = placement,
              suraName = "البقرة",
              selectedStart = SuraAyah(2, 255),
              selectedEnd = null,
              isBookmarked = false,
              onListenClick = {},
              onTafsirClick = {},
              onBookmarkClick = {},
              onCopyClick = {},
              onShareClick = {}
            )
          }
        }
      }
      activity.setContentView(composeView)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      composeView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      composeView.layout(0, 0, 720, 1604)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      val viewRoot = composeView.getChildAt(0) as ViewRootForTest
      val caretNode = viewRoot.semanticsOwner
        .getAllSemanticsNodes(mergingEnabled = false)
        .firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == TAG_FLOATING_AYAH_CARET }

      assertThat(caretNode).isNotNull()
      val bounds = caretNode!!.boundsInRoot
      assertThat(bounds.left).isWithin(1.5f).of(expectedCaretLeftPx)
      assertThat(bounds.width).isWithin(1.0f).of(24f)
    }
  }

  @Test
  fun `FloatingAyahActionBar omits SelectionTagPill when showSelectionTag is false`() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val density = Density(2.0f)

    fun measureTagCountAndHeight(showTag: Boolean): Pair<Boolean, Float> {
      val composeView = ComposeView(activity)
      val placement = AyahActionPlacement(
        direction = AyahBarPlacementDirection.ABOVE_AYAH,
        topPx = 100f,
        caretCenterXPx = 360f,
        pillLeftPx = 36f,
        pillWidthPx = 648f,
        showSelectionTag = showTag
      )
      composeView.setContent {
        CompositionLocalProvider(
          LocalLayoutDirection provides LayoutDirection.Rtl,
          LocalDensity provides density
        ) {
          QuranTheme(useDarkTheme = false, style = AppThemeStyle.BEIGE) {
            FloatingAyahActionBar(
              placement = placement,
              suraName = "البقرة",
              selectedStart = SuraAyah(2, 282),
              selectedEnd = null,
              isBookmarked = false,
              onListenClick = {},
              onTafsirClick = {},
              onBookmarkClick = {},
              onCopyClick = {},
              onShareClick = {}
            )
          }
        }
      }
      activity.setContentView(composeView)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      composeView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      composeView.layout(0, 0, 720, 1604)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      val viewRoot = composeView.getChildAt(0) as ViewRootForTest
      val nodes = viewRoot.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
      val hasTagText = nodes.any { node ->
        node.config.getOrNull(SemanticsProperties.Text)
          ?.any { it.text.contains("البقرة") } == true
      }
      val caretBottom = nodes.first {
        it.config.getOrNull(SemanticsProperties.TestTag) == TAG_FLOATING_AYAH_CARET
      }.boundsInRoot.bottom
      return hasTagText to caretBottom
    }

    val (withTagHasText, withTagCaretBottom) = measureTagCountAndHeight(showTag = true)
    val (compactHasText, compactCaretBottom) = measureTagCountAndHeight(showTag = false)

    assertThat(withTagHasText).isTrue()
    assertThat(compactHasText).isFalse()
    assertThat(compactCaretBottom).isLessThan(withTagCaretBottom)
  }

  @Test
  fun `computeSelectedItemOverlayBounds maps line-by-line window coordinates when imageView is null`() {
    // QuranLineByLinePresenter encodes window X inside firstItem/lastItem and window Y in yScroll.
    val indicator = SelectionIndicator.SelectedItemPosition(
      firstItem = SelectionRectangle(left = 180f, top = 420f, right = 540f, bottom = 480f),
      lastItem = SelectionRectangle(left = 200f, top = 500f, right = 460f, bottom = 560f),
      xScroll = 0f,
      yScroll = 140f
    )
    val overlayLoc = intArrayOf(10, 40)

    val bounds = computeSelectedItemOverlayBounds(
      indicator = indicator,
      overlayLocationInWindow = overlayLoc,
      imageViewLocationInWindow = null,
      imageViewWidth = 0,
      viewPagerTranslationY = -80f
    )

    assertThat(bounds.firstLineLeft).isWithin(0.01f).of(170f)
    assertThat(bounds.firstLineTop).isWithin(0.01f).of(520f)
    assertThat(bounds.firstLineRight).isWithin(0.01f).of(530f)
    assertThat(bounds.firstLineBottom).isWithin(0.01f).of(580f)
    assertThat(bounds.lastLineLeft).isWithin(0.01f).of(190f)
    assertThat(bounds.lastLineTop).isWithin(0.01f).of(600f)
    assertThat(bounds.lastLineRight).isWithin(0.01f).of(450f)
    assertThat(bounds.lastLineBottom).isWithin(0.01f).of(660f)
    assertThat(bounds.unNudgedLastLineBottom).isWithin(0.01f).of(740f)

    // Verify FloatingAyahActionBar places cleanly below the line-by-line ayah with zero overlap
    val placement = computeFloatingAyahBarPlacement(
      bounds = bounds,
      viewportWidthPx = 720f,
      viewportHeightPx = 1604f,
      topObstacleBottomPx = 148f,
      bottomObstacleTopPx = 1400f,
      pillHorizontalInsetPx = 36f,
      caretHeightPx = 14f,
      pillHeightPx = 104f,
      selectionTagHeightPx = 56f,
      gapFromAyahPx = 12f,
      caretHorizontalPaddingPx = 60f
    )
    assertThat(placement.direction).isEqualTo(AyahBarPlacementDirection.BELOW_AYAH)
    assertThat(placement.topPx).isGreaterThan(bounds.lastLineBottom)
  }

  @Test
  fun `computeSelectedItemOverlayBounds preserves ImageView-relative offset and dual-page xSideFix`() {
    val indicator = SelectionIndicator.SelectedItemPosition(
      firstItem = SelectionRectangle(left = 800f, top = 100f, right = 1020f, bottom = 150f),
      lastItem = SelectionRectangle(left = 760f, top = 160f, right = 980f, bottom = 210f),
      xScroll = 12f,
      yScroll = -20f
    )
    val bounds = computeSelectedItemOverlayBounds(
      indicator = indicator,
      overlayLocationInWindow = intArrayOf(0, 48),
      imageViewLocationInWindow = intArrayOf(360, 100),
      imageViewWidth = 360,
      viewPagerTranslationY = 0f
    )

    // dx = (360 - 0) + 12 - 360 = 12; dy = (100 - 48) - 20 = 32
    assertThat(bounds.firstLineLeft).isWithin(0.01f).of(812f)
    assertThat(bounds.firstLineTop).isWithin(0.01f).of(132f)
    assertThat(bounds.lastLineBottom).isWithin(0.01f).of(242f)
    assertThat(bounds.unNudgedLastLineBottom).isWithin(0.01f).of(242f)
  }

  @Test
  fun `detaching legacy ayahToolBar removes it from quran_page_activity parent while keeping XML intact`() {
    context.setTheme(R.style.QuranToolBar_Overlay)
    val root = LayoutInflater.from(context)
      .inflate(R.layout.quran_page_activity, null) as ViewGroup

    val ayahToolBar = root.findViewById<AyahToolBar>(R.id.ayah_toolbar)
    assertThat(ayahToolBar).isNotNull()
    assertThat(root.findViewById<View>(R.id.toolbar)).isNotNull()

    ayahToolBar.visibility = View.GONE
    (ayahToolBar.parent as? ViewGroup)?.removeView(ayahToolBar)

    assertThat(ayahToolBar.parent).isNull()
    assertThat(ayahToolBar.visibility).isEqualTo(View.GONE)
    assertThat(root.findViewById<View>(R.id.ayah_toolbar)).isNull()
  }

  @Test
  fun `HybridReaderOverlay renders surah-prefixed ayah selection tag pill while top pill stays unprefixed`() {
    val arabicConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arabicContext = context.createConfigurationContext(arabicConfig)
    val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    try {
      val settings = QuranSettings.getInstance(arabicContext)
      val qari = Qari(
        id = 1,
        nameResource = R.string.app_name,
        url = "https://example.com/",
        path = "husary",
        hasGaplessAlternative = false,
        db = null
      )
      val controller = HybridReaderController(
        appContext = arabicContext,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranSettings = settings,
        dailyWirdRepository = DailyWirdRepository(
          arabicContext.getSharedPreferences("test_overlay_tag_wird", Context.MODE_PRIVATE)
        ) { 20_100L },
        audioStatusRepository = AudioStatusRepository(),
        currentQariManager = CurrentQariManager(arabicContext, QariUtil(FakePageProvider(listOf(qari)))),
        downloadInfoStreams = DownloadInfoStreams(),
        timingRepository = TimingRepository(),
        bookmarksDao = FakeBookmarksDao(),
        tafsirLookupHelper = TafsirLookupHelper(
          FakeTranslationsDBAdapter(),
          FakeTranslationModel(),
          TranslationUtil(quranInfo),
          ioDispatcher = Dispatchers.Main.immediate
        ),
        scope = scope,
        ioDispatcher = Dispatchers.Main.immediate
      )
      controller.updateCurrentPage(1)
      controller.onAyahSelectionChanged(
        SuraAyah(1, 2),
        SuraAyah(1, 2),
        AyahOverlayBounds(100f, 300f, 500f, 360f, 120f, 360f, 480f, 420f)
      )

      val composeView = ComposeView(activity)
      composeView.setContent {
        CompositionLocalProvider(LocalDensity provides Density(2.0f)) {
          HybridReaderOverlay(
            controller = controller,
            onBackToHub = {},
            onJumpPage = {},
            onTogglePageBookmark = {},
            onAyahListen = {},
            onAyahTafsir = {},
            onAyahBookmark = {},
            onAyahCopy = { _, _ -> },
            onAyahShare = { _, _ -> },
            onPlayPause = {},
            onSkipNext = {},
            onSkipPrev = {},
            onSpeedChanged = {},
            onRepeatChanged = {},
            onShowQariList = {},
            onCancelDownload = {},
            onAllowMobileDownload = {},
            onOpenTafsirManager = {},
            onTafsirStepAyah = {},
            onAppThemeStyleSelected = {},
            onAppThemeModeSelected = {},
            onPageAppearanceUpdated = {},
            onMushafNudgeChanged = {}
          )
        }
      }
      activity.setContentView(composeView)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      composeView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
      )
      composeView.layout(0, 0, 720, 1604)
      Shadows.shadowOf(Looper.getMainLooper()).idle()

      val viewRoot = composeView.getChildAt(0) as ViewRootForTest
      val allTexts = viewRoot.semanticsOwner
        .getAllSemanticsNodes(mergingEnabled = false)
        .flatMap { node ->
          node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        }

      assertThat(allTexts).contains("الفَاتِحَةِ")
      assertThat(allTexts.any { it.startsWith("سُورَةُ الفَاتِحَةِ — ") }).isTrue()
    } finally {
      scope.cancel()
      AppThemeStyle.select(AppThemeStyle.DEFAULT)
    }
  }
}
