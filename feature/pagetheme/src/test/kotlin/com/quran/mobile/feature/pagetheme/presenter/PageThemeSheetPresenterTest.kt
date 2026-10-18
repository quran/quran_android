package com.quran.mobile.feature.pagetheme.presenter

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.pagetheme.PageTheme
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.PageThemeSettings
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
import kotlin.math.ln1p
import kotlin.math.min

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
    settings.pageTheme = PageThemeConstants.PAGE_THEME_QUIET
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      val state = awaitItem()
      assertThat(state.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
      assertThat(state.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Quiet)
      assertThat(state.showBrightness).isFalse()
      assertThat(state.showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness sliders show only for Original on a dark page`() = runTest {
    states().test {
      val light = awaitItem()
      assertThat(light.showBrightness).isFalse()
      assertThat(light.cards.original().background).isEqualTo(PageThemeCardBackground.Shaded)

      light.eventSink(PageThemeSheetEvent.SelectAppearance(PageThemeConstants.PAGE_APPEARANCE_DARK))
      val dark = awaitItem()
      assertThat(dark.showBrightness).isTrue()
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)

      dark.eventSink(PageThemeSheetEvent.SelectTheme(PageThemeConstants.PAGE_THEME_PAPER))
      val paper = awaitItem()
      assertThat(paper.showBrightness).isFalse()
      assertThat(paper.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Paper)
      assertThat(settings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_PAPER)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness changes are saved and shown on the Original card`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.BackgroundBrightnessChanged(4))
      awaitItem().eventSink(PageThemeSheetEvent.TextBrightnessChanged(150))
      val state = awaitItem()
      assertThat(settings.nightModeBackgroundBrightness).isEqualTo(4)
      assertThat(settings.nightModeTextBrightness).isEqualTo(150)
      assertThat(state.backgroundBrightness).isEqualTo(4)
      assertThat(state.textBrightness).isEqualTo(150)
      val original = state.cards.original()
      assertThat(original.background).isEqualTo(PageThemeCardBackground.Solid(Color.rgb(4, 4, 4)))
      assertThat(original.textColor).isEqualTo(Color.rgb(230, 230, 230))
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
      assertThat(updated.cards.original().background).isEqualTo(
        PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun List<PageThemeCard>.original() = first { it.theme == PageTheme.Original }

  private class FakePageThemeSettings : PageThemeSettings {
    override var pageTheme: String = PageThemeConstants.PAGE_THEME_ORIGINAL
    override var pageAppearance: String = PageThemeConstants.PAGE_APPEARANCE_LIGHT
    override val isPageAppearanceDark: Boolean
      get() = pageAppearance == PageThemeConstants.PAGE_APPEARANCE_DARK
    override var nightModeTextBrightness: Int = 255
    override var nightModeBackgroundBrightness: Int = 0
    override val originalNightTextBrightness: Int
      get() = min(255, (50 * ln1p(nightModeBackgroundBrightness.toDouble()) + nightModeTextBrightness).toInt())

    var oldBackground: Boolean? = null

    override fun hasOldBackgroundOption(): Boolean = oldBackground != null
    override fun useOldBackground(): Boolean = oldBackground == true
    override fun setUseOldBackground(useOldBackground: Boolean) {
      oldBackground = useOldBackground
    }
  }
}
