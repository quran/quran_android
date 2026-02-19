package com.quran.labs.androidquran.extra.feature.linebyline.presenter

import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.extra.feature.linebyline.fakes.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for QuranLineByLineSettingsPresenter.
 *
 * The presenter uses its own CoroutineScope with Dispatchers.IO and SharingStarted.Eagerly.
 * Tests use runBlocking with specific predicate conditions to wait for the right settings
 * emission. All tests configure at least one non-default value so the loaded settings
 * differ from EmptySettings, ensuring StateFlow emits a new value.
 */
class QuranLineByLineSettingsPresenterTest {

  @Test
  fun `displaySettingsFlow emits night mode true when enabled`() = runBlocking {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setNightMode(true)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for night mode enabled emission
    val emitted = presenter.displaySettingsFlow.first { it.isNightMode }

    // Assert
    assertThat(emitted.isNightMode).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits non-default text brightness when configured`() = runBlocking {
    // Arrange - use a brightness that differs from EmptySettings' default (255)
    val expectedBrightness = 128
    val fakeSettings = FakeSettings()
    fakeSettings.setTextBrightness(expectedBrightness)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for the specific brightness value
    val emitted = presenter.displaySettingsFlow.first { it.textBrightness == expectedBrightness }

    // Assert
    assertThat(emitted.textBrightness).isEqualTo(expectedBrightness)
  }

  @Test
  fun `displaySettingsFlow emits non-default background brightness when configured`() = runBlocking {
    // Arrange - background brightness 50 differs from EmptySettings' default (0)
    val expectedBrightness = 50
    val fakeSettings = FakeSettings()
    fakeSettings.setBackgroundBrightness(expectedBrightness)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for the specific background brightness
    val emitted = presenter.displaySettingsFlow.first {
      it.nightModeBackgroundBrightness == expectedBrightness
    }

    // Assert
    assertThat(emitted.nightModeBackgroundBrightness).isEqualTo(expectedBrightness)
  }

  @Test
  fun `displaySettingsFlow emits show header footer true when enabled`() = runBlocking {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setHeaderFooter(true)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for header footer enabled
    val emitted = presenter.displaySettingsFlow.first { it.showHeaderFooter }

    // Assert
    assertThat(emitted.showHeaderFooter).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits show sidelines true when enabled`() = runBlocking {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setSidelinesEnabled(true)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for sidelines enabled
    val emitted = presenter.displaySettingsFlow.first { it.showSidelines }

    // Assert
    assertThat(emitted.showSidelines).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits show line dividers true when enabled`() = runBlocking {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setLineDividersEnabled(true)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for line dividers enabled
    val emitted = presenter.displaySettingsFlow.first { it.showLineDividers }

    // Assert
    assertThat(emitted.showLineDividers).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits combined settings when multiple are configured`() = runBlocking {
    // Arrange - configure a combination of settings
    val fakeSettings = FakeSettings()
    fakeSettings.setNightMode(true)
    fakeSettings.setTextBrightness(200)
    fakeSettings.setHeaderFooter(true)
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for an emission with night mode and header footer enabled
    val emitted = presenter.displaySettingsFlow.first { it.isNightMode && it.showHeaderFooter }

    // Assert
    assertThat(emitted.isNightMode).isTrue()
    assertThat(emitted.textBrightness).isEqualTo(200)
    assertThat(emitted.showHeaderFooter).isTrue()
  }

  @Test
  fun `displaySettingsFlow emits updated settings after preferences change`() = runBlocking {
    // Arrange - start with night mode off
    val fakeSettings = FakeSettings()
    fakeSettings.setNightMode(false)
    fakeSettings.setTextBrightness(100) // non-default so initial load differs from EmptySettings
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Wait for initial load (brightness=100 is non-default)
    val initial = presenter.displaySettingsFlow.first { it.textBrightness == 100 }
    assertThat(initial.isNightMode).isFalse()

    // Act - update night mode and emit change
    fakeSettings.setNightMode(true)
    fakeSettings.emitPreferencesChange("isNightMode")

    // Assert - wait for the updated emission with night mode true
    val updated = presenter.displaySettingsFlow.first { it.isNightMode }
    assertThat(updated.isNightMode).isTrue()
  }

  @Test
  fun `latestDisplaySettings returns a non-null DisplaySettings`() {
    // Arrange
    val fakeSettings = FakeSettings()
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act
    val settings = presenter.latestDisplaySettings()

    // Assert - non-null regardless of timing
    assertThat(settings).isNotNull()
  }

  @Test
  fun `displaySettingsFlow emits settings with correct night mode false and custom brightness`() = runBlocking {
    // Arrange
    val fakeSettings = FakeSettings()
    fakeSettings.setNightMode(false)
    fakeSettings.setTextBrightness(150) // non-default to trigger emission
    val presenter = QuranLineByLineSettingsPresenter(fakeSettings)

    // Act - wait for emission with custom brightness
    val emitted = presenter.displaySettingsFlow.first { it.textBrightness == 150 }

    // Assert
    assertThat(emitted.isNightMode).isFalse()
    assertThat(emitted.textBrightness).isEqualTo(150)
  }
}
