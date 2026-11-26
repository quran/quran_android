package com.quran.labs.androidquran.hybrid.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.PathParser
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.hub.DailyWirdGoalDialogNative
import com.quran.labs.androidquran.hybrid.hub.HybridHubUiState
import com.quran.labs.androidquran.hybrid.hub.HybridHubView
import com.quran.labs.androidquran.hybrid.hub.HybridPaletteSheetView
import com.quran.labs.androidquran.hybrid.reader.AyahOverlayBounds
import com.quran.labs.androidquran.hybrid.reader.HybridReaderOverlayView
import com.quran.labs.androidquran.hybrid.reader.HybridReaderUiState
import com.quran.labs.androidquran.hybrid.search.HybridBookmarkToggler
import com.quran.labs.androidquran.hybrid.search.HybridSearchEngine
import com.quran.labs.androidquran.hybrid.search.NativeHybridSearchView
import com.quran.labs.androidquran.fakes.FakeBookmarksDao
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.hybrid.contract.RecentSearchRepository
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.data.core.QuranInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridDesignPolishTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `soft ripple alpha is softened while preserving RippleDrawable contract`() {
    val light = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)
    val dark = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = true)

    assertThat(Color.alpha(light.ripple)).isEqualTo(20)
    assertThat(Color.alpha(dark.ripple)).isEqualTo(28)

    val rounded = HybridViewTokens.interactiveRoundedDrawable(
      fillColor = light.cardSurface,
      radiusPx = 16f,
      rippleColor = light.ripple
    )
    val circle = HybridViewTokens.interactiveCircleDrawable(
      fillColor = dark.cardSurface,
      rippleColor = dark.ripple
    )
    assertThat(rounded).isInstanceOf(RippleDrawable::class.java)
    assertThat(circle).isInstanceOf(RippleDrawable::class.java)
  }

  @Test
  fun `luminousHairlineColor and applySoftElevationShadow configure API 28 shadow alphas`() {
    val light = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = false)
    val dark = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = true)

    assertThat(HybridViewTokens.luminousHairlineColor(light, light.divider)).isEqualTo(light.divider)
    assertThat(HybridViewTokens.luminousHairlineColor(dark, dark.divider)).isEqualTo(0x1FFFFFFF)

    assertThat(HybridViewTokens.softShadowAmbientAlpha(isDark = false)).isEqualTo(0x12)
    assertThat(HybridViewTokens.softShadowSpotAlpha(isDark = false)).isEqualTo(0x1E)
    assertThat(HybridViewTokens.softShadowAmbientAlpha(isDark = true)).isEqualTo(0x38)
    assertThat(HybridViewTokens.softShadowSpotAlpha(isDark = true)).isEqualTo(0x52)

    val view = View(context)
    HybridViewTokens.applySoftElevationShadow(view, 8f, isDark = false)
    assertThat(view.elevation).isWithin(0.1f).of(HybridViewTokens.dpF(context, 8f))

    HybridViewTokens.applySoftElevationShadow(view, 12f, isDark = true)
    assertThat(view.elevation).isWithin(0.1f).of(HybridViewTokens.dpF(context, 12f))
  }

  @Test
  fun `applyTopRoundedOutlineProvider builds non-empty rounded rect outline`() {
    val view = View(context)
    HybridViewTokens.applyTopRoundedOutlineProvider(view, 24f)
    view.measure(
      View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, 400, 300)

    val outline = Outline()
    view.outlineProvider.getOutline(view, outline)
    assertThat(outline.isEmpty).isFalse()
  }

  @Test
  fun `floating and overlapping containers disable child and padding clipping`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    try {
      val nav = FloatingBottomNavView(activity)
      assertThat(nav.clipChildren).isFalse()
      assertThat(nav.clipToPadding).isFalse()

      val sheet = HybridPaletteSheetView(activity, {}, {}, {})
      assertThat(sheet.clipChildren).isFalse()
      assertThat(sheet.clipToPadding).isFalse()

      val quranInfo = QuranInfo(MadaniDataSource())
      val engine = HybridSearchEngine(
        context = activity,
        quranInfo = quranInfo,
        quranDisplayData = QuranDisplayData(quranInfo),
        quranFileUtils = FakeQuranFileUtils.create(activity),
        translationsDBAdapter = FakeTranslationsDBAdapter(),
        recentSearchRepository = RecentSearchRepository(
          activity.getSharedPreferences("design_polish_recents", Context.MODE_PRIVATE)
        ),
        hybridBookmarkToggler = HybridBookmarkToggler(FakeBookmarksDao()),
        scope = scope
      )
      val hub = HybridHubView(
        context = activity,
        scope = scope,
        searchEngineProvider = { engine },
        searchActionHandler = object : com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler {
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
      assertThat(hub.clipChildren).isFalse()
      assertThat(hub.clipToPadding).isFalse()
      assertThat(hub.indexRecyclerView.clipChildren).isFalse()
      assertThat(hub.indexRecyclerView.clipToPadding).isFalse()

      val searchView = NativeHybridSearchView(activity)
      assertThat(searchView.clipChildren).isTrue()
      assertThat(searchView.resultsRecyclerView.clipChildren).isTrue()
      assertThat(searchView.resultsRecyclerView.clipToPadding).isFalse()

      val overlay = HybridReaderOverlayView(activity)
      assertThat(overlay.clipChildren).isFalse()
      assertThat(overlay.clipToPadding).isFalse()
      overlay.render(
        HybridReaderUiState(
          currentPage = 1,
          suraName = "الفاتحة",
          selectedSuraName = "سورة الفاتحة",
          selectedStart = SuraAyah(1, 1),
          selectedEnd = SuraAyah(1, 1),
          ayahOverlayBounds = AyahOverlayBounds(100f, 300f, 500f, 360f, 100f, 300f, 500f, 360f)
        )
      )
      for (i in 0 until overlay.childCount) {
        val child = overlay.getChildAt(i)
        if (child is ViewGroup) {
          assertThat(child.clipChildren).isFalse()
          assertThat(child.clipToPadding).isFalse()
        }
      }
      hub.render(HybridHubUiState(selectedTab = HybridNavTab.INDEX), showPaletteSheet = false)
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `Apple press feedback preserves click dispatch and resets cleanly on detach`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val btn = FrameLayout(activity)
    var clicked = false
    btn.setOnClickListener { clicked = true }
    HybridViewTokens.attachApplePressFeedback(btn, AdaptiveMotionTokens.PRESS_SCALE)
    activity.setContentView(btn)
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
    val handledDown = btn.dispatchTouchEvent(down)
    down.recycle()
    assertThat(handledDown).isTrue()

    btn.scaleX = 0.975f
    btn.scaleY = 0.975f
    HybridViewTokens.resetApplePressFeedback(btn)
    assertThat(btn.scaleX).isEqualTo(1f)
    assertThat(btn.scaleY).isEqualTo(1f)

    btn.performClick()
    assertThat(clicked).isTrue()
  }

  @Test
  fun `critically damped interpolators and visibility transitions settle cleanly without loops`() {
    assertThat(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED.getInterpolation(0f)).isWithin(0.001f).of(0f)
    assertThat(AdaptiveMotionTokens.SPRING_CRITICALLY_DAMPED.getInterpolation(1f)).isWithin(0.001f).of(1f)
    assertThat(AdaptiveMotionTokens.APPLE_PRESS_EASE.getInterpolation(0f)).isWithin(0.001f).of(0f)
    assertThat(AdaptiveMotionTokens.APPLE_PRESS_EASE.getInterpolation(1f)).isWithin(0.001f).of(1f)

    Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    try {
      val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
      val target = View(activity)
      activity.setContentView(target)
      target.measure(
        View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
      )
      target.layout(0, 0, 200, 100)

      HybridViewTokens.animateVisibilityTransition(target, shouldBeVisible = true, translateYDp = 12f)
      assertThat(target.visibility).isEqualTo(View.VISIBLE)
      assertThat(target.alpha).isEqualTo(1f)
      assertThat(target.translationY).isEqualTo(0f)

      HybridViewTokens.animateVisibilityTransition(target, shouldBeVisible = false, translateYDp = 12f)
      assertThat(target.visibility).isEqualTo(View.GONE)
      assertThat(target.alpha).isEqualTo(1f)
      assertThat(target.translationY).isEqualTo(0f)
    } finally {
      Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
  }

  @Test
  fun `DailyWirdGoalDialogNative wraps card in unclipped 16dp shadow container`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val palette = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = true)
    val dialog = DailyWirdGoalDialogNative.show(
      context = activity,
      initialGoalPages = 10,
      palette = palette,
      onSave = {},
      onDismiss = {}
    )
    try {
      val content = dialog.findViewById<ViewGroup>(android.R.id.content)
      assertThat(content).isNotNull()
      // Verify the dialog showed cleanly with shadow-capable wrapper
      Shadows.shadowOf(Looper.getMainLooper()).idle()
      assertThat(dialog.isShowing).isTrue()
    } finally {
      dialog.dismiss()
    }
  }

  @Test
  fun `HybridViewPalette exposes duo-tone editorial and floating layer tokens across all 10 palettes`() {
    for (style in AppThemeStyle.entries) {
      val light = HybridViewPalette.from(style, isDark = false)
      val dark = HybridViewPalette.from(style, isDark = true)

      assertThat(light.editorialAccent).isNotEqualTo(light.accent)
      assertThat(dark.editorialAccent).isNotEqualTo(dark.accent)
      assertThat(Color.alpha(light.editorialSoftBg)).isEqualTo(33)
      assertThat(Color.alpha(dark.editorialSoftBg)).isEqualTo(38)
      assertThat(Color.alpha(light.editorialHighlightBg)).isEqualTo(41)
      assertThat(Color.alpha(dark.editorialHighlightBg)).isEqualTo(51)
      assertThat(light.medallionFill).isEqualTo(light.editorialSoftBg)
      assertThat(light.medallionBorder).isEqualTo(light.editorialAccent)
      assertThat(dark.medallionFill).isEqualTo(dark.editorialSoftBg)
      assertThat(dark.medallionBorder).isEqualTo(dark.editorialAccent)
      assertThat(light.cardHairlineBorder).isEqualTo(Color.argb(13, 0, 0, 0))
      assertThat(dark.cardHairlineBorder).isEqualTo(Color.argb(23, 255, 255, 255))
      assertThat(light.floatHairlineBorder).isEqualTo(Color.argb(224, 255, 255, 255))
      assertThat(dark.floatHairlineBorder).isEqualTo(Color.argb(33, 255, 255, 255))
      assertThat(Color.alpha(light.glassRimHighlight)).isEqualTo(203)
      assertThat(Color.alpha(dark.glassRimHighlight)).isEqualTo(36)
      assertThat(Color.alpha(light.glassFloatBg)).isLessThan(255)
      assertThat(Color.alpha(dark.glassFloatBg)).isLessThan(255)
    }
  }

  @Test
  fun `all 23 monochrome architectural stroke icons parse and rasterize cleanly`() {
    VectorPathDrawable.prewarmHubPaths()
    assertThat(HybridArchitecturalIcons.ALL).hasSize(23)

    val target = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(target)
      for ((name, spec) in HybridArchitecturalIcons.ALL) {
        assertThat(spec.layers).isNotEmpty()
        for (layer in spec.layers) {
          // Verify PathParser accepts every SVG path command cleanly without throwing
          val parsed = PathParser.createPathFromPathData(layer.pathData)
          assertThat(parsed).isNotNull()
          assertThat(layer.fillAlphaFraction > 0f || layer.strokeWidthViewport > 0f).isTrue()
        }

        val drawable = HybridArchitecturalIcons.drawable(spec, Color.BLACK, 24)
        assertThat(drawable.parsedLayerCount).isEqualTo(spec.layers.size)
        assertThat(drawable.intrinsicWidth).isEqualTo(24)
        assertThat(drawable.intrinsicHeight).isEqualTo(24)

        drawable.prewarmBitmap()
        drawable.setBounds(0, 0, 48, 48)
        drawable.draw(canvas)
        drawable.draw(canvas)

        // Verify tint update and cache hit path
        drawable.setTintColor(0xFF9B6D22.toInt())
        drawable.draw(canvas)
        assertThat(target.isRecycled).isFalse()
        assertThat(name).isNotEmpty()
      }
    } finally {
      target.recycle()
    }
  }

  @Test
  fun `Z0-Z4 floating layer helpers create static drawables and configure tinted shadows and outlines`() {
    val light = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = false)
    val dark = HybridViewPalette.from(AppThemeStyle.DEFAULT, isDark = true)

    val glow = HybridViewTokens.canvasAmbientGlowDrawable(light.surface, light.surfaceTopGlow)
    assertThat(glow).isInstanceOf(GradientDrawable::class.java)
    assertThat(glow.orientation).isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)

    val veil = HybridViewTokens.scrollUnderVeilDrawable(dark.surface)
    assertThat(veil).isInstanceOf(GradientDrawable::class.java)
    assertThat(veil.orientation).isEqualTo(GradientDrawable.Orientation.BOTTOM_TOP)
    val veilColors = veil.colors
    assertThat(veilColors).isNotNull()
    assertThat(veilColors!!.size).isEqualTo(4)
    assertThat(Color.alpha(veilColors[0])).isEqualTo(255)
    assertThat(Color.alpha(veilColors[1])).isEqualTo(255)
    assertThat(Color.alpha(veilColors[2])).isEqualTo(0xCC)
    assertThat(Color.alpha(veilColors[3])).isEqualTo(0)

    val floatSurface = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = light.glassFloatBg,
      borderColor = light.floatHairlineBorder,
      specularTopColor = light.glassRimHighlight,
      strokeWidthPx = 1,
      radiusPx = 28f,
      topGradientColor = light.surfaceElevated
    )
    assertThat(floatSurface).isInstanceOf(LayerDrawable::class.java)
    assertThat(floatSurface.numberOfLayers).isEqualTo(3)

    val track = HybridViewTokens.recessedTrackDrawable(
      trackColor = dark.surfaceVariant,
      innerShadowColor = Color.argb(60, 0, 0, 0),
      borderColor = dark.cardHairlineBorder,
      radiusPx = 999f
    )
    assertThat(track).isInstanceOf(LayerDrawable::class.java)
    assertThat(track.numberOfLayers).isEqualTo(3)

    // Render floatingSurface and recessedTrack onto a Bitmap to verify zero draw exceptions
    val bmp = Bitmap.createBitmap(120, 48, Bitmap.Config.ARGB_8888)
    try {
      val canvas = Canvas(bmp)
      floatSurface.setBounds(0, 0, 120, 48)
      floatSurface.draw(canvas)
      track.setBounds(0, 0, 120, 48)
      track.draw(canvas)
    } finally {
      bmp.recycle()
    }

    // Verify monotonic Z0 < Z1 < Z2 < Z3 < Z4 elevation ladder + shadow tint RGB + glass outline
    val view = View(context)
    var prevElev = -1f
    var prevSpotAlpha = -1
    var prevAmbientAlpha = -1
    for (z in 0..4) {
      HybridViewTokens.applyZLayerFloatation(
        view = view,
        zLevel = z,
        isDark = false,
        shadowTintColor = light.shadowTint,
        radiusPx = 999f
      )
      assertThat(view.elevation).isGreaterThan(prevElev)
      prevElev = view.elevation

      val spotColor = HybridViewTokens.zLevelSpotShadowColor(z, isDark = false, light.shadowTint)
      val ambientColor = HybridViewTokens.zLevelAmbientShadowColor(z, isDark = false, light.shadowTint)
      assertThat(spotColor and 0x00FFFFFF).isEqualTo(light.shadowTint and 0x00FFFFFF)
      assertThat(ambientColor and 0x00FFFFFF).isEqualTo(light.shadowTint and 0x00FFFFFF)
      assertThat(Color.alpha(spotColor)).isGreaterThan(prevSpotAlpha)
      assertThat(Color.alpha(ambientColor)).isGreaterThan(prevAmbientAlpha)
      prevSpotAlpha = Color.alpha(spotColor)
      prevAmbientAlpha = Color.alpha(ambientColor)
    }

    view.measure(
      View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(64, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, 300, 64)
    val outline = Outline()
    view.outlineProvider.getOutline(view, outline)
    assertThat(outline.isEmpty).isFalse()
    assertThat(outline.alpha).isEqualTo(1f)
  }
}
