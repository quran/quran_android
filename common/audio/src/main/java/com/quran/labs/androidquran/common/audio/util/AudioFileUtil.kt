package com.quran.labs.androidquran.common.audio.util

import com.quran.data.core.QuranFileManager
import com.quran.labs.androidquran.common.audio.model.AudioPathInfo
import com.quran.labs.androidquran.common.audio.model.QariItem
import java.io.File
import javax.inject.Inject

class AudioFileUtil @Inject constructor(private val quranFileManager: QuranFileManager) {

  fun getLocalQariUrl(item: QariItem): String? {
    val rootDirectory = quranFileManager.audioFileDirectory()
    return if (rootDirectory == null) null else rootDirectory + item.path
  }

  fun getQariDatabasePathIfGapless(item: QariItem): String? {
    var databaseName = item.databaseName
    if (databaseName != null) {
      val path = getLocalQariUrl(item)
      if (path != null) {
        databaseName = path + File.separator + databaseName + DB_EXTENSION
      }
    }
    return databaseName
  }

  fun getLocalAudioPathInfo(qari: QariItem): AudioPathInfo? {
    val localPath = getLocalQariUrl(qari)
    if (localPath != null) {
      val databasePath = getQariDatabasePathIfGapless(qari)
      val urlFormat = if (databasePath.isNullOrEmpty()) {
        localPath + File.separator + "%d" + File.separator +
            "%d" + AUDIO_EXTENSION
      } else {
        localPath + File.separator + "%03d" + AUDIO_EXTENSION
      }
      return AudioPathInfo(urlFormat, localPath, databasePath)
    }
    return null
  }

  companion object {
    const val AUDIO_EXTENSION = ".mp3"

    private const val DB_EXTENSION = ".db"
  }
}
