package com.quran.labs.androidquran.data

import com.quran.data.di.AppScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

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
   * For today, a Qari can either have mp3s or opus files, but not both.
   * In the future, this constraint may be relaxed, and this method allows this possibility.
   */
  override fun allowedAudioExtensions(qari: Qari): List<String> {
    return listOf(audioExtensionForQari(qari))
  }

  /**
   * For today, a Qari can either have mp3s or opus files, but not both.
   * In the future, this constraint may be relaxed, and this method allows this possibility.
   */
  override fun allowedAudioExtensions(qariItem: QariItem): List<String> {
    return listOf(audioExtensionForQari(qariItem))
  }

}
