package com.quran.labs.androidquran.common.audio.util

import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

@ContributesBinding(AppScope::class)
class QuranAudioExtensionDecider @Inject constructor() : AudioExtensionDecider {

  /**
   * Returns the audio file extension for a given Qari.
   * For today, if the Qari has an opus URL, it returns "opus", otherwise it returns "mp3". In
   * the future, this might depend on more issues, such as a global preference, or the presence
   * of a specific audio file type for the qari already downloaded.
   */
  override fun audioExtensionForQari(qari: Qari): String {
    return if (qari.opusUrl != null) "opus" else "mp3"
  }

  /**
   * Returns the audio file extension for a given Qari.
   * For today, if the Qari has an opus URL, it returns "opus", otherwise it returns "mp3". In
   * the future, this might depend on more issues, such as a global preference, or the presence
   * of a specific audio file type for the qari already downloaded.
   */
  override fun audioExtensionForQari(qariItem: QariItem): String {
    return if (qariItem.opusUrl != null) "opus" else "mp3"
  }

  /**
   * For qaris with opus support, we allow both opus and mp3 as valid local file extensions.
   * This allows playback/download checks to use legacy mp3 files while still preferring opus
   * for new downloads/streaming.
   */
  override fun allowedAudioExtensions(qari: Qari): List<String> {
    return if (qari.opusUrl != null) listOf("opus", "mp3") else listOf("mp3")
  }

  /**
   * For qaris with opus support, we allow both opus and mp3 as valid local file extensions.
   * This allows playback/download checks to use legacy mp3 files while still preferring opus
   * for new downloads/streaming.
   */
  override fun allowedAudioExtensions(qariItem: QariItem): List<String> {
    return if (qariItem.opusUrl != null) listOf("opus", "mp3") else listOf("mp3")
  }

}
