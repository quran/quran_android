package com.quran.labs.androidquran.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemePalette
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.common.ui.core.QuranAccent
import com.quran.labs.androidquran.common.ui.core.QuranColors
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.view.QuranSpinner
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class AppThemeProposalsTest {

  @After
  fun tearDown() {
    AppThemeStyle.select(AppThemeStyle.DEFAULT)
    AppThemeStyle.setOledDarkMode(false)
  }

  @Test
  fun `all theme styles satisfy WCAG AA contrast in both light and dark modes`() {
    for (style in AppThemeStyle.entries) {
      assertPaletteContrast("${style.storageKey} (light)", style.lightPalette)
      assertPaletteContrast("${style.storageKey} (dark)", style.darkPalette)
      assertPaletteContrast("${style.storageKey} (oled dark)", style.palette(isDark = true, oled = true))
    }
  }

  @Test
  fun `pure black OLED dark palettes use true #000000 surfaces, preserve duo-tone accents, pass WCAG AA, and localize labels`() {
    val pureBlack = Color(0xFF000000)
    val expectedCards = mapOf(
      AppThemeStyle.DEFAULT to Color(0xFF0B0C0E),
      AppThemeStyle.GREEN to Color(0xFF090D0B),
      AppThemeStyle.BLUE to Color(0xFF090C10),
      AppThemeStyle.BEIGE to Color(0xFF0D0B09),
      AppThemeStyle.GRAY to Color(0xFF0B0B0C)
    )
    val expectedElevated = mapOf(
      AppThemeStyle.DEFAULT to Color(0xFF131519),
      AppThemeStyle.GREEN to Color(0xFF101613),
      AppThemeStyle.BLUE to Color(0xFF10151C),
      AppThemeStyle.BEIGE to Color(0xFF161310),
      AppThemeStyle.GRAY to Color(0xFF131416)
    )

    try {
      AppThemeStyle.setOledDarkMode(true)
      for (style in AppThemeStyle.entries) {
        val label = "${style.storageKey} (oled)"
        val oled = style.palette(isDark = true, oled = true)
        assertWithMessage("$label 1-arg palette(true) respects AppThemeStyle.isOledDarkMode")
          .that(style.palette(true))
          .isEqualTo(oled)
        assertWithMessage("$label named palette(useDarkTheme = true) respects AppThemeStyle.isOledDarkMode")
          .that(style.palette(useDarkTheme = true))
          .isEqualTo(oled)
        assertWithMessage("$label light mode ignores OLED flag")
          .that(style.palette(isDark = false, oled = true))
          .isEqualTo(style.lightPalette)

        assertThat(oled.surface).isEqualTo(pureBlack)
        assertThat(oled.surfaceTopGlow).isEqualTo(pureBlack)
        assertThat(oled.toolbarBackground).isEqualTo(pureBlack)
        assertThat(oled.mushafOverlayBg).isEqualTo(pureBlack)
        assertThat(oled.shadowTint).isEqualTo(pureBlack)
        assertThat(oled.cardSurface).isEqualTo(expectedCards[style])
        assertThat(oled.surfaceElevated).isEqualTo(expectedElevated[style])
        assertThat(oled.divider).isEqualTo(Color(0xFF1F2329))
        assertThat(oled.glassFloatBg).isEqualTo(oled.surfaceElevated.copy(alpha = 0.94f))

        // Preserve every duo-tone accent and CTA token from darkPalette
        assertThat(oled.accent).isEqualTo(style.darkPalette.accent)
        assertThat(oled.editorialAccent).isEqualTo(style.darkPalette.editorialAccent)
        assertThat(oled.ctaBackground).isEqualTo(style.darkPalette.ctaBackground)
        assertThat(oled.onCta).isEqualTo(style.darkPalette.onCta)
        assertThat(oled.textPrimary).isEqualTo(style.darkPalette.textPrimary)
        assertThat(oled.textSecondary).isEqualTo(style.darkPalette.textSecondary)
        assertThat(oled.searchHighlight).isEqualTo(style.darkPalette.searchHighlight)

        assertPaletteContrast(label, oled)
        assertContrast(label, "onCta on ctaBackground", oled.onCta, oled.ctaBackground, 4.5)
      }

      AppThemeStyle.setOledDarkMode(false)
      for (style in AppThemeStyle.entries) {
        assertThat(style.palette(true)).isEqualTo(style.darkPalette)
      }
    } finally {
      AppThemeStyle.setOledDarkMode(false)
    }

    val appContext = ApplicationProvider.getApplicationContext<Context>()
    assertThat(appContext.getString(R.string.hybrid_appearance_oled_title)).isEqualTo("Pure Black (OLED)")
    assertThat(appContext.getString(R.string.hybrid_appearance_oled_subtitle))
      .isEqualTo("True black background for OLED screens")

    val arabicConfig = Configuration(appContext.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arabicContext = appContext.createConfigurationContext(arabicConfig)
    assertThat(arabicContext.getString(R.string.hybrid_appearance_oled_title))
      .isEqualTo("الوضع الليلي الكامل (OLED)")
    assertThat(arabicContext.getString(R.string.hybrid_appearance_oled_subtitle))
      .isEqualTo("خلفية سوداء نقية لشاشات OLED")
  }

  @Test
  fun `calm themes avoid harsh black and white, lift surfaces, and clearly separate highlights and badges`() {
    val pureBlack = Color(0xFF000000)
    val pureWhite = Color(0xFFFFFFFF)

    for (style in AppThemeStyle.entries) {
      val lightToolbarLuminance = ColorUtils.calculateLuminance(style.lightPalette.toolbarBackground.toArgb())
      assertWithMessage("${style.storageKey} light toolbar must be soft and light (luminance > 0.70)")
        .that(lightToolbarLuminance)
        .isGreaterThan(0.70)
      assertWithMessage("${style.storageKey} light toolbar must request dark status bar icons")
        .that(style.lightPalette.isLightToolbar)
        .isTrue()
      assertWithMessage("${style.storageKey} dark toolbar must request light status bar icons")
        .that(style.darkPalette.isLightToolbar)
        .isFalse()
    }

    for (style in AppThemeStyle.calmStyles) {
      for ((mode, palette) in listOf("light" to style.lightPalette, "dark" to style.darkPalette)) {
        val checkedTokens = buildList {
          add("surface" to palette.surface)
          if (mode == "dark") {
            add("surfaceElevated" to palette.surfaceElevated)
          }
          add("cardSurface" to palette.cardSurface)
          add("surfaceVariant" to palette.surfaceVariant)
          add("headerBackground" to palette.headerBackground)
          add("toolbarBackground" to palette.toolbarBackground)
          add("textPrimary" to palette.textPrimary)
          add("onToolbar" to palette.onToolbar)
          add("onAccent" to palette.onAccent)
          add("onTertiary" to palette.onTertiary)
        }
        for ((tokenName, color) in checkedTokens) {
          assertWithMessage("${style.storageKey} ($mode) $tokenName must not be harsh #000000")
            .that(color)
            .isNotEqualTo(pureBlack)
          assertWithMessage("${style.storageKey} ($mode) $tokenName must not be harsh #FFFFFF")
            .that(color)
            .isNotEqualTo(pureWhite)
        }

        // Light and Dark both maintain a subtle elevated surface step above canvas surface
        assertWithMessage("${style.storageKey} ($mode) surfaceElevated must differ gently from surface")
          .that(palette.surfaceElevated)
          .isNotEqualTo(palette.surface)

        // Duo-tone separation: Color 1 (accent) and Color 2 (editorialAccent) must be clearly distinct
        val duoDeltaE = cielabDeltaE(palette.accent, palette.editorialAccent)
        assertWithMessage("${style.storageKey} ($mode) editorialAccent vs accent Delta-E ($duoDeltaE)")
          .that(duoDeltaE)
          .isAtLeast(30.0)

        // Search highlight must clearly separate perceptually from primary body text
        val highlightDeltaE = cielabDeltaE(palette.searchHighlight, palette.textPrimary)
        assertWithMessage("${style.storageKey} ($mode) searchHighlight Delta-E vs textPrimary ($highlightDeltaE)")
          .that(highlightDeltaE)
          .isAtLeast(25.0)

        // Audio Manager downloaded (accent) vs undownloaded (tertiary) badges must be clearly scannable
        val badgeDeltaE = cielabDeltaE(palette.tertiary, palette.accent)
        assertWithMessage("${style.storageKey} ($mode) tertiary vs accent Delta-E ($badgeDeltaE)")
          .that(badgeDeltaE)
          .isAtLeast(40.0)
      }
    }
  }

  @Test
  fun `proposal 5 glass, mushaf overlay, and CTA tokens pass contrast and match reference palettes`() {
    for (style in AppThemeStyle.entries) {
      for ((mode, palette) in listOf("light" to style.lightPalette, "dark" to style.darkPalette)) {
        val label = "${style.storageKey} ($mode)"
        assertContrast(label, "onCta on ctaBackground", palette.onCta, palette.ctaBackground, 4.5)
        assertWithMessage("$label glassBg must be translucent").that(palette.glassBg.alpha).isLessThan(1.0f)
        assertWithMessage("$label glassFloatBg must be translucent").that(palette.glassFloatBg.alpha).isLessThan(1.0f)
        assertWithMessage("$label glassBorder must be translucent").that(palette.glassBorder.alpha).isLessThan(1.0f)
        assertWithMessage("$label navBg must be translucent").that(palette.navBg.alpha).isLessThan(1.0f)
        assertWithMessage("$label mushafOverlayBg must be opaque").that(palette.mushafOverlayBg.alpha).isEqualTo(1.0f)
        assertWithMessage("$label surfaceTopGlow must be opaque").that(palette.surfaceTopGlow.alpha).isEqualTo(1.0f)
        assertWithMessage("$label editorialAccent must differ from primary accent")
          .that(palette.editorialAccent)
          .isNotEqualTo(palette.accent)
        val expectedHighlightAlpha = if (palette.isLightToolbar) 0.16f else 0.20f
        assertWithMessage("$label ayahHighlightFill uses editorialAccent with calibrated alpha")
          .that(palette.ayahHighlightFill)
          .isEqualTo(palette.editorialAccent.copy(alpha = expectedHighlightAlpha))
      }
    }

    val defaultLight = AppThemeStyle.DEFAULT.lightPalette
    assertThat(defaultLight.surface).isEqualTo(Color(0xFFF6F5F1))
    assertThat(defaultLight.surfaceTopGlow).isEqualTo(Color(0xFFFFFDF9))
    assertThat(defaultLight.cardSurface).isEqualTo(Color(0xFFFFFFFF))
    assertThat(defaultLight.surfaceElevated).isEqualTo(Color(0xFFFFFFFF))
    assertThat(defaultLight.surfaceVariant).isEqualTo(Color(0xFFEAE8E1))
    assertThat(defaultLight.accent).isEqualTo(Color(0xFF23394F))
    assertThat(defaultLight.editorialAccent).isEqualTo(Color(0xFF9B6D22))
    assertThat(defaultLight.searchHighlight).isEqualTo(Color(0xFF7C5414))
    assertThat(QuranAccent.NavyGold.light).isEqualTo(Color(0xFF23394F))
    assertThat(QuranAccent.NavyGold.dark).isEqualTo(Color(0xFF89A5C4))
    assertThat(QuranAccent.NavyGold.onDark).isEqualTo(Color(0xFF15171A))
    val defaultQuranColors = QuranColors(Color.Black, Color.Green, Color.Yellow, Color.Yellow, Color.Gray)
    assertThat(defaultQuranColors.searchHighlight).isEqualTo(Color(0xFF7C5414))
    assertThat(defaultQuranColors.editorialAccent).isEqualTo(Color(0xFF9B6D22))

    val defaultDark = AppThemeStyle.DEFAULT.darkPalette
    assertThat(defaultDark.surface).isEqualTo(Color(0xFF15171A))
    assertThat(defaultDark.surfaceTopGlow).isEqualTo(Color(0xFF1F2329))
    assertThat(defaultDark.cardSurface).isEqualTo(Color(0xFF1E2126))
    assertThat(defaultDark.surfaceElevated).isEqualTo(Color(0xFF272B32))
    assertThat(defaultDark.surfaceVariant).isEqualTo(Color(0xFF30353E))
    assertThat(defaultDark.accent).isEqualTo(Color(0xFF89A5C4))
    assertThat(defaultDark.ctaBackground).isEqualTo(Color(0xFF2B3F56))
    assertThat(defaultDark.onCta).isEqualTo(Color(0xFFF2F5F8))
    assertThat(defaultDark.editorialAccent).isEqualTo(Color(0xFFE2B25C))
    assertThat(defaultDark.searchHighlight).isEqualTo(Color(0xFFE2B25C))
    assertThat(defaultDark.mushafOverlayBg).isEqualTo(Color(0xFF131518))

    val greenLight = AppThemeStyle.GREEN.lightPalette
    assertThat(greenLight.surface).isEqualTo(Color(0xFFEDF2EE))
    assertThat(greenLight.surfaceTopGlow).isEqualTo(Color(0xFFF7FBF7))
    assertThat(greenLight.cardSurface).isEqualTo(Color(0xFFF8FBF8))
    assertThat(greenLight.accent).isEqualTo(Color(0xFF234E36))
    assertThat(greenLight.editorialAccent).isEqualTo(Color(0xFF946F2D))

    val greenDark = AppThemeStyle.GREEN.darkPalette
    assertThat(greenDark.surface).isEqualTo(Color(0xFF0F1412))
    assertThat(greenDark.surfaceTopGlow).isEqualTo(Color(0xFF18231D))
    assertThat(greenDark.cardSurface).isEqualTo(Color(0xFF171F1B))
    assertThat(greenDark.surfaceElevated).isEqualTo(Color(0xFF202A24))
    assertThat(greenDark.surfaceVariant).isEqualTo(Color(0xFF29352E))
    assertThat(greenDark.accent).isEqualTo(Color(0xFF63AD82))
    assertThat(greenDark.ctaBackground).isEqualTo(Color(0xFF214933))
    assertThat(greenDark.editorialAccent).isEqualTo(Color(0xFFD5B16D))
    assertThat(greenDark.searchHighlight).isEqualTo(Color(0xFFD5B16D))
    assertThat(greenDark.mushafOverlayBg).isEqualTo(Color(0xFF0E1311))

    val blueDark = AppThemeStyle.BLUE.darkPalette
    assertThat(blueDark.surface).isEqualTo(Color(0xFF0F1319))
    assertThat(blueDark.surfaceTopGlow).isEqualTo(Color(0xFF18212E))
    assertThat(blueDark.cardSurface).isEqualTo(Color(0xFF171E28))
    assertThat(blueDark.surfaceElevated).isEqualTo(Color(0xFF202937))
    assertThat(blueDark.surfaceVariant).isEqualTo(Color(0xFF293446))
    assertThat(blueDark.accent).isEqualTo(Color(0xFF6BA0D6))
    assertThat(blueDark.ctaBackground).isEqualTo(Color(0xFF24476D))
    assertThat(blueDark.editorialAccent).isEqualTo(Color(0xFFD8A96B))
    assertThat(blueDark.searchHighlight).isEqualTo(Color(0xFFD8A96B))
    assertThat(blueDark.mushafOverlayBg).isEqualTo(Color(0xFF0E1218))

    val beigeLight = AppThemeStyle.BEIGE.lightPalette
    assertThat(beigeLight.surface).isEqualTo(Color(0xFFF5F0E6))
    assertThat(beigeLight.surfaceTopGlow).isEqualTo(Color(0xFFFFFBF4))
    assertThat(beigeLight.cardSurface).isEqualTo(Color(0xFFFCF9F3))
    assertThat(beigeLight.surfaceElevated).isEqualTo(Color(0xFFFFFDF9))
    assertThat(beigeLight.accent).isEqualTo(Color(0xFF754C29))
    assertThat(beigeLight.editorialAccent).isEqualTo(Color(0xFF295C52))
    assertThat(beigeLight.mushafOverlayBg).isEqualTo(Color(0xFFF8EBD5))
    assertContrast(
      "beige (light)",
      "textSecondary on surfaceVariant",
      beigeLight.textSecondary,
      beigeLight.surfaceVariant,
      5.5
    )

    val grayDark = AppThemeStyle.GRAY.darkPalette
    assertThat(grayDark.surface).isEqualTo(Color(0xFF161719))
    assertThat(grayDark.cardSurface).isEqualTo(Color(0xFF202225))
    assertThat(grayDark.accent).isEqualTo(Color(0xFFB2B7BF))
    assertThat(grayDark.editorialAccent).isEqualTo(Color(0xFFD3AA6B))
    assertThat(grayDark.mushafOverlayBg).isEqualTo(Color(0xFF141517))
  }

  @Test
  fun `theme style resolution defaults safely and uses plain color names only`() {
    assertThat(AppThemeStyle.fromPreference(null)).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("classic")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("legacy_theme_value")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("nonexistent_theme")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromKey(" GREEN ")).isEqualTo(AppThemeStyle.GREEN)

    assertThat(AppThemeStyle.entries.map { it.storageKey }).containsExactly(
      "default",
      "green",
      "blue",
      "beige",
      "gray"
    ).inOrder()

    assertThat(AppThemeStyle.calmStyles).containsExactly(
      AppThemeStyle.GREEN,
      AppThemeStyle.BLUE,
      AppThemeStyle.BEIGE,
      AppThemeStyle.GRAY
    ).inOrder()

    for (style in AppThemeStyle.entries) {
      assertThat(AppThemeStyle.fromPreference(style.storageKey)).isEqualTo(style)
      AppThemeStyle.select(style)
      assertThat(AppThemeStyle.current).isEqualTo(style)
      assertThat(QuranAccent.current).isEqualTo(style.accent)
    }

    val appContext = ApplicationProvider.getApplicationContext<Context>()
    assertThat(appContext.resources.getStringArray(R.array.theme_style_values).toList())
      .containsExactly("default", "green", "blue", "beige", "gray")
      .inOrder()
    assertThat(appContext.resources.getStringArray(R.array.theme_styles).toList())
      .containsExactly("Default", "Green", "Blue", "Beige", "Gray")
      .inOrder()
    assertThat(appContext.getString(R.string.prefs_theme_style_title)).isEqualTo("Theme color")

    val arabicConfig = Configuration(appContext.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arabicContext = appContext.createConfigurationContext(arabicConfig)
    assertThat(arabicContext.resources.getStringArray(R.array.theme_styles).toList())
      .containsExactly("افتراضي", "أخضر", "أزرق", "بيج", "رمادي")
      .inOrder()
    assertThat(arabicContext.getString(R.string.prefs_theme_style_title)).isEqualTo("لون السمة")
  }

  @Test
  fun `xml theme overlays resolve every token, dim disabled text, theme dialogs, and match compose`() {
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    for (useDark in listOf(false, true)) {
      val config = Configuration(appContext.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
          if (useDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      val modeContext = appContext.createConfigurationContext(config)

      for (style in AppThemeStyle.entries) {
        val label = "${style.storageKey} (${if (useDark) "dark" else "light"})"
        val themed = ContextThemeWrapper(modeContext, R.style.QuranToolBarDayNight)
        themed.theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)
        val expected = style.palette(useDark)

        assertAttrColor(label, "quranSurface", themed, R.attr.quranSurface, expected.surface)
        assertAttrColor(label, "quranSurfaceElevated", themed, R.attr.quranSurfaceElevated, expected.surfaceElevated)
        assertAttrColor(label, "quranHeaderBackground", themed, R.attr.quranHeaderBackground, expected.headerBackground)
        assertAttrColor(label, "quranHeaderText", themed, R.attr.quranHeaderText, expected.onHeader)
        assertAttrColor(label, "quranToolbarBackground", themed, R.attr.quranToolbarBackground, expected.toolbarBackground)
        assertAttrColor(label, "quranToolbarText", themed, R.attr.quranToolbarText, expected.onToolbar)
        assertAttrColor(label, "quranToolbarSecondaryText", themed, R.attr.quranToolbarSecondaryText, expected.onToolbarSecondary)
        assertAttrColor(label, "quranToolbarIconTint", themed, R.attr.quranToolbarIconTint, expected.onToolbar)
        assertAttrColor(label, "quranTabSelectedText", themed, R.attr.quranTabSelectedText, expected.tabSelectedText)
        assertAttrColor(label, "quranTabUnselectedText", themed, R.attr.quranTabUnselectedText, expected.tabUnselectedText)
        assertAttrColor(label, "quranTabIndicator", themed, R.attr.quranTabIndicator, expected.tabIndicator)
        assertAttrColor(label, "quranAccent", themed, R.attr.quranAccent, expected.accent)
        assertAttrColor(label, "quranOnAccent", themed, R.attr.quranOnAccent, expected.onAccent)
        assertAttrColor(label, "quranTextPrimary", themed, R.attr.quranTextPrimary, expected.textPrimary)
        assertAttrColor(label, "quranTextSecondary", themed, R.attr.quranTextSecondary, expected.textSecondary)
        assertAttrColor(label, "quranSuraNumberBg", themed, R.attr.quranSuraNumberBg, expected.suraBadgeBackground)
        assertAttrColor(label, "quranSuraNumberText", themed, R.attr.quranSuraNumberText, expected.suraBadgeText)
        assertAttrColor(label, "quranSuraNumberStroke", themed, R.attr.quranSuraNumberStroke, expected.suraBadgeStroke)
        assertAttrColor(label, "quranDivider", themed, R.attr.quranDivider, expected.divider)
        assertAttrColor(label, "quranSearchHighlight", themed, R.attr.quranSearchHighlight, expected.searchHighlight)
        assertWithMessage("$label quranIsLightToolbar")
          .that(ThemeUtil.resolveThemeBoolean(themed, R.attr.quranIsLightToolbar, !expected.isLightToolbar))
          .isEqualTo(expected.isLightToolbar)

        // Verify disabled preference text dims cleanly (state_enabled=false -> 38% alpha)
        assertDisabledTextAlpha(label, "textColorPrimary", themed, android.R.attr.textColorPrimary, expected.textPrimary)
        assertDisabledTextAlpha(label, "textColorSecondary", themed, android.R.attr.textColorSecondary, expected.textSecondary)

        // Verify AlertDialog / ThemeUtil.dialogThemeRes inherits the active theme's accent and floating surface
        val alertThemeValue = TypedValue()
        assertWithMessage("$label alertDialogTheme resolves")
          .that(themed.theme.resolveAttribute(androidx.appcompat.R.attr.alertDialogTheme, alertThemeValue, true))
          .isTrue()
        assertWithMessage("$label alertDialogTheme matches dialogThemeRes")
          .that(alertThemeValue.resourceId)
          .isEqualTo(ThemeUtil.dialogThemeRes(style))
        val dialogContext = ContextThemeWrapper(modeContext, ThemeUtil.dialogThemeRes(style))
        assertAttrColor(label, "QuranDialogTheme colorAccent", dialogContext, androidx.appcompat.R.attr.colorAccent, expected.accent)
        assertAttrColor(
          label,
          "QuranDialogTheme colorBackgroundFloating",
          dialogContext,
          android.R.attr.colorBackgroundFloating,
          expected.surfaceElevated
        )

        // Verify PagerActivity overlay statusBarColor and navigationBarColor match the tonal chrome
        val pagerThemed = ContextThemeWrapper(modeContext, R.style.QuranToolBar_Overlay)
        pagerThemed.theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)
        assertAttrColor(label, "navigationBarColor", pagerThemed, android.R.attr.navigationBarColor, expected.toolbarBackground)
        assertAttrColor(label, "statusBarColor", pagerThemed, android.R.attr.statusBarColor, expected.toolbarBackground)
      }
    }
  }

  @Test
  fun `inflated toolbars, overflow menus, dialogs, spinners, and action mode use calm surfaces without stock grey fallback`() {
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    val stockDarkGrey = 0xFF424242.toInt()
    val starkWhite = 0xFFFFFFFF.toInt()

    val toolbarLayouts = listOf(
      "quran_index" to R.layout.quran_index,
      "quran_page_activity" to R.layout.quran_page_activity,
      "search" to R.layout.search,
      "preferences" to R.layout.preferences,
      "bookmark_list" to R.layout.bookmark_list,
      "help" to R.layout.help,
      "about_us" to R.layout.about_us,
      "translation_manager" to R.layout.translation_manager
    )

    for (useDark in listOf(false, true)) {
      val config = Configuration(appContext.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
          if (useDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      val modeContext = appContext.createConfigurationContext(config)
      val defaultElevated = AppThemeStyle.DEFAULT.palette(useDark).surfaceElevated.toArgb()

      for (style in AppThemeStyle.entries) {
        val label = "${style.storageKey} (${if (useDark) "dark" else "light"})"
        val expected = style.palette(useDark)
        val expectedPopupBg = expected.surfaceElevated.toArgb()

        val themed = ContextThemeWrapper(modeContext, R.style.QuranToolBarDayNight)
        themed.theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)

        // ActionMode background & actionBarPopupTheme
        assertAttrColor(label, "actionModeBackground", themed, androidx.appcompat.R.attr.actionModeBackground, expected.toolbarBackground)
        val actionBarPopupValue = TypedValue()
        assertWithMessage("$label actionBarPopupTheme resolves")
          .that(themed.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarPopupTheme, actionBarPopupValue, true))
          .isTrue()
        assertWithMessage("$label actionBarPopupTheme matches popupThemeRes")
          .that(actionBarPopupValue.resourceId)
          .isEqualTo(ThemeUtil.popupThemeRes(style))

        // Dialog theme attributes (AppCompat & platform)
        for ((attrName, attrId) in listOf(
          "alertDialogTheme" to androidx.appcompat.R.attr.alertDialogTheme,
          "android:alertDialogTheme" to android.R.attr.alertDialogTheme,
          "dialogTheme" to androidx.appcompat.R.attr.dialogTheme,
          "android:dialogTheme" to android.R.attr.dialogTheme
        )) {
          val tv = TypedValue()
          assertWithMessage("$label $attrName resolves")
            .that(themed.theme.resolveAttribute(attrId, tv, true))
            .isTrue()
          assertWithMessage("$label $attrName matches dialogThemeRes")
            .that(tv.resourceId)
            .isEqualTo(ThemeUtil.dialogThemeRes(style))
        }

        val dialogContext = ContextThemeWrapper(modeContext, ThemeUtil.dialogThemeRes(style))
        assertAttrColor(label, "dialog colorBackground", dialogContext, android.R.attr.colorBackground, expected.surfaceElevated)
        assertAttrColor(label, "dialog colorBackgroundFloating", dialogContext, android.R.attr.colorBackgroundFloating, expected.surfaceElevated)
        assertDisabledTextAlpha(
          label,
          "dialog textColorAlertDialogListItem",
          dialogContext,
          androidx.appcompat.R.attr.textColorAlertDialogListItem,
          expected.textPrimary
        )
        val dialogBgValue = TypedValue()
        assertWithMessage("$label dialog windowBackground resolves")
          .that(dialogContext.theme.resolveAttribute(android.R.attr.windowBackground, dialogBgValue, true))
          .isTrue()
        val dialogBgDrawable = AppCompatResources.getDrawable(dialogContext, dialogBgValue.resourceId)
        val dialogShape = (dialogBgDrawable as? InsetDrawable)?.drawable as? GradientDrawable
        assertWithMessage("$label dialog windowBackground is themed rounded GradientDrawable").that(dialogShape).isNotNull()
        assertWithMessage("$label dialog windowBackground fill matches surfaceElevated")
          .that(dialogShape!!.color?.defaultColor)
          .isEqualTo(expectedPopupBg)
        assertPopupStyleFillColor(
          label,
          "dialog listPopupWindowStyle",
          dialogContext,
          androidx.appcompat.R.attr.listPopupWindowStyle,
          expectedPopupBg
        )

        // Verify dialog layouts inflate cleanly under dialogContext
        val dialogInflater = LayoutInflater.from(dialogContext)
        assertThat(dialogInflater.inflate(R.layout.jump_dialog, null)).isNotNull()
        assertThat(dialogInflater.inflate(R.layout.tag_dialog, null)).isNotNull()
        assertThat(dialogInflater.inflate(R.layout.tag_row, null)).isNotNull()

        // Inflate all 8 toolbar layouts (using QuranToolBar_Overlay for quran_page_activity)
        val pagerThemed = ContextThemeWrapper(modeContext, R.style.QuranToolBar_Overlay).apply {
          theme.applyStyle(ThemeUtil.themeOverlayRes(style), true)
        }
        for ((layoutName, layoutRes) in toolbarLayouts) {
          val hostContext = if (layoutRes == R.layout.quran_page_activity) pagerThemed else themed
          val root = LayoutInflater.from(hostContext).inflate(layoutRes, null)
          val toolbar = root.findViewById<Toolbar>(R.id.toolbar)
          assertWithMessage("$label $layoutName toolbar present").that(toolbar).isNotNull()
          assertWithMessage("$label $layoutName Toolbar.popupTheme must resolve active calm popup overlay")
            .that(toolbar.popupTheme)
            .isEqualTo(ThemeUtil.popupThemeRes(style))
          assertWithMessage("$label $layoutName Toolbar.popupTheme must not be stock AppCompat DayNight")
            .that(toolbar.popupTheme)
            .isNotEqualTo(androidx.appcompat.R.style.ThemeOverlay_AppCompat_DayNight)

          val popupContext = ContextThemeWrapper(toolbar.context, toolbar.popupTheme)
          assertAttrColor("$label $layoutName", "popup colorBackground", popupContext, android.R.attr.colorBackground, expected.surfaceElevated)
          assertAttrColor("$label $layoutName", "popup colorBackgroundFloating", popupContext, android.R.attr.colorBackgroundFloating, expected.surfaceElevated)
          assertAttrColor("$label $layoutName", "popup colorAccent", popupContext, androidx.appcompat.R.attr.colorAccent, expected.accent)
          assertDisabledTextAlpha("$label $layoutName", "popup textColorPrimary", popupContext, android.R.attr.textColorPrimary, expected.textPrimary)
          assertDisabledTextAlpha("$label $layoutName", "popup textColorSecondary", popupContext, android.R.attr.textColorSecondary, expected.textSecondary)

          val overflowFill = resolvePopupStyleFillColor(
            "$label $layoutName",
            "actionOverflowMenuStyle",
            popupContext,
            androidx.appcompat.R.attr.actionOverflowMenuStyle
          )
          assertWithMessage("$label $layoutName overflow menu background matches surfaceElevated")
            .that(overflowFill)
            .isEqualTo(expectedPopupBg)
          assertPopupStyleFillColor(
            "$label $layoutName",
            "popupMenuStyle",
            popupContext,
            androidx.appcompat.R.attr.popupMenuStyle,
            expectedPopupBg
          )
          assertPopupStyleFillColor(
            "$label $layoutName",
            "listPopupWindowStyle",
            popupContext,
            androidx.appcompat.R.attr.listPopupWindowStyle,
            expectedPopupBg
          )

          if (style != AppThemeStyle.DEFAULT) {
            assertWithMessage("$label $layoutName overflow popup must not be stock dark grey #424242")
              .that(overflowFill)
              .isNotEqualTo(stockDarkGrey)
            if (useDark) {
              assertWithMessage("$label $layoutName dark overflow popup must not be stark white #FFFFFF")
                .that(overflowFill)
                .isNotEqualTo(starkWhite)
              assertWithMessage("$label $layoutName dark overflow popup must be distinct from Default theme")
                .that(overflowFill)
                .isNotEqualTo(defaultElevated)
            }
          }

          // Verify PopupMenu constructs cleanly on the resolved popup context
          val popupMenu = PopupMenu(popupContext, toolbar)
          assertThat(popupMenu.menu).isNotNull()

          if (layoutRes == R.layout.quran_page_activity) {
            val spinner = root.findViewById<QuranSpinner>(R.id.spinner)
            assertThat(spinner).isNotNull()
            val spinnerBg = spinner.popupBackground as? GradientDrawable
            assertWithMessage("$label QuranSpinner popupBackground is themed GradientDrawable").that(spinnerBg).isNotNull()
            assertWithMessage("$label QuranSpinner popupBackground matches surfaceElevated")
              .that(spinnerBg!!.color?.defaultColor)
              .isEqualTo(expectedPopupBg)

            if (style != AppThemeStyle.DEFAULT) {
              val expectedAyahToolbarBg = if (useDark) expected.toolbarBackground else expected.accent
              assertAttrColor(
                label,
                "ayahToolbarBackground",
                pagerThemed,
                com.quran.labs.androidquran.common.toolbar.R.attr.ayahToolbarBackground,
                expectedAyahToolbarBg
              )
            }
          }
        }
      }
    }
  }

  private fun assertPopupStyleFillColor(
    label: String,
    styleName: String,
    context: Context,
    styleAttr: Int,
    expectedColor: Int
  ) {
    val fillColor = resolvePopupStyleFillColor(label, styleName, context, styleAttr)
    assertWithMessage("$label $styleName popupBackground fill color")
      .that(fillColor)
      .isEqualTo(expectedColor)
  }

  private fun resolvePopupStyleFillColor(
    label: String,
    styleName: String,
    context: Context,
    styleAttr: Int
  ): Int {
    val styleValue = TypedValue()
    assertWithMessage("$label $styleName resolves")
      .that(context.theme.resolveAttribute(styleAttr, styleValue, true))
      .isTrue()
    val ta = context.obtainStyledAttributes(styleValue.resourceId, intArrayOf(android.R.attr.popupBackground))
    try {
      val drawable = ta.getDrawable(0)
      val shape = drawable as? GradientDrawable
      assertWithMessage("$label $styleName android:popupBackground must be a themed GradientDrawable (got $drawable)")
        .that(shape)
        .isNotNull()
      return shape!!.color!!.defaultColor
    } finally {
      ta.recycle()
    }
  }

  @Test
  fun `pager status and nav bar icons match soft light toolbars and dark toolbars`() {
    assertThat(
      PagerActivity.shouldUseLightStatusBar(
        isActionBarHidden = false,
        isNightMode = false,
        isLightToolbar = true
      )
    ).isTrue()
    assertThat(
      PagerActivity.shouldUseLightSystemBars(
        isActionBarHidden = false,
        isNightMode = false,
        lightNavigationBar = true
      )
    ).isTrue()

    assertThat(
      PagerActivity.shouldUseLightStatusBar(
        isActionBarHidden = false,
        isNightMode = true,
        isLightToolbar = true
      )
    ).isTrue()

    assertThat(
      PagerActivity.shouldUseLightStatusBar(
        isActionBarHidden = false,
        isNightMode = true,
        isLightToolbar = false
      )
    ).isFalse()
    assertThat(
      PagerActivity.shouldUseLightSystemBars(
        isActionBarHidden = false,
        isNightMode = true,
        lightNavigationBar = false
      )
    ).isFalse()

    assertThat(
      PagerActivity.shouldUseLightStatusBar(
        isActionBarHidden = true,
        isNightMode = false,
        isLightToolbar = false
      )
    ).isTrue()
    assertThat(
      PagerActivity.shouldUseLightStatusBar(
        isActionBarHidden = true,
        isNightMode = true,
        isLightToolbar = true
      )
    ).isFalse()
  }

  private fun assertDisabledTextAlpha(
    label: String,
    attrName: String,
    context: Context,
    attrRes: Int,
    expectedEnabled: Color
  ) {
    val typedValue = TypedValue()
    assertWithMessage("$label $attrName resolves").that(context.theme.resolveAttribute(attrRes, typedValue, true)).isTrue()
    assertWithMessage("$label $attrName must reference a ColorStateList resource")
      .that(typedValue.resourceId)
      .isNotEqualTo(0)
    val csl = AppCompatResources.getColorStateList(context, typedValue.resourceId)
    assertWithMessage("$label $attrName ColorStateList").that(csl).isNotNull()

    val enabledColor = csl!!.getColorForState(intArrayOf(android.R.attr.state_enabled), 0)
    val disabledColor = csl.getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
    assertWithMessage("$label $attrName enabled color").that(enabledColor).isEqualTo(expectedEnabled.toArgb())
    assertWithMessage("$label $attrName disabled alpha (~0.38)")
      .that(AndroidColor.alpha(disabledColor))
      .isIn(85..110)
  }

  private fun assertAttrColor(
    label: String,
    attrName: String,
    context: Context,
    attrRes: Int,
    expected: Color
  ) {
    val resolved = ThemeUtil.resolveThemeColor(context, attrRes, 0)
    assertWithMessage("$label $attrName")
      .that(resolved)
      .isEqualTo(expected.toArgb())
  }

  private fun assertPaletteContrast(label: String, palette: AppThemePalette) {
    assertContrast(label, "textPrimary on surface", palette.textPrimary, palette.surface, 4.5)
    assertContrast(label, "textPrimary on surfaceElevated", palette.textPrimary, palette.surfaceElevated, 4.5)
    assertContrast(label, "textPrimary on cardSurface", palette.textPrimary, palette.cardSurface, 4.5)
    assertContrast(label, "textSecondary on surface", palette.textSecondary, palette.surface, 4.5)
    assertContrast(label, "textSecondary on surfaceElevated", palette.textSecondary, palette.surfaceElevated, 4.5)
    assertContrast(label, "onHeader on headerBackground", palette.onHeader, palette.headerBackground, 4.5)
    assertContrast(label, "onToolbar on toolbarBackground", palette.onToolbar, palette.toolbarBackground, 4.5)
    assertContrast(label, "onToolbarSecondary on toolbarBackground", palette.onToolbarSecondary, palette.toolbarBackground, 4.5)
    assertContrast(label, "tabSelectedText on toolbarBackground", palette.tabSelectedText, palette.toolbarBackground, 4.5)
    assertContrast(label, "tabUnselectedText on toolbarBackground", palette.tabUnselectedText, palette.toolbarBackground, 4.5)
    assertContrast(label, "tabIndicator on toolbarBackground", palette.tabIndicator, palette.toolbarBackground, 3.0)
    assertContrast(label, "accent on surface", palette.accent, palette.surface, 4.5)
    assertContrast(label, "accent on surfaceElevated", palette.accent, palette.surfaceElevated, 4.5)
    assertContrast(label, "onAccent on accent", palette.onAccent, palette.accent, 4.5)
    assertContrast(label, "onTertiary on tertiary", palette.onTertiary, palette.tertiary, 4.5)
    assertContrast(label, "suraBadgeText on suraBadgeBackground", palette.suraBadgeText, palette.suraBadgeBackground, 4.5)
    assertContrast(label, "searchHighlight on surface", palette.searchHighlight, palette.surface, 4.5)
    assertContrast(label, "searchHighlight on surfaceElevated", palette.searchHighlight, palette.surfaceElevated, 4.5)
    assertContrast(label, "searchHighlight on cardSurface", palette.searchHighlight, palette.cardSurface, 4.5)
    val highlightPillBg = palette.ayahHighlightFill.compositeOver(palette.cardSurface)
    assertContrast(label, "searchHighlight on tinted match pill", palette.searchHighlight, highlightPillBg, 4.5)
  }

  private fun assertContrast(
    label: String,
    pairName: String,
    foreground: Color,
    background: Color,
    minRatio: Double
  ) {
    val ratio = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
    assertWithMessage("$label -> $pairName contrast ratio ($ratio) must be >= $minRatio")
      .that(ratio)
      .isAtLeast(minRatio)
  }

  private fun cielabDeltaE(a: Color, b: Color): Double {
    val labA = DoubleArray(3)
    val labB = DoubleArray(3)
    ColorUtils.colorToLAB(a.toArgb(), labA)
    ColorUtils.colorToLAB(b.toArgb(), labB)
    val dl = labA[0] - labB[0]
    val da = labA[1] - labB[1]
    val db = labA[2] - labB[2]
    return sqrt(dl * dl + da * da + db * db)
  }
}
