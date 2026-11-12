package com.quran.mobile.feature.pagetheme.presenter

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.pagetheme.FakePageThemeSettings
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import com.quran.mobile.feature.pagetheme.R
import com.quran.mobile.feature.pagetheme.state.PageColor
import com.quran.mobile.feature.pagetheme.state.PageThemeCard
import com.quran.mobile.feature.pagetheme.state.PageThemeCardBackground
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetEvent
import com.quran.mobile.feature.pagetheme.state.PageThemeSheetState
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
      assertThat(state.selected()).isEqualTo(PageColor.Dark)
      assertThat(state.isNightPage).isTrue()
      assertThat(state.matchAppTheme).isFalse()
      assertThat(state.showBrightness).isTrue()
      assertThat(state.showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `picking a day color leaves the dark page`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.SelectColor(PageColor.Sepia))
      val sepia = expectMostRecentItem()
      assertThat(sepia.selected()).isEqualTo(PageColor.Sepia)
      assertThat(sepia.showBrightness).isFalse()
      assertThat(settings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_SEPIA)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)

      sepia.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Dark))
      val dark = awaitItem()
      assertThat(dark.selected()).isEqualTo(PageColor.Dark)
      assertThat(settings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_SEPIA)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `matching a light app keeps matching while picking day colors`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    states().test {
      val light = awaitItem()
      assertThat(light.matchAppTheme).isTrue()
      assertThat(light.selected()).isEqualTo(PageColor.Light)

      light.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Sepia))
      val sepia = awaitItem()
      assertThat(sepia.matchAppTheme).isTrue()
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)

      sepia.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Dark))
      val dark = awaitItem()
      assertThat(dark.matchAppTheme).isFalse()
      assertThat(dark.selected()).isEqualTo(PageColor.Dark)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `matching a dark app stops when picking a day color`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    settings.isAppDark = true
    states().test {
      val dark = awaitItem()
      assertThat(dark.matchAppTheme).isTrue()
      assertThat(dark.selected()).isEqualTo(PageColor.Dark)

      dark.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Dark))
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)

      dark.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Light))
      val light = awaitItem()
      assertThat(light.matchAppTheme).isFalse()
      assertThat(light.selected()).isEqualTo(PageColor.Light)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `turning matching off keeps the page as it is`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
    settings.isAppDark = true
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.MatchAppThemeChanged(false))
      val dark = awaitItem()
      assertThat(dark.matchAppTheme).isFalse()
      assertThat(dark.selected()).isEqualTo(PageColor.Dark)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)

      dark.eventSink(PageThemeSheetEvent.MatchAppThemeChanged(true))
      assertThat(awaitItem().matchAppTheme).isTrue()
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
      cancelAndIgnoreRemainingEvents()
    }

    settings.isAppDark = false
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.MatchAppThemeChanged(false))
      assertThat(awaitItem().selected()).isEqualTo(PageColor.Light)
      assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `cards show each page color`() = runTest {
    states().test {
      val cards = awaitItem().cards
      assertThat(cards.map { it.color }).containsExactly(PageColor.Light, PageColor.Sepia, PageColor.Dark).inOrder()
      assertThat(cards.card(PageColor.Light).background).isEqualTo(PageThemeCardBackground.Shaded)
      assertThat(cards.card(PageColor.Sepia).background).isEqualTo(
        PageThemeCardBackground.Solid(context.getColor(R.color.page_theme_sepia_bg))
      )
      assertThat(cards.card(PageColor.Dark).background).isEqualTo(PageThemeCardBackground.Solid(Color.BLACK))
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness changes are saved and shown on the Dark card`() = runTest {
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    states().test {
      awaitItem().eventSink(PageThemeSheetEvent.BackgroundBrightnessChanged(4))
      awaitItem().eventSink(PageThemeSheetEvent.TextBrightnessChanged(150))
      val state = awaitItem()
      assertThat(settings.nightModeBackgroundBrightness).isEqualTo(4)
      assertThat(settings.nightModeTextBrightness).isEqualTo(150)
      assertThat(state.backgroundBrightness).isEqualTo(4)
      assertThat(state.textBrightness).isEqualTo(150)
      val dark = state.cards.card(PageColor.Dark)
      assertThat(dark.background).isEqualTo(PageThemeCardBackground.Solid(Color.rgb(4, 4, 4)))
      assertThat(dark.textColor).isEqualTo(Color.rgb(230, 230, 230))
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
      assertThat(updated.cards.card(PageColor.Light).background).isEqualTo(
        PageThemeCardBackground.Solid(context.getColor(R.color.page_background))
      )

      updated.eventSink(PageThemeSheetEvent.SelectColor(PageColor.Sepia))
      assertThat(awaitItem().showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun PageThemeSheetState.selected() = cards.single { it.isSelected }.color

  private fun List<PageThemeCard>.card(color: PageColor) = first { it.color == color }
}
