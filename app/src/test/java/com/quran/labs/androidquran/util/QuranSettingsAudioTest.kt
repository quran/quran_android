package com.quran.labs.androidquran.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranSettingsAudioTest {

  @Test
  fun testShouldEnforceAudioBoundsDefaultAndToggle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = QuranSettings.getInstance(context)

    // Verify default value is false
    assertThat(settings.shouldEnforceAudioBounds()).isFalse()

    // Verify setting to true persists
    settings.setShouldEnforceAudioBounds(true)
    assertThat(settings.shouldEnforceAudioBounds()).isTrue()

    // Verify setting back to false persists
    settings.setShouldEnforceAudioBounds(false)
    assertThat(settings.shouldEnforceAudioBounds()).isFalse()
  }
}
