package com.quran.labs.androidquran.util

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AudioRevisionSettingsTest {
  private lateinit var context: Context
  private lateinit var perInstallationPreferences: SharedPreferences

  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    perInstallationPreferences = context.getSharedPreferences(
      "com.quran.labs.androidquran.per_installation", Context.MODE_PRIVATE
    )
    perInstallationPreferences.edit().clear().commit()
    QuranSettings.setInstance(null)
  }

  @After fun tearDown() {
    perInstallationPreferences.edit().clear().commit()
    QuranSettings.setInstance(null)
  }

  @Test fun `audio revision saved before the gapped fix is ignored so every update is checked again`() {
    perInstallationPreferences.edit().putInt("currentAudioRevision", 42).commit()

    assertThat(QuranSettings.getInstance(context).currentAudioRevision).isEqualTo(1)
  }

  @Test fun `audio revision saved after the gapped fix is kept`() {
    QuranSettings.getInstance(context).currentAudioRevision = 42
    QuranSettings.setInstance(null)

    assertThat(QuranSettings.getInstance(context).currentAudioRevision).isEqualTo(42)
  }
}
