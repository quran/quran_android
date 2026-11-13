package com.quran.labs.androidquran.common.audio.util

import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
class QuranAudioExtensionDecider @Inject constructor(
  private val audioDecoderCapabilities: AudioDecoderCapabilities
) : AudioExtensionDecider {

  /**
   * Returns the audio file extension for a given Qari.
   * Opus is preferred when the Qari provides an opus URL and the device has an opus decoder;
   * otherwise mp3 is used. Falling back to mp3 on devices without an opus decoder avoids silent
   * playback failures (see issue #4018). In the future, this might depend on more issues, such
   * as a global preference, or the presence of a specific audio file type already downloaded.
   */
  override fun audioExtensionForQari(qari: Qari): String {
    return if (canUseOpus(qari.opusUrl)) "opus" else "mp3"
  }

  /**
   * Returns the audio file extension for a given Qari.
   * Opus is preferred when the Qari provides an opus URL and the device has an opus decoder;
   * otherwise mp3 is used. Falling back to mp3 on devices without an opus decoder avoids silent
   * playback failures (see issue #4018). In the future, this might depend on more issues, such
   * as a global preference, or the presence of a specific audio file type already downloaded.
   */
  override fun audioExtensionForQari(qariItem: QariItem): String {
    return if (canUseOpus(qariItem.opusUrl)) "opus" else "mp3"
  }

  /**
   * For qaris with opus support on devices with an opus decoder, we allow both opus and mp3 as
   * valid local file extensions. This allows playback/download checks to use legacy mp3 files
   * while still preferring opus for new downloads/streaming. On devices that cannot decode opus,
   * only mp3 is allowed so that playback does not fail silently.
   */
  override fun allowedAudioExtensions(qari: Qari): List<String> {
    return if (canUseOpus(qari.opusUrl)) listOf("opus", "mp3") else listOf("mp3")
  }

  /**
   * For qaris with opus support on devices with an opus decoder, we allow both opus and mp3 as
   * valid local file extensions. This allows playback/download checks to use legacy mp3 files
   * while still preferring opus for new downloads/streaming. On devices that cannot decode opus,
   * only mp3 is allowed so that playback does not fail silently.
   */
  override fun allowedAudioExtensions(qariItem: QariItem): List<String> {
    return if (canUseOpus(qariItem.opusUrl)) listOf("opus", "mp3") else listOf("mp3")
  }

  private fun canUseOpus(opusUrl: String?): Boolean =
    opusUrl != null && audioDecoderCapabilities.isOpusSupported()

}
