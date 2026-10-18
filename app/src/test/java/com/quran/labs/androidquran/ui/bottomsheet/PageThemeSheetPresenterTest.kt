package com.quran.labs.androidquran.ui.bottomsheet

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PageThemeSheetPresenterTest {
  private lateinit var context: Context
  private lateinit var preferences: SharedPreferences
  private lateinit var settings: QuranSettings

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    preferences = PreferenceManager.getDefaultSharedPreferences(context)
    preferences.edit().clear().commit()
    QuranSettings.setInstance(null)
    settings = QuranSettings.getInstance(context)
  }

  @After fun tearDown() {
    preferences.edit().clear().commit()
    QuranSettings.setInstance(null)
  }

  private fun states() = moleculeFlow(RecompositionMode.Immediate) {
    PageThemeSheetPresenter(context, settings).present()
  }

  @Test fun `starts from the saved settings`() = runTest {
    settings.pageTheme = Constants.PAGE_THEME_QUIET
    settings.pageAppearance = Constants.PAGE_APPEARANCE_DARK
    states().test {
      val state = awaitItem()
      assertThat(state.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_DARK)
      assertThat(state.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Quiet)
      assertThat(state.showBrightness).isFalse()
      assertThat(state.showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness sliders show only for Original on a dark page`() = runTest {
    settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
    states().test {
      val light = awaitItem()
      assertThat(light.showBrightness).isFalse()
      assertThat(light.cards.original().background).isEqualTo(PageThemeCardBackground.Shaded)

      light.eventSink(PageThemeSheetEvent.SelectAppearance(Constants.PAGE_APPEARANCE_DARK))
      val dark = awaitItem()
      assertThat(dark.showBrightness).isTrue()
      assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_DARK)

      dark.eventSink(PageThemeSheetEvent.SelectTheme(Constants.PAGE_THEME_PAPER))
      val paper = awaitItem()
      assertThat(paper.showBrightness).isFalse()
      assertThat(paper.cards.single { it.isSelected }.theme).isEqualTo(PageTheme.Paper)
      assertThat(settings.pageTheme).isEqualTo(Constants.PAGE_THEME_PAPER)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test fun `brightness changes are saved and shown on the Original card`() = runTest {
    settings.pageAppearance = Constants.PAGE_APPEARANCE_DARK
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
    settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
    states().test {
      assertThat(awaitItem().showOldBackground).isFalse()
      cancelAndIgnoreRemainingEvents()
    }

    preferences.edit().putBoolean(Constants.PREF_USE_OLD_BACKGROUND, false).commit()
    states().test {
      val state = awaitItem()
      assertThat(state.showOldBackground).isTrue()
      state.eventSink(PageThemeSheetEvent.UseOldBackgroundChanged(true))
      val updated = awaitItem()
      assertThat(settings.useOldBackground()).isTrue()
      assertThat(updated.cards.original().background).isEqualTo(
        PageThemeCardBackground.Solid(ContextCompat.getColor(context, R.color.page_background))
      )
      cancelAndIgnoreRemainingEvents()
    }
  }

  private fun List<PageThemeCard>.original() = first { it.theme == PageTheme.Original }
}
