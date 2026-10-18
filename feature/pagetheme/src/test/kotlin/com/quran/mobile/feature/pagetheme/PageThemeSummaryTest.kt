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

  @Test fun `summary names the theme and the page appearance`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = FakePageThemeSettings()
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Original · Light")

    settings.pageTheme = PageThemeConstants.PAGE_THEME_QUIET
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    assertThat(pageThemeSummary(context, settings)).isEqualTo("Quiet · Automatic")
  }
}
