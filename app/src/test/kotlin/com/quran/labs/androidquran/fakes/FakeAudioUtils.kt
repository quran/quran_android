package com.quran.labs.androidquran.fakes

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.util.AudioUtilsInterface

class FakeAudioUtils : AudioUtilsInterface {
  val localQariUrls: MutableMap<QariItem, String?> = mutableMapOf()
  val qariUrls: MutableMap<Pair<QariItem, String>, String> = mutableMapOf()
  var haveAllFilesResult: Boolean = false
  var shouldDownloadBasmallahResult: Boolean = false
  val qariDatabasePaths: MutableMap<QariItem, String?> = mutableMapOf()

  override fun getLocalQariUrl(item: QariItem): String? = localQariUrls[item]

  override fun getQariUrl(item: QariItem, extension: String): String {
    return qariUrls[item to extension]
      ?: error("Not stubbed: getQariUrl($item, $extension)")
  }

  override fun haveAllFiles(
    baseUrl: String,
    path: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean,
    allowedExtensions: List<String>
  ): Boolean = haveAllFilesResult

  override fun shouldDownloadBasmallah(
    baseDirectory: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean,
    allowedExtensions: List<String>
  ): Boolean = shouldDownloadBasmallahResult

  override fun getQariDatabasePathIfGapless(item: QariItem): String? = qariDatabasePaths[item]
}
