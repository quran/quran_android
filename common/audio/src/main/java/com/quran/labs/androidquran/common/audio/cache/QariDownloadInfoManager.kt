package com.quran.labs.androidquran.common.audio.cache

import com.quran.data.core.QuranFileManager
import com.quran.labs.androidquran.common.audio.cache.command.AudioInfoCommand
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QariDownloadInfoManager @Inject constructor(
  private val quranFileManager: QuranFileManager,
  private val audioInfoCommand: AudioInfoCommand,
  private val downloadInfoStreams: DownloadInfoStreams,
  private val audioCacheInvalidator: AudioCacheInvalidator
) {
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
  private val storageCache = QariDownloadInfoStorageCache()

  init {
    scope.launch {
      withContext(Dispatchers.IO) {
        populateCache()
        subscribeToChanges()
      }
    }
  }

  fun downloadedQariInfo(): Flow<List<QariDownloadInfo>> {
    return storageCache.flow()
  }

  fun downloadQariInfoFilteringNonDownloadedGappedQaris(): Flow<List<QariDownloadInfo>> {
    return downloadedQariInfo().map { list ->
      list.filter { qariDownloadInfo ->
        val qari = qariDownloadInfo.qari
        val gappedItem = qariDownloadInfo as? QariDownloadInfo.GappedQariDownloadInfo
        qari.isGapless ||
            // gapped qaris are only shown if they don't have a gapless alternative or if
            // some file for the qari is already downloaded.
            (!qari.hasGaplessAlternative ||
                qariDownloadInfo.fullyDownloadedSuras.isNotEmpty() ||
                (gappedItem?.partiallyDownloadedSuras?.isNotEmpty() ?: false))
      }
    }
  }

  private fun populateCache() {
    val audioDirectory = quranFileManager.audioFileDirectory() ?: return
    val qariDownloadInfo = audioInfoCommand.generateAllQariDownloadInfo(audioDirectory)
    storageCache.writeAll(qariDownloadInfo)
  }

  private suspend fun subscribeToChanges() {
    val downloadStream = downloadInfoStreams.downloadInfoStream()
        // only care to refresh after successful file downloads
      .filter { it is DownloadInfo.FileDownloaded }
      .mapNotNull { it.metadata as? AudioDownloadMetadata }
      .map { it.qariId }

    merge(downloadStream, audioCacheInvalidator.qarisToInvalidate())
      .collect { qariId ->
        updateQariInformationForQariId(qariId)
      }
  }

  private suspend fun updateQariInformationForQariId(qariId: Int) {
    val qariDownloadInfo = getUpdatedQariInformation(qariId)
    if (qariDownloadInfo != null) {
      withContext(Dispatchers.Main) {
        val updated = storageCache.lastValue()
          .map {
            // latest last value, but replace the qari item with our updated one
            if (it.qari.id == qariDownloadInfo.qari.id) qariDownloadInfo else it
          }
        storageCache.writeAll(updated)
      }
    }
  }

  private fun getUpdatedQariInformation(qariId: Int): QariDownloadInfo? {
    val lastInfo = storageCache.lastValue()
    val updatedQari = lastInfo.firstOrNull { it.qari.id == qariId } ?: return null
    val audioDirectory = quranFileManager.audioFileDirectory()
    return if (audioDirectory != null) {
      audioInfoCommand.generateQariDownloadInfo(updatedQari.qari, audioDirectory)
    } else {
      null
    }
  }
}
