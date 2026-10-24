package com.quran.mobile.feature.appicon

import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.appicon.fakes.FakeAppIconPreference
import com.quran.mobile.feature.appicon.fakes.TestAppIcons
import org.junit.Test

class SavedAppIconTest {

  @Test
  fun `nothing saved means the default icon`() {
    assertThat(TestAppIcons.savedAppIcon().option.value).isEqualTo(TestAppIcons.default)
  }

  @Test
  fun `a saved id restores its icon`() {
    val savedAppIcon = TestAppIcons.savedAppIcon(FakeAppIconPreference(savedIconId = "blue"))

    assertThat(savedAppIcon.option.value).isEqualTo(TestAppIcons.blue)
  }

  @Test
  fun `an unknown saved id falls back to the default icon`() {
    val savedAppIcon = TestAppIcons.savedAppIcon(FakeAppIconPreference(savedIconId = "graphite"))

    assertThat(savedAppIcon.option.value).isEqualTo(TestAppIcons.default)
  }
}
