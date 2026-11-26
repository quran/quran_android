package com.quran.labs.androidquran.hybrid.reader

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract.StudyTab
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.util.QuranUtils
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridReaderFloatingLayersTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @After
  fun tearDown() {
    AppThemeStyle.select(AppThemeStyle.DEFAULT)
  }

  private fun measureAndLayout(view: View, widthPx: Int = 720, heightPx: Int = 1604) {
    view.measure(
      View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    view.layout(0, 0, widthPx, heightPx)
  }

  @Test
  fun `top pills, floating ayah bar, and audio capsule apply Z3 floatation and duo-tone Color 1 vs Color 2 roles`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    measureAndLayout(overlay)

    for (style in AppThemeStyle.entries) {
      for (isDark in listOf(false, true)) {
        val mode = if (isDark) Constants.THEME_DARK else Constants.THEME_LIGHT
        val palette = HybridViewPalette.from(style, isDark)
        overlay.render(
          HybridReaderUiState(
            currentPage = 1,
            suraNumber = 1,
            suraName = "الفاتحة",
            isCurrentPageBookmarked = true,
            selectedStart = SuraAyah(1, 7),
            selectedEnd = SuraAyah(1, 7),
            selectedSuraName = "سورة الفاتحة",
            isSelectedAyahBookmarked = true,
            ayahOverlayBounds = AyahOverlayBounds(
              firstLineLeft = 100f,
              firstLineTop = 500f,
              firstLineRight = 600f,
              firstLineBottom = 560f,
              lastLineLeft = 120f,
              lastLineTop = 560f,
              lastLineRight = 560f,
              lastLineBottom = 620f
            ),
            appearanceState = ReaderAppearanceState(
              appThemeStyle = style,
              appThemeMode = mode
            )
          )
        )
        measureAndLayout(overlay)

        val z3Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(3, isDark))
        val z2Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark))
        val z1Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark))

        val navPill = overlay.navPillForTest()
        val toolsPill = overlay.toolsPillForTest()
        val capsule = overlay.audioCapsuleCardForTest()
        val capsulePlay = overlay.capsulePlayBtnForTest()
        val ayahBar = overlay.ayahActionPillForTest()!!
        val ayahTag = overlay.ayahSelectionTagPillForTest()!!

        assertWithMessage("$style/$mode navPill Z3").that(navPill.elevation).isWithin(0.1f).of(z3Elevation)
        assertThat(navPill.background).isInstanceOf(LayerDrawable::class.java)
        assertWithMessage("$style/$mode toolsPill Z3").that(toolsPill.elevation).isWithin(0.1f).of(z3Elevation)
        assertThat(toolsPill.background).isInstanceOf(LayerDrawable::class.java)
        assertWithMessage("$style/$mode capsule Z3").that(capsule.elevation).isWithin(0.1f).of(z3Elevation)
        assertThat(capsule.background).isInstanceOf(LayerDrawable::class.java)
        assertWithMessage("$style/$mode capsulePlay Z2").that(capsulePlay.elevation).isWithin(0.1f).of(z2Elevation)
        assertWithMessage("$style/$mode ayahBar Z3").that(ayahBar.elevation).isWithin(0.1f).of(z3Elevation)
        assertThat(ayahBar.background).isInstanceOf(LayerDrawable::class.java)
        assertWithMessage("$style/$mode ayahTag Z1").that(ayahTag.elevation).isWithin(0.1f).of(z1Elevation)
        assertThat(ayahTag.background).isInstanceOf(LayerDrawable::class.java)
        assertWithMessage("$style/$mode ayahTag Color 2").that(ayahTag.currentTextColor).isEqualTo(palette.editorialAccent)
        assertThat(ayahTag.text.toString()).doesNotContain("\u00B7")

        val capsulePlayOutline = Outline()
        capsulePlay.outlineProvider.getOutline(capsulePlay, capsulePlayOutline)
        assertThat(capsulePlayOutline.isEmpty).isFalse()
        assertThat(capsulePlayOutline.alpha).isEqualTo(1f)
        val c1SpotShadow = HybridViewTokens.zLevelSpotShadowColor(2, isDark, palette.ctaBackground)
        assertThat(c1SpotShadow and 0x00FFFFFF).isEqualTo(palette.ctaBackground and 0x00FFFFFF)
      }
    }
  }

  @Test
  fun `detached floating 2-tab study sheet uses compact Recitation without Tafsir peek, Z1 Tafsir card with list button and expand-collapse`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    val navInsetPx = 48
    overlay.applyWindowInsets(statusTop = 64, navBottom = navInsetPx)
    measureAndLayout(overlay)

    val palette = HybridViewPalette.from(AppThemeStyle.GREEN, isDark = true)
    val recitationState = HybridReaderUiState(
      currentPage = 1,
      suraNumber = 1,
      suraName = "الفاتحة",
      qariFullName = "المنشاوي",
      isStudyDrawerOpen = true,
      selectedStudyTab = StudyTab.RECITATION,
      playbackStatus = PlaybackStatus.PLAYING,
      tafsirState = TafsirDisplayState(
        loadState = TafsirLoadState.LOADED,
        sura = 1,
        ayah = 1,
        suraName = "سورة الفاتحة",
        sourceName = "التفسير الميسر",
        selectedFilename = "ar.muyassar",
        availableSources = listOf(
          TafsirSourceItem("ar.muyassar", "التفسير الميسر", isArabic = true),
          TafsirSourceItem("ar.katheer", "تفسير ابن كثير", isArabic = true)
        ),
        snippetText = "أبتدئ قراءة القرآن باسم الله",
        fullText = "أبتدئ قراءة القرآن باسم الله"
      ),
      appearanceState = ReaderAppearanceState(
        appThemeStyle = AppThemeStyle.GREEN,
        appThemeMode = Constants.THEME_DARK
      )
    )
    overlay.render(recitationState)
    measureAndLayout(overlay)

    val scrim = overlay.studyDrawerScrimForTest()!!
    val sheet = overlay.studyDrawerSheetForTest()!!
    val lp = sheet.layoutParams as FrameLayout.LayoutParams
    val expectedInsetH = HybridViewTokens.dp(activity, 10f)
    val expectedInsetB = navInsetPx + HybridViewTokens.dp(activity, 12f)

    // Detached geometry & compact Apple Recitation height (~236dp, no empty Tafsir void!)
    assertThat(lp.leftMargin).isEqualTo(expectedInsetH)
    assertThat(lp.rightMargin).isEqualTo(expectedInsetH)
    assertThat(lp.bottomMargin).isEqualTo(expectedInsetB)
    assertThat(sheet.left).isEqualTo(expectedInsetH)
    assertThat(sheet.right).isEqualTo(720 - expectedInsetH)
    assertThat(sheet.bottom).isEqualTo(1604 - expectedInsetB)
    val compactRecHeight = sheet.height
    assertThat(compactRecHeight).isAtMost(HybridViewTokens.dp(activity, 250f))

    // Z4 surface + 5-stop static vertical gradient fade scrim visible above sheet top edge
    val z4Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(4, isDark = true))
    assertThat(sheet.elevation).isWithin(0.1f).of(z4Elevation)
    assertThat(sheet.background).isInstanceOf(LayerDrawable::class.java)
    assertThat(scrim.background).isInstanceOf(GradientDrawable::class.java)
    val scrimGradient = scrim.background as GradientDrawable
    assertThat(scrimGradient.orientation).isEqualTo(GradientDrawable.Orientation.TOP_BOTTOM)
    assertThat(scrimGradient.colors?.size).isEqualTo(5)
    assertThat(scrimGradient.colors!![0]).isEqualTo(Color.TRANSPARENT)
    assertThat(Color.alpha(scrimGradient.colors!![2])).isGreaterThan(0)
    assertThat(Color.alpha(scrimGradient.colors!![4])).isGreaterThan(Color.alpha(scrimGradient.colors!![2]))

    // Recessed 2-tab rail ONLY (Recitation | Tafsir — ZERO Appearance tab inside Study Sheet!)
    val tabRail = sheet.getChildAt(1) as ViewGroup
    assertThat(tabRail.background).isInstanceOf(LayerDrawable::class.java)
    val tabs = overlay.drawerTabButtonsForTest()
    assertThat(tabs.keys).containsExactly(StudyTab.RECITATION, StudyTab.TAFSIR)
    assertThat(tabs.containsKey(StudyTab.APPEARANCE)).isFalse()
    val z2Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(2, isDark = true))
    assertThat(tabs.getValue(StudyTab.RECITATION).elevation).isWithin(0.1f).of(z2Elevation)
    assertThat(tabs.getValue(StudyTab.RECITATION).currentTextColor).isEqualTo(palette.accent)
    assertThat(tabs.getValue(StudyTab.TAFSIR).elevation).isEqualTo(0f)
    assertThat(tabs.getValue(StudyTab.TAFSIR).currentTextColor).isEqualTo(palette.textSecondary)

    // Recitation controls + Color-1-tinted Z2 Play CTA + active Stop button + LTR speed label
    val recPlay = overlay.recPlayBtnForTest()!!
    val recStop = overlay.recStopBtnForTest()!!
    val recSpeed = overlay.recSpeedBtnForTest()!!
    assertThat(recPlay.elevation).isWithin(0.1f).of(z2Elevation)
    assertThat(recStop.visibility).isEqualTo(View.VISIBLE)
    assertThat(recStop.isEnabled).isTrue()
    assertThat(recStop.contentDescription).isEqualTo(context.getString(R.string.stop))
    assertThat(recSpeed.textDirection).isEqualTo(View.TEXT_DIRECTION_LTR)
    val recPlayOutline = Outline()
    recPlay.outlineProvider.getOutline(recPlay, recPlayOutline)
    assertThat(recPlayOutline.isEmpty).isFalse()
    assertThat(recPlayOutline.alpha).isEqualTo(1f)
    val sheetOutline = Outline()
    sheet.outlineProvider.getOutline(sheet, sheetOutline)
    assertThat(sheetOutline.isEmpty).isFalse()
    assertThat(sheetOutline.alpha).isEqualTo(1f)
    val recPlaySpot = HybridViewTokens.zLevelSpotShadowColor(2, isDark = true, shadowTintColor = palette.ctaBackground)
    assertThat(recPlaySpot and 0x00FFFFFF).isEqualTo(palette.ctaBackground and 0x00FFFFFF)

    // Verify ZERO Tafsir peek text inside Recitation tab
    val recTexts = ArrayList<String>()
    fun collectTexts(v: View) {
      if (v is TextView && v.visibility == View.VISIBLE) recTexts.add(v.text.toString())
      if (v is ViewGroup && v.visibility == View.VISIBLE) {
        for (i in 0 until v.childCount) collectTexts(v.getChildAt(i))
      }
    }
    collectTexts((sheet.getChildAt(2) as ViewGroup).getChildAt(0))
    assertThat(recTexts.none { it.contains("التفسير الميسر") || it.contains("أبتدئ قراءة") }).isTrue()

    // Switch to Tafsir tab -> Z1 inner card + dedicated قائمة التفاسير button + taller compact Tafsir height
    val tafsirTabState = recitationState.copy(selectedStudyTab = StudyTab.TAFSIR)
    overlay.render(tafsirTabState)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    measureAndLayout(overlay)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(tabs.getValue(StudyTab.TAFSIR).elevation).isWithin(0.1f).of(z2Elevation)
    assertThat(tabs.getValue(StudyTab.RECITATION).elevation).isEqualTo(0f)
    val compactTafsirHeight = sheet.height
    assertThat(compactTafsirHeight).isGreaterThan(compactRecHeight)

    val tafsirBodyCard = overlay.tafsirBodyCardForTest()!!
    val z1Elevation = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(1, isDark = true))
    assertThat(tafsirBodyCard.elevation).isWithin(0.1f).of(z1Elevation)
    assertThat(tafsirBodyCard.background).isInstanceOf(LayerDrawable::class.java)
    val tafsirListBtn = overlay.tafsirListBtnForTest()!!
    assertThat(tafsirListBtn.visibility).isEqualTo(View.VISIBLE)

    // Overflowing Tafsir text reveals expand/collapse button and expands height smoothly toward ~80% vh
    val expandBtn = overlay.tafsirExpandBtnForTest()!!
    overlay.setTafsirOverflowForTest(true)
    assertThat(expandBtn.visibility).isEqualTo(View.VISIBLE)
    assertThat(expandBtn.contentDescription).isEqualTo(context.getString(R.string.hybrid_reader_tafsir_expand_cd))

    overlay.toggleTafsirExpandedForTest()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    measureAndLayout(overlay)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(overlay.isTafsirExpandedForTest()).isTrue()
    assertThat(expandBtn.contentDescription).isEqualTo(context.getString(R.string.hybrid_reader_tafsir_collapse_cd))
    assertThat((sheet.layoutParams as FrameLayout.LayoutParams).height).isGreaterThan(compactTafsirHeight)

    // Switching back to Recitation automatically resets expanded state & restores compact Recitation height
    overlay.render(recitationState)
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    measureAndLayout(overlay)
    assertThat(sheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(overlay.isTafsirExpandedForTest()).isFalse()
    assertThat((sheet.layoutParams as FrameLayout.LayoutParams).height).isEqualTo(compactRecHeight)
  }

  @Test
  fun `study sheet stop button dispatches onStop callback and Tafsir tab localizes English metadata cleanly`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    var stopCount = 0

    // Directly exercise click wiring via render + synthetic stop callback
    val field = HybridReaderOverlayView::class.java.getDeclaredField("onStop")
    field.isAccessible = true
    field.set(overlay, { stopCount++ })

    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraNumber = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = StudyTab.RECITATION,
        playbackStatus = PlaybackStatus.PLAYING
      )
    )
    val stopBtn = overlay.recStopBtnForTest()!!
    assertThat(stopBtn.visibility).isEqualTo(View.VISIBLE)
    assertThat(stopBtn.isEnabled).isTrue()
    stopBtn.performClick()
    assertThat(stopCount).isEqualTo(1)

    overlay.render(
      HybridReaderUiState(
        currentPage = 12,
        suraNumber = 2,
        suraName = "البقرة",
        firstAyahOnPage = 77,
        isStudyDrawerOpen = true,
        selectedStudyTab = StudyTab.RECITATION,
        playbackStatus = null
      )
    )
    assertThat(stopBtn.visibility).isEqualTo(View.GONE)
    assertThat(stopBtn.isEnabled).isFalse()

    // Idle capsule and Study Sheet subtitles consistently reflect page first ayah (77), not stale ayah 1
    val localized77 = QuranUtils.getLocalizedNumber(77)
    assertThat(overlay.recQariSubtitleForTest()!!.text.toString()).contains(localized77)
    assertThat(overlay.capsuleMetaForTest().text.toString()).contains(localized77)

    // Switch to Tafsir tab with English metadata -> source chip localizes to Arabic title cleanly
    overlay.render(
      HybridReaderUiState(
        currentPage = 12,
        suraNumber = 2,
        suraName = "البقرة",
        firstAyahOnPage = 77,
        isStudyDrawerOpen = true,
        selectedStudyTab = StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          loadState = TafsirLoadState.LOADED,
          sura = 2,
          ayah = 77,
          suraName = "سورة البقرة",
          sourceName = "Arabic Muyassar Tafseer",
          selectedFilename = "ar.muyassar",
          availableSources = listOf(
            TafsirSourceItem("ar.muyassar", "Arabic Muyassar Tafseer", isArabic = true),
            TafsirSourceItem("ar.katheer", "Arabic Ibn Kathir Tafseer", isArabic = true)
          ),
          fullText = "تفسير الآية"
        )
      )
    )
    val allTafsirTexts = ArrayList<String>()
    fun collect(v: View) {
      if (v is TextView) allTafsirTexts.add(v.text.toString())
      if (v is ViewGroup) {
        for (i in 0 until v.childCount) collect(v.getChildAt(i))
      }
    }
    collect(overlay.studyDrawerSheetForTest()!!)
    assertThat(allTafsirTexts).contains("التفسير الميسر")
    assertThat(allTafsirTexts).contains("تفسير ابن كثير")
    assertThat(allTafsirTexts.none { it.contains("Arabic Muyassar Tafseer") }).isTrue()
  }

  @Test
  fun `standalone Reader Appearance Sheet renders detached Z4 card with opaque fill and Tafsir list button opens picker`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    overlay.applyWindowInsets(statusTop = 64, navBottom = 48)
    measureAndLayout(overlay)

    // Standalone Appearance Sheet opens independently without building Study Drawer
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraNumber = 1,
        suraName = "الفاتحة",
        isAppearanceSheetOpen = true,
        isStudyDrawerOpen = false,
        appearanceState = ReaderAppearanceState(
          appThemeStyle = AppThemeStyle.BEIGE,
          appThemeMode = Constants.THEME_LIGHT
        )
      )
    )
    measureAndLayout(overlay)

    assertThat(overlay.hasAppearanceSheetBuiltForTest()).isTrue()
    assertThat(overlay.isAppearanceSheetRenderVisibleForTest()).isTrue()
    assertThat(overlay.hasStudyDrawerBuiltForTest()).isFalse()
    val appCard = overlay.appearanceSheetCardForTest()!!
    assertThat(appCard.visibility).isEqualTo(View.VISIBLE)
    assertThat(appCard.clipChildren).isFalse()
    assertThat(appCard.clipToPadding).isFalse()
    val z4Light = HybridViewTokens.dpF(activity, HybridViewTokens.zLevelElevationDp(4, isDark = false))
    assertThat(appCard.elevation).isWithin(0.1f).of(z4Light)
    assertThat(appCard.background).isInstanceOf(LayerDrawable::class.java)
    assertThat(overlay.appearanceSheetDoneBtnForTest()!!.visibility).isEqualTo(View.VISIBLE)

    // Close Appearance Sheet -> open Study Drawer on Tafsir tab -> tap قائمة التفاسير -> opens detached picker
    var managerOpenedCount = 0
    val managerField = HybridReaderOverlayView::class.java.getDeclaredField("onOpenTafsirManager")
    managerField.isAccessible = true
    managerField.set(overlay, { managerOpenedCount++ })

    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraNumber = 1,
        suraName = "الفاتحة",
        isAppearanceSheetOpen = false,
        isStudyDrawerOpen = true,
        selectedStudyTab = StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          loadState = TafsirLoadState.LOADED,
          sura = 1,
          ayah = 1,
          suraName = "سورة الفاتحة",
          selectedFilename = "ar.muyassar",
          availableSources = listOf(
            TafsirSourceItem("ar.muyassar", "التفسير الميسر", isArabic = true),
            TafsirSourceItem("ar.saadi", "تفسير السعدي", isArabic = true)
          ),
          fullText = "تفسير"
        )
      )
    )
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    measureAndLayout(overlay)
    assertThat(appCard.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(overlay.isAppearanceSheetRenderVisibleForTest()).isFalse()

    overlay.tafsirListBtnForTest()!!.performClick()
    assertThat(overlay.isTafsirPickerOpen()).isTrue()
    val pickerSheet = overlay.tafsirPickerSheetForTest()!!
    assertThat(pickerSheet.visibility).isEqualTo(View.VISIBLE)
    assertThat(pickerSheet.clipChildren).isFalse()

    // Tapping "Download more tafsirs" CTA dismisses picker and invokes manager callback
    overlay.tafsirPickerDownloadCtaForTest()!!.performClick()
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(350, TimeUnit.MILLISECONDS)
    assertThat(pickerSheet.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    assertThat(managerOpenedCount).isEqualTo(1)
    assertThat(overlay.isTafsirPickerOpen()).isFalse()

    // With 0 installed tafsirs, tapping قائمة التفاسير jumps straight to manager
    overlay.render(
      HybridReaderUiState(
        currentPage = 1,
        suraNumber = 1,
        suraName = "الفاتحة",
        isStudyDrawerOpen = true,
        selectedStudyTab = StudyTab.TAFSIR,
        tafsirState = TafsirDisplayState(
          loadState = TafsirLoadState.EMPTY,
          sura = 1,
          ayah = 1,
          suraName = "سورة الفاتحة",
          availableSources = emptyList()
        )
      )
    )
    overlay.tafsirListBtnForTest()!!.performClick()
    assertThat(managerOpenedCount).isEqualTo(2)
  }

  @Test
  fun `Light floating pills, ayah bar, capsule, and detached study sheet use 100 percent opaque base fills to prevent Mushaf bleed-through`() {
    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    val overlay = HybridReaderOverlayView(activity)
    activity.setContentView(overlay)
    measureAndLayout(overlay)

    overlay.render(
      HybridReaderUiState(
        currentPage = 12,
        suraNumber = 2,
        suraName = "البقرة",
        selectedStart = SuraAyah(2, 79),
        selectedEnd = SuraAyah(2, 79),
        selectedSuraName = "سورة البقرة",
        ayahOverlayBounds = AyahOverlayBounds(100f, 500f, 600f, 560f, 100f, 560f, 560f, 620f),
        appearanceState = ReaderAppearanceState(
          appThemeStyle = AppThemeStyle.DEFAULT,
          appThemeMode = Constants.THEME_LIGHT
        )
      )
    )
    overlay.render(
      HybridReaderUiState(
        currentPage = 12,
        suraNumber = 2,
        suraName = "البقرة",
        isStudyDrawerOpen = true,
        appearanceState = ReaderAppearanceState(
          appThemeStyle = AppThemeStyle.DEFAULT,
          appThemeMode = Constants.THEME_LIGHT
        )
      )
    )

    fun assertBaseLayerOpaque(label: String, view: View) {
      val layer = view.background as LayerDrawable
      val base = layer.getDrawable(0) as GradientDrawable
      val colors = base.colors
      if (colors != null) {
        colors.forEach { c ->
          assertWithMessage("$label gradient stop alpha").that(Color.alpha(c)).isEqualTo(255)
        }
      } else {
        val solidColor = base.color?.defaultColor
        assertWithMessage("$label solid fill alpha").that(Color.alpha(solidColor!!)).isEqualTo(255)
      }
    }

    assertBaseLayerOpaque("navPill", overlay.navPillForTest())
    assertBaseLayerOpaque("toolsPill", overlay.toolsPillForTest())
    assertBaseLayerOpaque("audioCapsuleCard", overlay.audioCapsuleCardForTest())
    assertBaseLayerOpaque("ayahActionPill", overlay.ayahActionPillForTest()!!)
    assertBaseLayerOpaque("ayahSelectionTagPill", overlay.ayahSelectionTagPillForTest()!!)
    assertBaseLayerOpaque("studyDrawerSheet", overlay.studyDrawerSheetForTest()!!)
  }

  @Test
  fun `AppThemeStyle ayahHighlightFill glows in Color 2 editorialAccent at 16 percent light and 20 percent dark across all themes`() {
    for (style in AppThemeStyle.entries) {
      val light = style.palette(useDarkTheme = false)
      val dark = style.palette(useDarkTheme = true)
      val lightFill = light.ayahHighlightFill.toArgb()
      val darkFill = dark.ayahHighlightFill.toArgb()
      val lightEditorial = light.editorialAccent.toArgb()
      val darkEditorial = dark.editorialAccent.toArgb()

      assertThat(lightFill and 0x00FFFFFF).isEqualTo(lightEditorial and 0x00FFFFFF)
      assertThat(Color.alpha(lightFill)).isIn(40..42)
      assertThat(darkFill and 0x00FFFFFF).isEqualTo(darkEditorial and 0x00FFFFFF)
      assertThat(Color.alpha(darkFill)).isIn(50..52)
    }
  }
}
