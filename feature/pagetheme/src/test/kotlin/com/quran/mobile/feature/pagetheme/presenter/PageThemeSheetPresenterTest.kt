package com.quran.mobile.feature.pagetheme.presenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.pagetheme.PageTheme
import com.quran.mobile.feature.pagetheme.FakePageThemeSettings
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.R
import com.quran.mobile.feature.pagetheme.state.PageThemeCard
import com.quran.mobile.feature.pagetheme.state.PageThemeCardBackground
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetEvent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PageThemeSheetPresenterTest {
  private lateinit var context: Context
  private lateinit var settings: FakePageThemeSettings

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    settings = FakePageThemeSettings()
  }

  private fun states() = moleculeFlow(RecompositionMode.Immediate) {
    PageThemeSheetPresenter(context, settings).present()
  }

  @Test fun `starts from the saved settings`() = runTest {
    settings.pageTheme = PageThemeConstants.PAGE_THEME_SEPIA
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      val state = awaitItem()
      assertThat(state.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
      assertThat(state.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Sepia)
      assertThat(state.showBrightness).isTrue()
      assertThat(state.showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness sliders show whenever the page is dark`() = runTest {
    states().test {
      val light = awaitItem()
      assertThat(light.showBrightness).isFalse()
      assertThat(light.cards.light().background).isEqualTo(PageThemeCardBackground.Shaded)

      light.eventSink(PageThemeSheetEvent.SelectAppearance(PageThemeConstants.PAGE_APPEARANCE_DARK))
      val dark = awaitItem()
      assertThat(dark.showBrightness).isTrue()
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)

      dark.eventSink(PageThemeSheetEvent.SelectTheme(PageThemeConstants.PAGE_THEME_SEPIA))
      val sepia = awaitItem()
      assertThat(sepia.showBrightness).isTrue()
      assertThat(sepia.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Sepia)
      assertThat(settings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_SEPIA)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `automatic follows the app theme`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    states().test {
      assertThat(awaitItem().showBrightness).isFalse()
      cancelAndIgnoreRemainingEvents()
    }

    settings.isAppDark = true
    states().test {
      assertThat(awaitItem().showBrightness).isTrue()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness changes are saved`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.BackgroundBrightnessChanged(4))
      awaitItem().eventSink(PageThemeSheetEvent.TextBrightnessChanged(150))
      val state = awaitItem()
      assertThat(settings.nightModeBackgroundBrightness).isEqualTo(4)
      assertThat(settings.nightModeTextBrightness).isEqualTo(150)
      assertThat(state.backgroundBrightness).isEqualTo(4)
      assertThat(state.textBrightness).isEqualTo(150)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `sepia uses its own background`() = runTest {
    states().test {
      assertThat(awaitItem().cards.first { it.theme == PageTheme.Sepia }.background).isEqualTo(
        PageThemeCardBackground.Solid(context.getColor(R.color.page_theme_sepia_bg))
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `only legacy background users see the old background option`() = runTest {
    states().test {
      assertThat(awaitItem().showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }

    settings.oldBackground = false
    states().test {
      val state = awaitItem()
      assertThat(state.showOldBackground).isTrue()
      state.eventSink(PageThemeSheetEvent.UseOldBackgroundChanged(true))
      val updated = awaitItem()
      assertThat(settings.useOldBackground()).isTrue()
      assertThat(updated.cards.light().background).isEqualTo(
        PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
      )

      updated.eventSink(PageThemeSheetEvent.SelectTheme(PageThemeConstants.PAGE_THEME_SEPIA))
      assertThat(awaitItem().showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun List<PageThemeCard>.light() = first { it.theme == PageTheme.Light }
}
