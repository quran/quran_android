package com.quran.labs.androidquran.common.audio.util

import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import org.junit.Test

class QuranAudioExtensionDeciderTest {

  private class FakeAudioDecoderCapabilities(private val opusSupported: Boolean) :
    AudioDecoderCapabilities {
    override fun isOpusSupported(): Boolean = opusSupported
  }

  private fun decider(opusSupported: Boolean = true) =
    QuranAudioExtensionDecider(FakeAudioDecoderCapabilities(opusSupported))

  private fun opusQari() = Qari(
    id = 1,
    nameResource = 1,
    url = "https://example.com/mp3/",
    opusUrl = "https://example.com/opus/",
    path = "test_qari",
    hasGaplessAlternative = false
  )

  private fun opusQariItem() = QariItem(
    id = 1,
    name = "Qari",
    url = "https://example.com/mp3/",
    opusUrl = "https://example.com/opus/",
    path = "test_qari",
    hasGaplessAlternative = false
  )

  @Test
  fun testAllowedExtensionsForOpusQariIncludesMp3Fallback() {
    val decider = decider(opusSupported = true)
    val qari = opusQari()

    assertThat(decider.audioExtensionForQari(qari)).isEqualTo("opus")
    assertThat(decider.allowedAudioExtensions(qari)).containsExactly("opus", "mp3").inOrder()
  }

  @Test
  fun testAllowedExtensionsForOpusQariItemIncludesMp3Fallback() {
    val decider = decider(opusSupported = true)
    val qariItem = opusQariItem()

    assertThat(decider.audioExtensionForQari(qariItem)).isEqualTo("opus")
    assertThat(decider.allowedAudioExtensions(qariItem)).containsExactly("opus", "mp3").inOrder()
  }

  @Test
  fun testOpusQariFallsBackToMp3WhenDeviceHasNoOpusDecoder() {
    val decider = decider(opusSupported = false)
    val qari = opusQari()

    assertThat(decider.audioExtensionForQari(qari)).isEqualTo("mp3")
    assertThat(decider.allowedAudioExtensions(qari)).containsExactly("mp3")
  }

  @Test
  fun testOpusQariItemFallsBackToMp3WhenDeviceHasNoOpusDecoder() {
    val decider = decider(opusSupported = false)
    val qariItem = opusQariItem()

    assertThat(decider.audioExtensionForQari(qariItem)).isEqualTo("mp3")
    assertThat(decider.allowedAudioExtensions(qariItem)).containsExactly("mp3")
  }

  @Test
  fun testAllowedExtensionsForMp3OnlyQari() {
    val decider = decider(opusSupported = true)
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
