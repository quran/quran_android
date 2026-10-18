package com.quran.labs.androidquran.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.upgrade.PreferencesUpgrade
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
  private lateinit var perInstallationPreferences: SharedPreferences
  private lateinit var originalSystemConfiguration: Configuration

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    preferences = PreferenceManager.getDefaultSharedPreferences(context)
    perInstallationPreferences = context.getSharedPreferences(
      "com.quran.labs.androidquran.per_installation", Context.MODE_PRIVATE
    )
    preferences.edit().clear().commit()
    perInstallationPreferences.edit().clear().commit()
    QuranSettings.setInstance(null)
    originalSystemConfiguration = Configuration(Resources.getSystem().configuration)
  }

  @After fun tearDown() {
    preferences.edit().clear().commit()
    perInstallationPreferences.edit().clear().commit()
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
      assertThat(settings.isPageAppearanceDark).isFalse()
      settings.pageAppearance = Constants.PAGE_APPEARANCE_DARK
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(palette.darkBackground.toInt())
      assertThat(settings.pageThemeTextColor).isEqualTo(palette.darkText.toInt())
      assertThat(settings.isPageAppearanceDark).isTrue()
      assertThat(settings.isNightMode).isTrue()
    }
  }

  @Test fun `Quiet pages are dark in both appearances`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageTheme = Constants.PAGE_THEME_QUIET
    settings.pageAppearance = Constants.PAGE_APPEARANCE_LIGHT
    assertThat(settings.isNightMode).isTrue()
    assertThat(settings.isPageAppearanceDark).isFalse()
    assertThat(settings.pageThemeBackgroundColor).isEqualTo(0xff4a4a4d.toInt())
    settings.pageTheme = Constants.PAGE_THEME_PAPER
    assertThat(settings.isNightMode).isFalse()
  }

  @Test fun `light and dark page appearances ignore the app theme`() {
    val settings = QuranSettings.getInstance(context)
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

  @Test fun `fresh installs get an Original page that follows the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.currentTheme()
    settings.upgradePreferences(noFlavorUpgrade)
    assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_AUTOMATIC)
    assertThat(settings.pageTheme).isEqualTo(Constants.PAGE_THEME_ORIGINAL)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()
  }

  @Test fun `upgradePreferences migrates existing installs`() {
    perInstallationPreferences.edit().putInt(Constants.PREF_VERSION, 1).commit()
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    val settings = QuranSettings.getInstance(context)
    settings.upgradePreferences(noFlavorUpgrade)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_DARK)
    assertThat(settings.isNightMode).isTrue()
  }

  @Test fun `reads before the upgrade runs fall back to the legacy night mode`() {
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_LIGHT)
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    assertThat(settings.pageAppearance).isEqualTo(Constants.PAGE_APPEARANCE_DARK)
    assertThat(preferences.contains(Constants.PREF_PAGE_APPEARANCE)).isFalse()
  }

  @Test fun `upgrading night mode users keep a dark page and their settings`() {
    preferences.edit()
      .putBoolean(Constants.PREF_NIGHT_MODE, true)
      .putString(Constants.PREF_APP_THEME, Constants.THEME_LIGHT)
      .putInt("nightModeTextBrightness", 128)
      .commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_DARK)
    assertThat(preferences.contains(Constants.PREF_PAGE_THEME)).isFalse()
    assertThat(preferences.getString(Constants.PREF_APP_THEME, null)).isEqualTo(Constants.THEME_LIGHT)
    assertThat(preferences.getInt("nightModeTextBrightness", 0)).isEqualTo(128)
  }

  @Test fun `upgrading day users keep a light page`() {
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_LIGHT)
  }

  @Test fun `restored backups without a version still count as upgrades`() {
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    PageThemePreferences.migrate(preferences, false)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_DARK)

    preferences.edit().clear().putInt(Constants.PREF_LAST_PAGE, 42).commit()
    PageThemePreferences.migrate(preferences, false)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_LIGHT)
  }

  @Test fun `only legacy background users get the old background option`() {
    preferences.edit().putBoolean(Constants.PREF_USE_NEW_BACKGROUND, false).commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getBoolean(Constants.PREF_USE_OLD_BACKGROUND, false)).isTrue()

    preferences.edit().clear().putBoolean(Constants.PREF_USE_NEW_BACKGROUND, true).commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()

    preferences.edit().clear().commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()
  }

  @Test fun `migration runs once and keeps later choices`() {
    preferences.edit()
      .putString(Constants.PREF_PAGE_APPEARANCE, Constants.PAGE_APPEARANCE_AUTOMATIC)
      .putString(Constants.PREF_PAGE_THEME, Constants.PAGE_THEME_FOCUS)
      .putBoolean(Constants.PREF_NIGHT_MODE, true)
      .putBoolean(Constants.PREF_USE_NEW_BACKGROUND, false)
      .commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(Constants.PAGE_APPEARANCE_AUTOMATIC)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(Constants.PAGE_THEME_FOCUS)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()
  }

  @Test fun `unknown theme keys fall back to Original`() {
    preferences.edit().putString(Constants.PREF_PAGE_THEME, "unsupported").commit()
    assertThat(QuranSettings.getInstance(context).pageTheme).isEqualTo(Constants.PAGE_THEME_ORIGINAL)
  }

  private val noFlavorUpgrade = PreferencesUpgrade { _, _, _ -> true }

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
