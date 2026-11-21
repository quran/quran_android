package com.quran.labs.androidquran.widget

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AudioWidgetPreferencesTest {

  private lateinit var preferences: AudioWidgetPreferences

  private val mulk = AudioWidgetConfig(
    title = "Before sleep",
    qariId = 7,
    start = SuraAyah(67, 1),
    end = SuraAyah(67, 30),
    verseRepeat = 0,
    rangeRepeat = -1,
    playbackSpeed = 1.0f,
    enforceBounds = true
  )

  private val kahf = AudioWidgetConfig(
    title = null,
    qariId = 3,
    start = SuraAyah(18, 1),
    end = SuraAyah(18, 110),
    verseRepeat = 2,
    rangeRepeat = 0,
    playbackSpeed = 1.25f,
    enforceBounds = false
  )

  @Before
  fun setup() {
    preferences = AudioWidgetPreferences(getApplicationContext())
  }

  @Test
  fun unconfiguredWidgetHasNoConfig() {
    assertThat(preferences.config(42)).isNull()
  }

  @Test
  fun savedConfigIsReturned() {
    preferences.save(1, mulk)
    assertThat(preferences.config(1)).isEqualTo(mulk)
  }

  @Test
  fun widgetsKeepIsolatedSettings() {
    preferences.save(1, mulk)
    preferences.save(2, kahf)

    assertThat(preferences.config(1)).isEqualTo(mulk)
    assertThat(preferences.config(2)).isEqualTo(kahf)
  }

  @Test
  fun reconfiguringReplacesSettings() {
    preferences.save(1, mulk)
    preferences.save(1, kahf)
    assertThat(preferences.config(1)).isEqualTo(kahf)
  }

  @Test
  fun deletingWidgetOnlyRemovesItsOwnSettings() {
    // widget ids 1 and 11 share a prefix, which must not be confused
    preferences.save(1, mulk)
    preferences.save(11, kahf)

    preferences.delete(1)

    assertThat(preferences.config(1)).isNull()
    assertThat(preferences.config(11)).isEqualTo(kahf)
  }
}
