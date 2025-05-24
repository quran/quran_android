package com.quran.labs.androidquran.common.audio.util

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem

/**
 * Interface to determine the audio file extension for a given Qari or QariItem.
 *
 * Note: The fact that both [Qari] and [QariItem] are supported is due to the usage of
 * both. In reality, this highlights an underlying issue, which is that these are really
 * the exact same thing and should be unified in the future (the only real difference is
 * one has a resource id for a name whereas the other has the actual resource string).
 */
interface AudioExtensionDecider {
  /**
   * Returns the audio file extension for the given Qari.
   * This is the extension that should be used for downloading or streaming audio files.
   */
  fun audioExtensionForQari(qari: Qari): String

  /**
   * Returns the audio file extension for the given Qari.
   * This is the extension that should be used for downloading or streaming audio files.
   */
  fun audioExtensionForQari(qariItem: QariItem): String

  /**
   * Returns a list of allowed audio file extensions for the given Qari.
   * This can be used to filter or validate audio files for playback. It's mostly there in case
   * we decide to allow a mix of audio formats for a Qari in the future (ex sura Fatiha can be
   * mp3, sura Baqarah can be opus, and so on).
   */
  fun allowedAudioExtensions(qari: Qari): List<String>

  /**
   * Returns a list of allowed audio file extensions for the given Qari.
   * This can be used to filter or validate audio files for playback. It's mostly there in case
   * we decide to allow a mix of audio formats for a Qari in the future (ex sura Fatiha can be
   * mp3, sura Baqarah can be opus, and so on).
   */
  fun allowedAudioExtensions(qariItem: QariItem): List<String>
}
