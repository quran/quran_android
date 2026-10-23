package com.quran.labs.androidquran.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.common.upgrade.PreferencesUpgrade
import com.quran.data.model.PageAppearance
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.Constants
import com.quran.mobile.feature.pagetheme.PageThemeConstants
import kotlinx.coroutines.test.runTest
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

  @Test fun `light pages use their own background and dark pages use the night sliders`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_LIGHT
    settings.pageTheme = PageThemeConstants.PAGE_THEME_LIGHT
    assertThat(settings.isNightMode).isFalse()
    assertThat(settings.isLightPage).isTrue()
    assertThat(settings.pageThemeBackgroundColor).isEqualTo(0xffffffff.toInt())
    settings.pageTheme = PageThemeConstants.PAGE_THEME_SEPIA
    assertThat(settings.isLightPage).isFalse()
    assertThat(settings.pageThemeBackgroundColor).isEqualTo(0xfff8ebd5.toInt())

    preferences.edit()
      .putInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS, 150)
      .putInt(Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS, 4)
      .commit()
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    for (theme in listOf(PageThemeConstants.PAGE_THEME_LIGHT, PageThemeConstants.PAGE_THEME_SEPIA)) {
      settings.pageTheme = theme
      assertThat(settings.isNightMode).isTrue()
      assertThat(settings.isLightPage).isFalse()
      assertThat(settings.pageTextBrightness).isEqualTo(230)
      assertThat(settings.pageThemeBackgroundColor).isEqualTo(Color.rgb(4, 4, 4))
    }
  }

  @Test fun `night text brightness never goes below the readable minimum`() {
    preferences.edit()
      .putInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS, 40)
      .putInt(Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS, 0)
      .commit()
    val settings = QuranSettings.getInstance(context)
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    assertThat(settings.nightModeTextBrightness).isEqualTo(PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS)
    assertThat(settings.pageTextBrightness).isEqualTo(PageThemeConstants.MIN_NIGHT_MODE_TEXT_BRIGHTNESS)
  }

  @Test fun `line by line pages are shaded only for Light by day`() = runTest {
    val settings = QuranSettings.getInstance(context)
    val lineByLineSettings = SettingsImpl(settings)
    assertThat(lineByLineSettings.dayPageBackgroundColor()).isNull()
    settings.pageTheme = PageThemeConstants.PAGE_THEME_SEPIA
    assertThat(lineByLineSettings.dayPageBackgroundColor()).isEqualTo(0xfff8ebd5.toInt())
    preferences.edit().putInt(Constants.PREF_NIGHT_MODE_BACKGROUND_BRIGHTNESS, 10).commit()
    assertThat(lineByLineSettings.nightModeBackgroundBrightness()).isEqualTo(10)
  }

  @Test fun `line by line pages resolve automatic from the app theme`() = runTest {
    val lineByLineSettings = SettingsImpl(QuranSettings.getInstance(context))
    val appearances = mapOf(
      PageThemeConstants.PAGE_APPEARANCE_LIGHT to PageAppearance.Light,
      PageThemeConstants.PAGE_APPEARANCE_DARK to PageAppearance.Dark,
      PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC to PageAppearance.Automatic
    )
    appearances.forEach { (key, appearance) ->
      QuranSettings.getInstance(context).pageAppearance = key
      assertThat(lineByLineSettings.pageAppearance()).isEqualTo(appearance)
    }
    assertThat(PageAppearance.Light.isNightMode(isAppDark = true)).isFalse()
    assertThat(PageAppearance.Dark.isNightMode(isAppDark = false)).isTrue()
    assertThat(PageAppearance.Automatic.isNightMode(isAppDark = false)).isFalse()
    assertThat(PageAppearance.Automatic.isNightMode(isAppDark = true)).isTrue()
  }

  @Test fun `only legacy background users draw the old background`() {
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.useOldBackground()).isFalse()
    preferences.edit().putBoolean(Constants.PREF_USE_OLD_BACKGROUND, true).commit()
    assertThat(settings.useOldBackground()).isTrue()
  }

  @Test fun `light and dark page appearances ignore the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_LIGHT
    setAppTheme(Constants.THEME_DARK)
    assertThat(settings.isNightMode).isFalse()
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_DARK
    setAppTheme(Constants.THEME_LIGHT)
    assertThat(settings.isNightMode).isTrue()
  }

  @Test fun `automatic page appearance follows the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.pageAppearance = PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC
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

  @Test fun `fresh installs get a Light page that follows the app theme`() {
    val settings = QuranSettings.getInstance(context)
    settings.currentTheme()
    settings.upgradePreferences(noFlavorUpgrade)
    assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
    assertThat(settings.pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_LIGHT)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()
  }

  @Test fun `upgradePreferences migrates existing installs`() {
    perInstallationPreferences.edit().putInt(Constants.PREF_VERSION, 1).commit()
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    val settings = QuranSettings.getInstance(context)
    settings.upgradePreferences(noFlavorUpgrade)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
    assertThat(settings.isNightMode).isTrue()
  }

  @Test fun `reads before the upgrade runs fall back to the legacy night mode`() {
    val settings = QuranSettings.getInstance(context)
    assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    assertThat(settings.pageAppearance).isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
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
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)
    assertThat(preferences.contains(Constants.PREF_PAGE_THEME)).isFalse()
    assertThat(preferences.getString(Constants.PREF_APP_THEME, null)).isEqualTo(Constants.THEME_LIGHT)
    assertThat(preferences.getInt("nightModeTextBrightness", 0)).isEqualTo(128)
  }

  @Test fun `upgrading day users keep a light page`() {
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
  }

  @Test fun `restored backups without a version still count as upgrades`() {
    preferences.edit().putBoolean(Constants.PREF_NIGHT_MODE, true).commit()
    PageThemePreferences.migrate(preferences, false)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_DARK)

    preferences.edit().clear().putInt(Constants.PREF_LAST_PAGE, 42).commit()
    PageThemePreferences.migrate(preferences, false)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_LIGHT)
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
      .putString(Constants.PREF_PAGE_APPEARANCE, PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
      .putString(Constants.PREF_PAGE_THEME, PageThemeConstants.PAGE_THEME_SEPIA)
      .putBoolean(Constants.PREF_NIGHT_MODE, true)
      .putBoolean(Constants.PREF_USE_NEW_BACKGROUND, false)
      .commit()
    PageThemePreferences.migrate(preferences, true)
    assertThat(preferences.getString(Constants.PREF_PAGE_APPEARANCE, null))
      .isEqualTo(PageThemeConstants.PAGE_APPEARANCE_AUTOMATIC)
    assertThat(preferences.getString(Constants.PREF_PAGE_THEME, null)).isEqualTo(PageThemeConstants.PAGE_THEME_SEPIA)
    assertThat(preferences.contains(Constants.PREF_USE_OLD_BACKGROUND)).isFalse()
  }

  @Test fun `unknown theme keys fall back to Light`() {
    preferences.edit().putString(Constants.PREF_PAGE_THEME, "unsupported").commit()
    assertThat(QuranSettings.getInstance(context).pageTheme).isEqualTo(PageThemeConstants.PAGE_THEME_LIGHT)
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
}
