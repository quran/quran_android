package com.quran.labs.androidquran.util

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate
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
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
  }

  @Test fun `all five reader palettes follow saved light and dark appearance`() {
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
      settings.setAppTheme(Constants.THEME_LIGHT)
      setSystemNight(true)
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(palette.lightBackground.toInt())
      assertThat(settings.pageThemeTextColor).isEqualTo(palette.lightText.toInt())
      assertThat(settings.isNightMode).isEqualTo(palette.key == "quiet")
      settings.setAppTheme(Constants.THEME_DARK)
      setSystemNight(false)
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(palette.darkBackground.toInt())
      assertThat(settings.pageThemeTextColor).isEqualTo(palette.darkText.toInt())
      assertThat(settings.isNightMode).isTrue()
    }
  }

  @Test fun `Quiet follows live system palette changes while keeping dark app chrome`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageTheme = Constants.PAGE_THEME_QUIET
    settings.setAppTheme(Constants.THEME_DEFAULT)
    setSystemNight(false)
    assertThat(settings.pageThemeBackgroundColor).isEqualTo(0xff4a4a4d.toInt())
    assertThat(settings.isNightMode).isTrue()
    assertThat(ThemeUtil.appNightMode(settings.currentTheme(), settings.pageTheme))
      .isEqualTo(AppCompatDelegate.MODE_NIGHT_YES)
    setSystemNight(true)
    assertThat(settings.pageThemeBackgroundColor).isEqualTo(0xff000000.toInt())
    assertThat(settings.pageThemeTextColor).isEqualTo(0xff8d8d92.toInt())
    assertThat(settings.currentTheme()).isEqualTo(Constants.THEME_DEFAULT)
    settings.pageTheme = Constants.PAGE_THEME_PAPER
    assertThat(ThemeUtil.appNightMode(settings.currentTheme(), settings.pageTheme))
      .isEqualTo(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
  }

  @Test fun `system configuration changes notify readers without writing preferences`() {
    val settings = QuranSettings.getInstance(context)
    val changes = mutableListOf<String?>()
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> changes.add(key) }
    val before = preferences.all
    settings.registerPreferencesListener(listener)
    (context as Application).onConfigurationChanged(Configuration(context.resources.configuration))
    assertThat(changes).containsExactly(Constants.PREF_APP_THEME)
    assertThat(preferences.all).isEqualTo(before)
    settings.unregisterPreferencesListener(listener)
    changes.clear()
    (context as Application).onConfigurationChanged(Configuration(context.resources.configuration))
    assertThat(changes).isEmpty()
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
