package com.quran.labs.androidquran.common.audio.cache

import com.quran.data.core.QuranFileManager
import com.quran.labs.androidquran.common.audio.cache.command.AudioInfoCommand
import com.quran.labs.androidquran.common.audio.model.QariDownloadInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class QariDownloadInfoManager @Inject constructor(
  private val qariDownloadInfoCache: QariDownloadInfoCache,
  private val quranFileManager: QuranFileManager,
  private val audioInfoCommand: AudioInfoCommand
) {

  fun downloadedQariInfo(): Flow<List<QariDownloadInfo>> {
    return qariDownloadInfoCache.flow()
  }

  fun populateCache() {
    val audioDirectory = quranFileManager.audioFileDirectory() ?: return
    val qariDownloadInfo = audioInfoCommand.generateAllQariDownloadInfo(audioDirectory)
    qariDownloadInfoCache.writeAll(qariDownloadInfo)
  }
}
