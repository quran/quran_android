package com.quran.mobile.feature.pagetheme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PageThemeSummaryTest {

  @Test fun `summary names the page color and whether it matches the app`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = FakePageThemeSettings()
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Light")

    settings.pageTheme = PageThemeConstants.PAGE_THEME_SEPIA
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Sepia")

    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Dark")

    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Sepia · Match app theme")
  }
}
