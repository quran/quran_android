package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.data.model.highlight.HighlightColor
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.ReadingBookmarkSlots
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.hybrid.contract.DailyWirdState
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.ui.FloatingBottomNavView
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.RubElHizbView
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranSettings
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
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridHubDuoToneAndFloatationTest {

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    AppThemeStyle.setOledDarkMode(false)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
    AppThemeStyle.setOledDarkMode(false)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun `HybridHubView applies Z0 ambient glow, Z2 floating header pill without bottom divider, non-interactive veil, and Z3 FloatingBottomNav`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    val quranInfo = QuranInfo(MadaniDataSource())
    val prefs = activity.getSharedPreferences("hub_duotone_test", Context.MODE_PRIVATE)
      .apply { edit().clear().commit() }

    try {
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranFileUtils = FakeQuranFileUtils.create(activity),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(prefs),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = scope,
        ioDispatcher = UnconfinedTestDispatcher(),
        computeDispatcher = UnconfinedTestDispatcher()
      )
      val lightPalette = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = false)
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
        initialPalette = lightPalette
      )

      // Z0 canvas ambient top glow
      assertThat(hub.background).isInstanceOf(GradientDrawable::class.java)
      assertThat((hub.background as GradientDrawable).orientation)
        .isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)

      // Header overlay has NO flush bottom divider (only headerContainer)
      val mainContainer = hub.getChildAt(0) as FrameLayout
      val headerOverlay = mainContainer.getChildAt(1) as ViewGroup
      assertThat(headerOverlay.childCount).isEqualTo(1)

      // Z2 floating header action pill with APPEARANCE_FILLED + SETTINGS
      val actionPill = hub.headerActionPillForTest() as LinearLayout
      assertThat(actionPill.background).isInstanceOf(LayerDrawable::class.java)
      assertThat(actionPill.elevation).isWithin(0.1f).of(
        HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = false))
      )
      val paletteBtn = actionPill.findViewWithTag<ImageView>("hub_palette_button")
      assertThat(paletteBtn).isNotNull()
      assertThat(paletteBtn.drawable).isInstanceOf(VectorPathDrawable::class.java)
      assertThat(paletteBtn.contentDescription.toString())
        .isEqualTo(activity.getString(R.string.hybrid_hub_palette_desc))

      // Non-interactive scroll-under fade veil pinned behind Z3 FloatingBottomNavView
      val veil = hub.bottomFadeViewForTest()
      assertThat(veil.isClickable).isFalse()
      assertThat(veil.isFocusable).isFalse()
      assertThat(veil.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO)
      assertThat(veil.background).isInstanceOf(GradientDrawable::class.java)
      assertThat((veil.background as GradientDrawable).orientation)
        .isEqualTo(GradientDrawable.Orientation.BOTTOM_TOP)

      // Z3 FloatingBottomNavView uses opaque floatingSurfaceDrawable (LayerDrawable) + Z3 elevation
      val navPill = hub.bottomNavView.getChildAt(0) as ViewGroup
      assertThat(navPill.background).isInstanceOf(LayerDrawable::class.java)
      val navBaseLayer = (navPill.background as LayerDrawable).getDrawable(0) as GradientDrawable
      assertThat(Color.alpha(navBaseLayer.color!!.defaultColor)).isEqualTo(255)
      assertThat(navPill.elevation).isWithin(0.1f).of(
        HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(3, isDark = false))
      )

      hub.unbind()
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `HybridIndexAdapter applies Z2 Hero, recessed Segmented Rail with Z2 thumb, Z1 Surah cards, and Color-2 RubElHizbView`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = true)
    val rv = RecyclerView(activity)
    val adapter = HybridIndexAdapter(
      onContinueReading = {},
      onOpenWirdGoal = {},
      onSegmentSelected = {},
      onOpenPage = {}
    )
    adapter.updatePalette(palette)
    adapter.submitRows(
      listOf(
        HybridIndexRow.Hero(
          dailyWird = DailyWirdState(lastPage = 1, lastJuz = 1, pagesReadToday = 8, goalPages = 10),
          lastSuraName = "سورة الفاتحة",
          lastJuz = 1
        ),
        HybridIndexRow.SegmentBar(HubIndexSegment.SURAS),
        HybridIndexRow.SurahRow(SuraIndexItem(1, "الفاتحة", "مكية، ٧ آيات", 1, 1, true, 7))
      ),
      HubIndexSegment.SURAS
    )

    // Hero Card: Z2 gradient float + Color 2 eyebrow + Color 1 CTA with Z2 elevation
    val heroHolder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(0))
    adapter.onBindViewHolder(heroHolder, 0)
    val heroCard = heroHolder.itemView as ViewGroup
    assertThat(heroCard.background).isInstanceOf(LayerDrawable::class.java)
    assertThat(heroCard.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = true))
    )
    val allHeroTexts = collectViews<TextView>(heroCard)
    val eyebrow = allHeroTexts.first { it.text.toString() == activity.getString(R.string.hybrid_hub_continue_reading) }
    assertThat(eyebrow.currentTextColor).isEqualTo(palette.editorialAccent)
    val ctaText = allHeroTexts.first { it.text.toString() == activity.getString(R.string.hybrid_hub_resume_cta) }
    assertThat(ctaText.currentTextColor).isEqualTo(palette.onCta)
    val ctaPill = ctaText.parent as View
    assertThat(ctaPill.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = true))
    )
    allHeroTexts.forEach { tv ->
      assertThat(tv.text.toString()).doesNotContain("\u00B7")
    }

    // Recessed Segmented Rail: LayerDrawable track (Dark uses recessed palette.surface well) + Z2 surfaceElevated active thumb
    val segHolder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(1))
    adapter.onBindViewHolder(segHolder, 1)
    val segTrack = segHolder.itemView as ViewGroup
    assertThat(segTrack.background).isInstanceOf(LayerDrawable::class.java)
    val trackBase = (segTrack.background as LayerDrawable).getDrawable(0) as GradientDrawable
    assertThat(trackBase.color!!.defaultColor).isEqualTo(palette.surface)
    val surasTab = segTrack.findViewWithTag<TextView>("hub_segment_suras")
    val juzTab = segTrack.findViewWithTag<TextView>("hub_segment_juz")
    assertThat(surasTab.background).isInstanceOf(LayerDrawable::class.java)
    val thumbBase = (surasTab.background as LayerDrawable).getDrawable(0) as GradientDrawable
    assertThat(thumbBase.color!!.defaultColor).isEqualTo(palette.surfaceElevated)
    assertThat(surasTab.currentTextColor).isEqualTo(palette.accent)
    assertThat(surasTab.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = true))
    )
    assertThat(juzTab.elevation).isEqualTo(0f)
    assertThat(juzTab.currentTextColor).isEqualTo(palette.textSecondary)

    // Surah Card: Z1 float + Color 2 RubElHizbView medallion + Color 1 page pill
    val suraHolder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(2))
    adapter.onBindViewHolder(suraHolder, 2)
    val suraCard = suraHolder.itemView as ViewGroup
    assertThat(suraCard.background)
      .isInstanceOf(HybridViewTokens.LightweightPressColorDrawable::class.java)
    assertThat(suraCard.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = true))
    )
    val medallion = collectViews<RubElHizbView>(suraCard).single()
    assertThat(medallion.medallionFillColorForTest).isEqualTo(palette.medallionFill)
    assertThat(medallion.medallionBorderColorForTest).isEqualTo(palette.medallionBorder)
    assertThat(medallion.numeralColorForTest).isEqualTo(palette.editorialAccent)
    assertThat(medallion.strokeWidthPxForTest)
      .isWithin(0.1f)
      .of(HybridViewTokens.dpF(activity, 1.35f))
    medallion.measure(
      View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
    )
    medallion.layout(0, 0, 100, 100)
    medallion.draw(Canvas())

    val pagePill = suraCard.getChildAt(suraCard.childCount - 1) as TextView
    assertThat(pagePill.currentTextColor).isEqualTo(palette.accent)
  }

  @Test
  fun `HybridPaletteSheetView uses detached Z4 floatation, plain theme names ONLY, and 2-dot duo-tone swatches`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BLUE, isDark = false)
    val sheet = HybridPaletteSheetView(
      context = activity,
      onSelectThemeMode = {},
      onSelectThemeStyle = {},
      onDismiss = {}
    )
    sheet.update(
      palette = palette,
      themeMode = Constants.THEME_LIGHT,
      themeStyle = AppThemeStyle.BLUE,
      navBottomInsetPx = 12
    )

    val card = sheet.sheetCardForTest()
    assertThat(card.background).isInstanceOf(LayerDrawable::class.java)
    val sheetBase = (card.background as LayerDrawable).getDrawable(0) as GradientDrawable
    val sheetColors = sheetBase.colors
    assertThat(sheetColors).isNotNull()
    sheetColors!!.forEach { color ->
      assertThat(Color.alpha(color)).isEqualTo(255)
    }
    assertThat(card.elevation).isWithin(0.1f).of(
      HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(4, isDark = false))
    )

    val expectedPlainLabels = mapOf(
      AppThemeStyle.DEFAULT to activity.getString(R.string.hybrid_reader_palette_default),
      AppThemeStyle.GREEN to activity.getString(R.string.hybrid_reader_palette_green),
      AppThemeStyle.BLUE to activity.getString(R.string.hybrid_reader_palette_blue),
      AppThemeStyle.BEIGE to activity.getString(R.string.hybrid_reader_palette_beige),
      AppThemeStyle.GRAY to activity.getString(R.string.hybrid_reader_palette_gray)
    )

    for (style in AppThemeStyle.entries) {
      val expectedPalette = HybridViewPalette.from(style, isDark = false)
      val swatch = sheet.styleSwatchForTest(style)
      assertThat(swatch.primaryColor).isEqualTo(expectedPalette.accent)
      assertThat(swatch.editorialColor).isEqualTo(expectedPalette.editorialAccent)
      assertThat(swatch.primaryColor).isNotEqualTo(swatch.editorialColor)

      swatch.measure(
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST)
      )
      swatch.layout(0, 0, swatch.measuredWidth, swatch.measuredHeight)
      swatch.draw(Canvas())

      assertThat(sheet.styleLabelForTest(style).text.toString())
        .isEqualTo(expectedPlainLabels[style])

      val checkView = sheet.styleCheckViewForTest(style)
      assertThat(checkView.visibility).isEqualTo(
        if (style == AppThemeStyle.BLUE) View.VISIBLE else View.INVISIBLE
      )
      assertThat(checkView.drawable).isInstanceOf(VectorPathDrawable::class.java)
    }
  }

  @Test
  fun `HybridBookmarksView uses architectural bookmark icons in editorialAccent while preserving flat CardHolder`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
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
    view.applyPalette(palette)
    assertThat(view.background).isInstanceOf(GradientDrawable::class.java)

    view.render(
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
    val rv = view.getChildAt(0) as RecyclerView
    rv.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
    )
    rv.layout(0, 0, 720, 1200)
    val cardView = rv.getChildAt(1) as ViewGroup
    assertThat(cardView.elevation).isEqualTo(0f)
    val images = collectViews<ImageView>(cardView)
    assertThat(images).hasSize(2) // leading bookmark badge + trailing close remove button
    images.forEach { iv ->
      assertThat(iv.drawable).isInstanceOf(VectorPathDrawable::class.java)
    }
  }

  @Test
  fun `HybridBookmarksView binds real reading slot and highlight colors across recycled CardHolder`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.BEIGE, isDark = false)
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
    view.applyPalette(palette)
    view.render(
      HybridHubUiState(
        bookmarks = HubBookmarksUiState(
          readingBookmarks = listOf(
            HubReadingBookmarkItem(ReadingBookmarkType.TEAL, 1, null, null, "الفاتحة", 1),
            HubReadingBookmarkItem(ReadingBookmarkType.ORANGE, 2, 2, 1, "البقرة", 1, customName = "ورد المساء"),
            HubReadingBookmarkItem(ReadingBookmarkType.RED, 50, null, null, "آل عمران", 3)
          ),
          recentPages = listOf(
            HubRecentPageItem(page = 10, suraName = "البقرة", juz = 1)
          ),
          ayahBookmarks = listOf(
            HubAyahBookmarkItem("b1", 2, 255, 42, "البقرة", 3, null)
          ),
          highlights = listOf(
            HubHighlightItem(1, 1, 1, "الفاتحة", 1, null, HighlightColor.BLUE),
            HubHighlightItem(1, 2, 1, "الفاتحة", 1, null, null)
          ),
          isLoaded = true
        )
      )
    )

    val rv = view.getChildAt(0) as RecyclerView
    @Suppress("UNCHECKED_CAST")
    val adapter = rv.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
    // Rows: 0=ReadingHdr, 1=Teal, 2=Orange, 3=Red, 4=RecentHdr, 5=Recent, 6=AyahHdr, 7=Ayah, 8=HlHdr, 9=HlBlue, 10=HlNull
    assertThat(adapter.itemCount).isEqualTo(11)

    // Bind a SINGLE recycled CardHolder sequentially across every row kind with the SAME palette
    val holder = adapter.onCreateViewHolder(rv, adapter.getItemViewType(1))
    val badgeView = collectViews<ImageView>(holder.itemView).first()

    val tealColor = ContextCompat.getColor(activity, ReadingBookmarkSlots[ReadingBookmarkType.TEAL].colorResourceId)
    adapter.onBindViewHolder(holder, 1)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = tealColor,
        badgeBg = ColorUtils.setAlphaComponent(tealColor, 36),
        isFilled = true
      )
    )

    val orangeColor = ContextCompat.getColor(activity, ReadingBookmarkSlots[ReadingBookmarkType.ORANGE].colorResourceId)
    adapter.onBindViewHolder(holder, 2)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = orangeColor,
        badgeBg = ColorUtils.setAlphaComponent(orangeColor, 36),
        isFilled = true
      )
    )
    assertThat(collectViews<TextView>(holder.itemView).any { it.text.toString() == "ورد المساء" }).isTrue()

    val redColor = ContextCompat.getColor(activity, ReadingBookmarkSlots[ReadingBookmarkType.RED].colorResourceId)
    adapter.onBindViewHolder(holder, 3)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = redColor,
        badgeBg = ColorUtils.setAlphaComponent(redColor, 36),
        isFilled = true
      )
    )
    assertThat(redColor).isNotEqualTo(tealColor)
    assertThat(orangeColor).isNotEqualTo(tealColor)

    adapter.onBindViewHolder(holder, 5)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = palette.textSecondary,
        badgeBg = palette.surfaceVariant,
        isFilled = false
      )
    )

    adapter.onBindViewHolder(holder, 7)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = palette.editorialAccent,
        badgeBg = palette.editorialSoftBg,
        isFilled = true
      )
    )

    val blueHlColor = ContextCompat.getColor(activity, highlightListColorRes(HighlightColor.BLUE))
    adapter.onBindViewHolder(holder, 9)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = blueHlColor,
        badgeBg = ColorUtils.setAlphaComponent(blueHlColor, 36),
        isFilled = true
      )
    )

    adapter.onBindViewHolder(holder, 10)
    assertThat(badgeView.tag).isEqualTo(
      BoundBookmarkBadgeVisuals(
        iconTint = palette.editorialAccent,
        badgeBg = palette.editorialSoftBg,
        isFilled = true
      )
    )
  }

  @Test
  fun `QuranActivity keeps Appearance sheet open on live style switches and restores open state across recreation`() {
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    val settings = QuranSettings.getInstance(appContext)
    val prevStyle = settings.appThemeStyle
    settings.pageType = "madani"
    settings.appThemeStyle = AppThemeStyle.DEFAULT.storageKey

    try {
      val savedState = Bundle()
      Robolectric.buildActivity(QuranActivity::class.java).use { controller ->
        val activity = controller.create().start().resume().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val paletteBtn = activity.hubView.headerActionPillForTest()
          .findViewWithTag<View>("hub_palette_button")
        assertThat(paletteBtn.performClick()).isTrue()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val sheet = activity.hubView.paletteSheetViewForTest()!!
        assertThat(activity.isPaletteSheetShownForTest).isTrue()
        assertThat(sheet.visibility).isEqualTo(View.VISIBLE)

        // Switching Duo-Tone styles applies live in-place without recreating or dismissing the sheet
        sheet.styleChipForTest(AppThemeStyle.GREEN).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.isFinishing).isFalse()
        assertThat(activity.appliedThemeStyleForTest).isEqualTo(AppThemeStyle.GREEN)
        assertThat(activity.isPaletteSheetShownForTest).isTrue()
        assertThat(sheet.visibility).isEqualTo(View.VISIBLE)
        assertThat(sheet.styleCheckViewForTest(AppThemeStyle.GREEN).visibility).isEqualTo(View.VISIBLE)

        sheet.styleChipForTest(AppThemeStyle.BLUE).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.appliedThemeStyleForTest).isEqualTo(AppThemeStyle.BLUE)
        assertThat(activity.isPaletteSheetShownForTest).isTrue()
        assertThat(sheet.visibility).isEqualTo(View.VISIBLE)

        // Tapping inside the sheet card does not dismiss it
        sheet.sheetCardForTest().performClick()
        assertThat(activity.isPaletteSheetShownForTest).isTrue()

        controller.saveInstanceState(savedState)
      }

      // Recreated activity (e.g. after Light<->Dark night mode transition) restores sheet open
      Robolectric.buildActivity(QuranActivity::class.java).use { recreatedController ->
        val restored = recreatedController.create(savedState).start().resume().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val restoredSheet = restored.hubView.paletteSheetViewForTest()!!
        assertThat(restored.isPaletteSheetShownForTest).isTrue()
        assertThat(restoredSheet.visibility).isEqualTo(View.VISIBLE)

        // Done button dismisses
        restoredSheet.doneButtonForTest().performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(restored.isPaletteSheetShownForTest).isFalse()

        // Re-open, then outside scrim tap dismisses
        val paletteBtn = restored.hubView.headerActionPillForTest()
          .findViewWithTag<View>("hub_palette_button")
        paletteBtn.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(restored.isPaletteSheetShownForTest).isTrue()
        restoredSheet.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(restored.isPaletteSheetShownForTest).isFalse()

        // Re-open, then Back press dismisses
        paletteBtn.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(restored.isPaletteSheetShownForTest).isTrue()
        restored.onBackPressedDispatcher.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(restored.isPaletteSheetShownForTest).isFalse()
      }
    } finally {
      settings.appThemeStyle = prevStyle
    }
  }

  @Test
  fun `Arabic bookmark and reading bookmark sheets have 100 percent Arabic localization with zero middle dots`() {
    val baseContext = ApplicationProvider.getApplicationContext<Context>()
    val arConfig = Configuration(baseContext.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arContext = baseContext.createConfigurationContext(arConfig)

    fun arString(name: String, vararg args: Any): String {
      val resId = arContext.resources.getIdentifier(name, "string", arContext.packageName)
      assertThat(resId).isNotEqualTo(0)
      return if (args.isEmpty()) arContext.getString(resId) else arContext.getString(resId, *args)
    }

    assertThat(arString("ayahbookmark_highlight_title")).isEqualTo("تظليل")
    assertThat(arString("ayahbookmark_reading_bookmark_is_here", "برتقالي")).isEqualTo("علامة برتقالي على هذه الآية")
    assertThat(arString("ayahbookmark_reading_bookmark_move_here", "برتقالي")).isEqualTo("نقل علامة برتقالي إلى هنا")
    assertThat(arString("ayahbookmark_reading_bookmark_set_here", "أحمر")).isEqualTo("وضع علامة أحمر هنا")
    assertThat(arString("ayahbookmark_reading_bookmark_move")).isEqualTo("نقل")
    assertThat(arString("ayahbookmark_reading_bookmark_set")).isEqualTo("وضع")
    assertThat(arString("ayahbookmark_other_reading_bookmark")).isEqualTo("علامات القراءة الأخرى")
    assertThat(arString("ayahbookmark_highlight_saved_on_close")).isEqualTo("يُحفظ تلقائياً عند إغلاق النافذة")
    assertThat(arString("readingbookmark_title")).isEqualTo("علامات القراءة")
    assertThat(arString("readingbookmark_subtitle")).isEqualTo("كل علامة تحفظ موضعاً واحداً وتنقلها مع تقدمك")
    assertThat(arString("readingbookmark_placing_on", "الفاتحة")).isEqualTo("التثبيت على الفاتحة")
    assertThat(arString("readingbookmark_at_location", "الصفحة 1")).isEqualTo("عند الصفحة 1")
    assertThat(arString("readingbookmark_ayah_location")).isEqualTo("%1\$s — صفحة %2\$d")
    val formattedAyahLoc = arString("readingbookmark_ayah_location", "سُورَةُ الفاتحة، آية 1", 1)
    assertThat(formattedAyahLoc).contains("— صفحة ")
    assertThat(formattedAyahLoc).doesNotContain("\u00B7")
    assertThat(formattedAyahLoc).doesNotContain("Page")
    assertThat(arString("readingbookmark_not_placed")).isEqualTo("غير موضوعة بعد")
    assertThat(arString("readingbookmark_on_this_page")).isEqualTo("على هذه الصفحة")
    assertThat(arString("readingbookmark_on_this_ayah")).isEqualTo("على هذه الآية")
    assertThat(arString("readingbookmark_move_here")).isEqualTo("نقل إلى هنا")
    assertThat(arString("readingbookmark_set_here")).isEqualTo("وضع هنا")
    assertThat(arString("readingbookmark_clear")).isEqualTo("إزالة")
    assertThat(arString("readingbookmark_back")).isEqualTo("رجوع")
    assertThat(arString("readingbookmark_edit")).isEqualTo("تعديل")
    assertThat(arString("readingbookmark_done_editing")).isEqualTo("إنهاء التعديل")
    assertThat(arString("readingbookmark_editing_subtitle")).isEqualTo("اختر اسماً مميزاً لكل علامة قراءة")

    val allKeys = listOf(
      "ayahbookmark_done",
      "ayahbookmark_saved",
      "ayahbookmark_not_saved",
      "ayahbookmark_reading_bookmark_is_here",
      "ayahbookmark_reading_bookmark_move_here",
      "ayahbookmark_reading_bookmark_set_here",
      "ayahbookmark_reading_bookmark_move",
      "ayahbookmark_reading_bookmark_set",
      "ayahbookmark_other_reading_bookmark",
      "ayahbookmark_highlight_title",
      "ayahbookmark_highlight_saved_on_close",
      "ayahbookmark_highlight_none",
      "ayahbookmark_collections_header",
      "ayahbookmark_new_collection",
      "ayahbookmark_collection_name_hint",
      "ayahbookmark_collection_name_error",
      "ayahbookmark_collection_name_duplicate",
      "ayahbookmark_collection_name_reserved",
      "ayahbookmark_collection_name_empty",
      "ayahbookmark_collection_name_too_long",
      "ayahbookmark_cancel",
      "ayahbookmark_create",
      "readingbookmark_title",
      "readingbookmark_subtitle",
      "readingbookmark_placing_on",
      "readingbookmark_at_location",
      "readingbookmark_ayah_location",
      "readingbookmark_not_placed",
      "readingbookmark_on_this_page",
      "readingbookmark_on_this_ayah",
      "readingbookmark_move_here",
      "readingbookmark_set_here",
      "readingbookmark_clear",
      "readingbookmark_back",
      "readingbookmark_edit",
      "readingbookmark_done_editing",
      "readingbookmark_editing_subtitle"
    )
    val asciiWordRegex = Regex("[A-Za-z]{2,}")
    for (key in allKeys) {
      val raw = arString(key)
      assertThat(raw).doesNotContain("\u00B7")
      assertThat(asciiWordRegex.containsMatchIn(raw)).isFalse()
    }
  }

  @Test
  fun `HybridPaletteSheetView shows OLED toggle ONLY in Dark mode and QuranActivity toggles OLED live keeping sheet open`() {
    Robolectric.buildActivity(FragmentActivity::class.java).use { hostController ->
      val activity = hostController.setup().get()
      val toggledValues = mutableListOf<Boolean>()
      val sheet = HybridPaletteSheetView(
        context = activity,
        onSelectThemeMode = {},
        onSelectThemeStyle = {},
        onDismiss = {},
        onToggleOledDarkMode = { toggledValues.add(it) }
      )

      // Hidden in Light mode
      sheet.update(
        palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false, oled = false),
        themeMode = Constants.THEME_LIGHT,
        themeStyle = AppThemeStyle.DEFAULT,
        navBottomInsetPx = 0,
        isOledDarkMode = false
      )
      assertThat(sheet.oledToggleRowForTest().visibility).isEqualTo(View.GONE)

      // Visible in Dark mode
      sheet.update(
        palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = true, oled = false),
        themeMode = Constants.THEME_DARK,
        themeStyle = AppThemeStyle.DEFAULT,
        navBottomInsetPx = 0,
        isOledDarkMode = false
      )
      assertThat(sheet.oledToggleRowForTest().visibility).isEqualTo(View.VISIBLE)
      assertThat(sheet.oledToggleRowForTest().isSelected).isFalse()
      assertThat(sheet.oledTitleViewForTest().text.toString())
        .isEqualTo(activity.getString(R.string.hybrid_appearance_oled_title))
      assertThat(sheet.oledSubtitleViewForTest().text.toString())
        .isEqualTo(activity.getString(R.string.hybrid_appearance_oled_subtitle))

      sheet.oledToggleRowForTest().performClick()
      assertThat(toggledValues).containsExactly(true)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    // End-to-end live in-place toggle in QuranActivity Dark mode (even when OS uiMode is Light!)
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    QuranSettings.setInstance(null)
    val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
    val settings = QuranSettings.getInstance(appContext)
    val prevTheme = prefs.getString(Constants.PREF_APP_THEME, null)
    val prevStyle = settings.appThemeStyle
    val prevOled = settings.isOledDarkMode
    prefs.edit().putString(Constants.PREF_APP_THEME, Constants.THEME_DARK).commit()
    settings.appThemeStyle = AppThemeStyle.GREEN.storageKey
    settings.isOledDarkMode = false
    settings.pageType = "madani"
    AppThemeStyle.setOledDarkMode(false)

    try {
      Robolectric.buildActivity(QuranActivity::class.java).use { controller ->
        val hubActivity = controller.create().start().resume().visible().get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val paletteBtn = hubActivity.hubView.headerActionPillForTest()
          .findViewWithTag<View>("hub_palette_button")
        paletteBtn.performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val liveSheet = hubActivity.hubView.paletteSheetViewForTest()!!
        assertThat(hubActivity.isPaletteSheetShownForTest).isTrue()
        assertThat(liveSheet.oledToggleRowForTest().visibility).isEqualTo(View.VISIBLE)
        assertThat(hubActivity.appliedOledDarkModeForTest).isFalse()

        // Toggle OLED ON live — stays open, no recreation, pure #000000 canvas
        liveSheet.oledToggleRowForTest().performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(hubActivity.isFinishing).isFalse()
        assertThat(hubActivity.isPaletteSheetShownForTest).isTrue()
        assertThat(liveSheet.visibility).isEqualTo(View.VISIBLE)
        assertThat(hubActivity.appliedOledDarkModeForTest).isTrue()
        assertThat(settings.isOledDarkMode).isTrue()
        assertThat(AppThemeStyle.isOledDarkMode).isTrue()
        assertThat(liveSheet.oledToggleRowForTest().isSelected).isTrue()
        val oledBgColors = (hubActivity.hubView.background as GradientDrawable).colors!!
        assertThat(oledBgColors.all { it == Color.BLACK }).isTrue()

        // Toggle OLED back OFF live — stays open, restores tonal dark surface
        liveSheet.oledToggleRowForTest().performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(hubActivity.isPaletteSheetShownForTest).isTrue()
        assertThat(hubActivity.appliedOledDarkModeForTest).isFalse()
        assertThat(settings.isOledDarkMode).isFalse()
        assertThat(AppThemeStyle.isOledDarkMode).isFalse()
        val nonOledBgColors = (hubActivity.hubView.background as GradientDrawable).colors!!
        assertThat(nonOledBgColors.last()).isNotEqualTo(Color.BLACK)

        // Switching to Light mode inside Appearance sheet immediately hides OLED toggle row
        liveSheet.modeButtonForTest(Constants.THEME_LIGHT).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(liveSheet.oledToggleRowForTest().visibility).isEqualTo(View.GONE)
      }
    } finally {
      if (prevTheme == null) {
        prefs.edit().remove(Constants.PREF_APP_THEME).commit()
      } else {
        prefs.edit().putString(Constants.PREF_APP_THEME, prevTheme).commit()
      }
      settings.appThemeStyle = prevStyle
      settings.isOledDarkMode = prevOled
      AppThemeStyle.setOledDarkMode(false)
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
      Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
  }

  private inline fun <reified T : View> collectViews(root: View): List<T> {
    val out = ArrayList<T>()
    val queue = ArrayDeque<View>()
    queue.add(root)
    while (queue.isNotEmpty()) {
      val v = queue.removeFirst()
      if (v is T) out.add(v)
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) queue.add(v.getChildAt(i))
      }
    }
    return out
  }
}
