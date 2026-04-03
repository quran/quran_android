package com.quran.labs.androidquran.fakes

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider

class FakeAudioExtensionDecider : AudioExtensionDecider {
  val extensionForQariMap: MutableMap<QariItem, String> = mutableMapOf()
  val allowedExtensionsMap: MutableMap<QariItem, List<String>> = mutableMapOf()

  override fun audioExtensionForQari(qari: Qari): String =
    error("Use QariItem overload")

  override fun audioExtensionForQari(qariItem: QariItem): String =
    extensionForQariMap[qariItem]
      ?: error("Not stubbed: audioExtensionForQari($qariItem)")

  override fun allowedAudioExtensions(qari: Qari): List<String> =
    error("Use QariItem overload")

  override fun allowedAudioExtensions(qariItem: QariItem): List<String> =
    allowedExtensionsMap[qariItem]
      ?: error("Not stubbed: allowedAudioExtensions($qariItem)")
}
