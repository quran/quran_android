package com.quran.labs.androidquran.common.audio.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import org.junit.Test

class QuranAudioExtensionDeciderTest {

  private val decider = QuranAudioExtensionDecider()

  @Test
  fun testAllowedExtensionsForOpusQariIncludesMp3Fallback() {
    val qari = Qari(
      id = 1,
      nameResource = 1,
      url = "https://example.com/mp3/",
      opusUrl = "https://example.com/opus/",
      path = "test_qari",
      hasGaplessAlternative = false
    )

    assertThat(decider.audioExtensionForQari(qari)).isEqualTo("opus")
    assertThat(decider.allowedAudioExtensions(qari)).containsExactly("opus", "mp3").inOrder()
  }

  @Test
  fun testAllowedExtensionsForOpusQariItemIncludesMp3Fallback() {
    val qariItem = QariItem(
      id = 1,
      name = "Qari",
      url = "https://example.com/mp3/",
      opusUrl = "https://example.com/opus/",
      path = "test_qari",
      hasGaplessAlternative = false
    )

    assertThat(decider.audioExtensionForQari(qariItem)).isEqualTo("opus")
    assertThat(decider.allowedAudioExtensions(qariItem)).containsExactly("opus", "mp3").inOrder()
  }

  @Test
  fun testAllowedExtensionsForMp3OnlyQari() {
    val qari = Qari(
      id = 2,
      nameResource = 1,
      url = "https://example.com/mp3/",
      opusUrl = null,
      path = "test_qari",
      hasGaplessAlternative = false
    )

    assertThat(decider.audioExtensionForQari(qari)).isEqualTo("mp3")
    assertThat(decider.allowedAudioExtensions(qari)).containsExactly("mp3")
  }
}
