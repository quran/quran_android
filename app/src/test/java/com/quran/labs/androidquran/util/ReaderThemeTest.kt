package com.quran.labs.androidquran.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.Constants
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class ReaderThemeTest {
  private lateinit var context: Context
  private lateinit var preferences: SharedPreferences
  private lateinit var originalSystemConfiguration: Configuration

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    preferences = PreferenceManager.getDefaultSharedPreferences(context)
    preferences.edit().clear().commit()
    QuranSettings.setInstance(null)
    originalSystemConfiguration = Configuration(Resources.getSystem().configuration)
  }

  @After fun tearDown() {
    preferences.edit().clear().commit()
    QuranSettings.setInstance(null)
    Resources.getSystem().updateConfiguration(originalSystemConfiguration, Resources.getSystem().displayMetrics)
  }

  @Test fun `all five reader palettes follow the page appearance`() {
    val settings = QuranSettings.getInstance(context)
    val palettes = listOf(
      Palette("paper", 0xffeeeded, 0xff1d1a1a, 0xff1c1c1e, 0xfff2f2f0),
      Palette("original", 0xffffffff, 0xff000000, 0xff000000, 0xffffffff),
      Palette("quiet", 0xff4a4a4d, 0xffebebf4, 0xff000000, 0xff8d8d92),
      Palette("calm", 0xffeee2cc, 0xff32281e, 0xff413b31, 0xfff7ecdd),
      Palette("focus", 0xfffefcf5, 0xff141202, 0xff18160d, 0xfffef9ec)
    )
    for (palette in palettes) {
      settings.pageTheme = palette.key
      settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(palette.lightBackground.toInt())
      assertThat(settings.pageThemeTextColor).isEqualTo(palette.lightText.toInt())
      assertThat(settings.isNightMode).isFalse()
      settings.pageAppearance = Constants.PAGE_APPEARANCE_DARK
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(palette.darkBackground.toInt())
      assertThat(settings.pageThemeTextColor).isEqualTo(palette.darkText.toInt())
      assertThat(settings.isNightMode).isTrue()
    }
  }

  @Test fun `light and dark page appearances ignore the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageTheme = Constants.PAGE_THEME_QUIET
    settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
    setAppTheme(Constants.THEME_DARK)
    assertThat(settings.isNightMode).isFalse()
    settings.pageAppearance = Constants.PAGE_APPEARANCE_DARK
    setAppTheme(Constants.THEME_LIGHT)
    assertThat(settings.isNightMode).isTrue()
  }

  @Test fun `automatic page appearance follows the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageAppearance = Constants.PAGE_APPEARANCE_AUTOMATIC
    setAppTheme(Constants.THEME_LIGHT)
    setSystemNight(true)
    assertThat(settings.isNightMode).isFalse()
    setAppTheme(Constants.THEME_DARK)
    setSystemNight(false)
    assertThat(settings.isNightMode).isTrue()
    setAppTheme(Constants.THEME_DEFAULT)
    setSystemNight(true)
    assertThat(settings.isNightMode).isTrue()
    setSystemNight(false)
    assertThat(settings.isNightMode).isFalse()
  }

  @Test fun `page appearance falls back to the legacy night mode setting`() {
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_LIGHT)
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_DARK)
    settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
    assertThat(settings.isNightMode).isFalse()
  }

  @Test fun `migration preserves explicit appearance and custom brightness values`() {
    preferences.edit().putBoolean("nightMode", true)
      .putString(Constants.PREF_APP_THEME, Constants.THEME_LIGHT)
      .putInt("nightModeTextBrightness", 128).commit()
    PageThemePreferences.migrate(preferences)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(Constants.PAGE_THEME_QUIET)
    assertThat(preferences.getString(Constants.PREF_APP_THEME, null)).isEqualTo(Constants.THEME_LIGHT)
    assertThat(preferences.getInt("nightModeTextBrightness", 0)).isEqualTo(128)
  }

  @Test fun `legacy gradient becomes Paper and white background becomes Original`() {
    preferences.edit().putBoolean("useNewBackground", true).commit()
    PageThemePreferences.migrate(preferences)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(Constants.PAGE_THEME_PAPER)
    preferences.edit().remove(Constants.PREF_PAGE_THEME).putBoolean("useNewBackground", false).commit()
    PageThemePreferences.migrate(preferences)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(Constants.PAGE_THEME_ORIGINAL)
  }

  @Test fun `migration never replaces a theme already chosen in the picker`() {
    preferences.edit().putBoolean("nightMode", true)
      .putString(Constants.PREF_PAGE_THEME, Constants.PAGE_THEME_FOCUS).commit()
    PageThemePreferences.migrate(preferences)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(Constants.PAGE_THEME_FOCUS)
  }

  @Test fun `new installations and unknown theme keys use Paper`() {
    assertThat(QuranSettings.getInstance(context).pageTheme).isEqualTo(Constants.PAGE_THEME_PAPER)
    preferences.edit().putString(Constants.PREF_PAGE_THEME, "unsupported").commit()
    assertThat(QuranSettings.getInstance(context).pageTheme).isEqualTo(Constants.PAGE_THEME_PAPER)
  }

  private fun setAppTheme(theme: String) {
    preferences.edit().putString(Constants.PREF_APP_THEME, theme).commit()
  }

  private fun setSystemNight(night: Boolean) {
    val system = Resources.getSystem()
    val configuration = Configuration(system.configuration)
    configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    system.updateConfiguration(configuration, system.displayMetrics)
  }

  private data class Palette(
    val key: String, val lightBackground: Long, val lightText: Long,
    val darkBackground: Long, val darkText: Long
  )
}
