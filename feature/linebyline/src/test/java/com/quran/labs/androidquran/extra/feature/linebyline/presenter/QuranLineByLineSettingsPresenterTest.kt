package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.PageAppearance
import com.quran.labs.androidquran.extra.feature.linebyline.fakes.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests for QuranLineByLineSettingsPresenter.
 *
 * The presenter accepts an injectable CoroutineScope, allowing tests to pass
 * runTest's backgroundScope for the presenter's long-lived StateFlow collection.
 * Tests use specific predicate conditions to wait for the right settings emission.
 * All tests configure at least one non-default value so the loaded settings
 * differ from EmptySettings, ensuring StateFlow emits a new value.
 */
class QuranLineByLineSettingsPresenterTest {

  @Test
  fun `displaySettingsFlow emits the saved page appearance`() = runTest {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setPageAppearance(PageAppearance.Dark)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for the dark page appearance emission
    val emitted = presenter.displaySettingsFlow.first { it.pageAppearance == PageAppearance.Dark }

    // Assert
    assertThat(emitted.pageAppearance).isEqualTo(PageAppearance.Dark)
  }

  @Test
  fun `displaySettingsFlow emits non-default text brightness when configured`() = runTest {
    // Arrange - use a brightness that differs from EmptySettings' default (255)
    val expectedBrightness = 128
    val fakeSettings = FakeSettings()
    fakeSettings.setTextBrightness(expectedBrightness)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for the specific brightness value
    val emitted = presenter.displaySettingsFlow.first { it.textBrightness == expectedBrightness }

    // Assert
    assertThat(emitted.textBrightness).isEqualTo(expectedBrightness)
  }

  @Test
  fun `displaySettingsFlow emits the day and night backgrounds when configured`() = runTest {
    // Arrange - a theme color differs from EmptySettings' default (the shaded background)
    val expectedColor = 0xfff8ebd5.toInt()
    val fakeSettings = FakeSettings()
    fakeSettings.setDayBackgroundColor(expectedColor)
    fakeSettings.setNightBackgroundBrightness(10)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for the specific background color
    val emitted = presenter.displaySettingsFlow.first {
      it.dayBackgroundColor == expectedColor
    }

    // Assert
    assertThat(emitted.dayBackgroundColor).isEqualTo(expectedColor)
    assertThat(emitted.nightModeBackgroundBrightness).isEqualTo(10)
  }

  @Test
  fun `displaySettingsFlow emits show header footer true when enabled`() = runTest {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setHeaderFooter(true)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for header footer enabled
    val emitted = presenter.displaySettingsFlow.first { it.showHeaderFooter }

    // Assert
    assertThat(emitted.showHeaderFooter).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits show sidelines true when enabled`() = runTest {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setSidelinesEnabled(true)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for sidelines enabled
    val emitted = presenter.displaySettingsFlow.first { it.showSidelines }

    // Assert
    assertThat(emitted.showSidelines).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits show line dividers true when enabled`() = runTest {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setLineDividersEnabled(true)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for line dividers enabled
    val emitted = presenter.displaySettingsFlow.first { it.showLineDividers }

    // Assert
    assertThat(emitted.showLineDividers).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits combined settings when multiple are configured`() = runTest {
    // Arrange - configure a combination of settings
    val fakeSettings = FakeSettings()
    fakeSettings.setPageAppearance(PageAppearance.Dark)
    fakeSettings.setTextBrightness(200)
    fakeSettings.setHeaderFooter(true)
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for an emission with a dark page and header footer enabled
    val emitted = presenter.displaySettingsFlow.first { it.pageAppearance == PageAppearance.Dark && it.showHeaderFooter }

    // Assert
    assertThat(emitted.pageAppearance).isEqualTo(PageAppearance.Dark)
    assertThat(emitted.textBrightness).isEqualTo(200)
    assertThat(emitted.showHeaderFooter).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits updated settings after preferences change`() = runTest {
    // Arrange - start with a light page
    val fakeSettings = FakeSettings()
    fakeSettings.setPageAppearance(PageAppearance.Light)
    fakeSettings.setTextBrightness(100) // non-default so initial load differs from EmptySettings
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Wait for initial load (brightness=100 is non-default)
    val initial = presenter.displaySettingsFlow.first { it.textBrightness == 100 }
    assertThat(initial.pageAppearance).isEqualTo(PageAppearance.Light)

    // Act - update the page appearance and emit change
    fakeSettings.setPageAppearance(PageAppearance.Dark)
    fakeSettings.emitPreferencesChange("pageAppearance")

    // Assert - wait for the updated emission with a dark page
    val updated = presenter.displaySettingsFlow.first { it.pageAppearance == PageAppearance.Dark }
    assertThat(updated.pageAppearance).isEqualTo(PageAppearance.Dark)
  }

  @Test
  fun `latestDisplaySettings returns EmptySettings before any preference loads`() = runTest {
    val fakeSettings = FakeSettings()
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)
    val settings = presenter.latestDisplaySettings()
    assertThat(settings.pageAppearance).isEqualTo(PageAppearance.Light)
    assertThat(settings.textBrightness).isEqualTo(255)
    assertThat(settings.showHeaderFooter).isFalse()
  }

  @Test
  fun `displaySettingsFlow emits settings with a light page and custom brightness`() = runTest {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setPageAppearance(PageAppearance.Light)
    fakeSettings.setTextBrightness(150) // non-default to trigger emission
    val presenter = QuranLineByLineSettingsPresenter.forTest(fakeSettings, backgroundScope)

    // Act - wait for emission with custom brightness
    val emitted = presenter.displaySettingsFlow.first { it.textBrightness == 150 }

    // Assert
    assertThat(emitted.pageAppearance).isEqualTo(PageAppearance.Light)
    assertThat(emitted.textBrightness).isEqualTo(150)
  }
}
