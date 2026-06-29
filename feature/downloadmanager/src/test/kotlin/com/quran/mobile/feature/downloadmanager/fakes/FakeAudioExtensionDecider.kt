package com.quran.mobile.feature.downloadmanager.fakes

import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider

class FakeAudioExtensionDecider : AudioExtensionDecider {
  val extensionForQariMap = mutableMapOf<Int, String>()
  val allowedExtensionsForQariMap = mutableMapOf<Int, List<String>>()

  override fun audioExtensionForQari(qari: Qari): String =
    extensionForQariMap[qari.id] ?: error("No extension configured for qari ${qari.id}")

  override fun audioExtensionForQari(qariItem: QariItem): String =
    error("Use Qari overload")

  override fun allowedAudioExtensions(qari: Qari): List<String> =
    allowedExtensionsForQariMap[qari.id] ?: error("No allowed extensions configured for qari ${qari.id}")

  override fun allowedAudioExtensions(qariItem: QariItem): List<String> =
    error("Use Qari overload")
}
