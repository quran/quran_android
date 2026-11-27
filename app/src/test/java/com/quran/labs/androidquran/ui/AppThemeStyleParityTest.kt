package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.util.ThemeUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class AppThemeStyleParityTest {

  @Test
  fun `all 10 AppThemeStyle palettes match XML resources and satisfy WCAG AA text contrast`() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val prefixes = mapOf(
      AppThemeStyle.DEFAULT to "default",
      AppThemeStyle.GREEN to "green",
      AppThemeStyle.BLUE to "blue",
      AppThemeStyle.BEIGE to "beige",
      AppThemeStyle.GRAY to "gray"
    )

    for ((style, prefix) in prefixes) {
      for (isDark in listOf(false, true)) {
        val cfg = Configuration(base.resources.configuration).apply {
          uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val themedCtx = base.createConfigurationContext(cfg)
        val ktPalette = style.palette(isDark)
        val viewPalette = HybridViewPalette.from(style, isDark)

        val checks = mapOf(
          "${prefix}_theme_surface" to ktPalette.surface.toArgb(),
          "${prefix}_theme_surface_elevated" to ktPalette.surfaceElevated.toArgb(),
          "${prefix}_theme_header_bg" to ktPalette.headerBackground.toArgb(),
          "${prefix}_theme_header_text" to ktPalette.onHeader.toArgb(),
          "${prefix}_theme_toolbar_bg" to ktPalette.toolbarBackground.toArgb(),
          "${prefix}_theme_toolbar_text" to ktPalette.onToolbar.toArgb(),
          "${prefix}_theme_toolbar_secondary_text" to ktPalette.onToolbarSecondary.toArgb(),
          "${prefix}_theme_toolbar_icon" to ktPalette.onToolbar.toArgb(),
          "${prefix}_theme_tab_selected" to ktPalette.tabSelectedText.toArgb(),
          "${prefix}_theme_tab_unselected" to ktPalette.tabUnselectedText.toArgb(),
          "${prefix}_theme_tab_indicator" to ktPalette.tabIndicator.toArgb(),
          "${prefix}_theme_accent" to ktPalette.accent.toArgb(),
          "${prefix}_theme_on_accent" to ktPalette.onAccent.toArgb(),
          "${prefix}_theme_text_primary" to ktPalette.textPrimary.toArgb(),
          "${prefix}_theme_text_secondary" to ktPalette.textSecondary.toArgb(),
          "${prefix}_theme_sura_badge_bg" to ktPalette.suraBadgeBackground.toArgb(),
          "${prefix}_theme_sura_badge_text" to ktPalette.suraBadgeText.toArgb(),
          "${prefix}_theme_sura_badge_stroke" to ktPalette.suraBadgeStroke.toArgb(),
          "${prefix}_theme_divider" to ktPalette.divider.toArgb(),
          "${prefix}_theme_search_highlight" to ktPalette.searchHighlight.toArgb()
        )
        for ((resName, ktColor) in checks) {
          val resId = themedCtx.resources.getIdentifier(resName, "color", themedCtx.packageName)
          assertWithMessage("Missing color $resName").that(resId).isNotEqualTo(0)
          val xmlColor = ContextCompat.getColor(themedCtx, resId)
          assertWithMessage("Mismatch $resName (isDark=$isDark)").that(xmlColor).isEqualTo(ktColor)
        }

        assertThat(viewPalette.surface).isEqualTo(ktPalette.surface.toArgb())
        assertThat(viewPalette.cardSurface).isEqualTo(ktPalette.cardSurface.toArgb())
        assertThat(viewPalette.accent).isEqualTo(ktPalette.accent.toArgb())
        assertThat(viewPalette.editorialAccent).isEqualTo(ktPalette.editorialAccent.toArgb())
        assertThat(viewPalette.surfaceTopGlow).isEqualTo(ktPalette.surfaceTopGlow.toArgb())
        assertThat(viewPalette.glassFloatBg).isEqualTo(ktPalette.glassFloatBg.toArgb())
        assertThat(viewPalette.shadowTint).isEqualTo(ktPalette.shadowTint.toArgb())
        assertThat(viewPalette.medallionFill).isEqualTo(viewPalette.editorialSoftBg)
        assertThat(viewPalette.medallionBorder).isEqualTo(viewPalette.editorialAccent)
        assertThat(viewPalette.searchHighlightBg).isEqualTo(viewPalette.editorialHighlightBg)

        val contrast = ColorUtils.calculateContrast(ktPalette.textPrimary.toArgb(), ktPalette.surface.toArgb())
        assertWithMessage("Low contrast for $style isDark=$isDark: $contrast")
          .that(contrast).isAtLeast(4.5)
      }
    }
  }

  @Test
  fun `AppThemeStyle fromPreference and ThemeUtil isDarkAppearance handle unknown and edge inputs`() {
    assertThat(AppThemeStyle.fromPreference(null)).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference("unknown_style")).isEqualTo(AppThemeStyle.DEFAULT)
    assertThat(AppThemeStyle.fromPreference(" BLUE ")).isEqualTo(AppThemeStyle.BLUE)

    assertThat(ThemeUtil.isDarkAppearance("dark", systemNight = false)).isTrue()
    assertThat(ThemeUtil.isDarkAppearance("light", systemNight = true)).isFalse()
    assertThat(ThemeUtil.isDarkAppearance("auto", systemNight = true)).isTrue()
    assertThat(ThemeUtil.isDarkAppearance("invalid", systemNight = false)).isFalse()
    assertThat(ThemeUtil.isDarkAppearance("invalid", systemNight = true)).isTrue()

    // Arabic RTL context resolves hybrid strings without formatting failures
    val base = ApplicationProvider.getApplicationContext<Context>()
    val arCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("ar")) }
    )
    assertThat(arCtx.getString(R.string.hybrid_search_no_results, "%1\$s")).contains("%1\$s")
  }
}
