package com.quran.labs.androidquran.util

import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.audio.model.QariItem

interface AudioUtilsInterface {
  fun getLocalQariUrl(item: QariItem): String?
  fun getQariUrl(item: QariItem, extension: String): String
  fun haveAllFiles(
    baseUrl: String,
    path: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean,
    allowedExtensions: List<String>
  ): Boolean
  fun shouldDownloadBasmallah(
    baseDirectory: String,
    start: SuraAyah,
    end: SuraAyah,
    isGapless: Boolean,
    allowedExtensions: List<String>
  ): Boolean
  fun getQariDatabasePathIfGapless(item: QariItem): String?
}
