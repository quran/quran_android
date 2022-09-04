package com.quran.mobile.feature.downloadmanager.presenter

import com.quran.data.core.QuranFileManager
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidator
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.model.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.QariDownloadInfo
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@ActivityScope
class SheikhAudioPresenter @Inject constructor(
  private val qariDownloadInfoManager: QariDownloadInfoManager,
  private val downloadInfoStream: DownloadInfoStreams,
  private val quranFileManager: QuranFileManager,
  private val audioCacheInvalidator: AudioCacheInvalidator,
  private val downloader: Downloader
) {

  fun sheikhInfo(qariId: Int): Flow<SheikhUiModel> {
    return sheikhInfoFlow(qariId)
  }

  private fun sheikhInfoFlow(qariId: Int): Flow<SheikhUiModel> {
    return qariDownloadInfoManager.downloadedQariInfo()
      .map { downloadInfo ->
        val qariInfo = downloadInfo.firstOrNull { it.qari.id == qariId }
        if (qariInfo == null) {
          null
        } else {
          val suraModels = (1..114)
            .map { sura ->
              SuraForQari(sura, qariInfo.fullyDownloadedSuras.contains(sura))
            }
          SheikhUiModel(qariInfo.qari, suraModels)
        }
      }
      .filterNotNull()
  }

  fun subscribeToDownloadInfo(qariId: Int): Flow<SuraDownloadStatusEvent> {
    return downloadInfoStream.downloadInfoStream()
      .mapNotNull {
        val metadata = it.metadata as? AudioDownloadMetadata
        if (metadata == null) {
          null
        } else {
          it to metadata
        }
      }
      .filter { (_, audioDownloadMetadata) -> audioDownloadMetadata.qariId == qariId }
      .map { (downloadInfo, _) -> downloadInfo }
      .mapNotNull {
        val suraString = it.filename.substringBeforeLast(".")
        val sura = suraString.toIntOrNull()
        if (sura != null) {
          it.asSuraDownloadedStatusEvent(sura)
        } else {
          null
        }
      }
  }

  suspend fun downloadSuras(qariId: Int, suras: List<Int>) {
    withContext(Dispatchers.IO) {
      val qariInfo = qariInfoForId(qariId)
      if (qariInfo != null) {
        val qari = qariInfo.qari
        val alreadyDownloaded = qariInfo.fullyDownloadedSuras.toSet()
        val sorted = (suras - alreadyDownloaded).sorted()
        if (sorted.isNotEmpty()) {
          if (sorted.size == 1 || (1 + sorted.last() - sorted.first()) == sorted.size) {
            downloader.downloadSuras(qari, sorted.first(), sorted.last())
          } else {
            sorted.forEach { downloader.downloadSura(qari, it) }
          }
        }
      }
    }
  }

  suspend fun removeSuras(qariId: Int, suras: List<Int>) {
    withContext(Dispatchers.IO) {
      val qariInfo = qariInfoForId(qariId)
      val directory = quranFileManager.audioFileDirectory()
      if (qariInfo != null && directory != null) {
        val qari = qariInfo.qari
        if (qari.isGapless) {
          suras.map { it.toString().padStart(3, '0') + ".mp3" }
            .map { File(File(directory, qari.path), it) }
            .filter { it.exists() }
            .forEach { it.delete() }
        } else {
          suras.map { File(File(directory, qari.path), it.toString()) }
            .filter { it.isDirectory }
            .forEach { it.deleteRecursively() }
        }
        audioCacheInvalidator.invalidateCacheForQari(qariId)
      }
    }
  }

  private suspend fun qariInfoForId(qariId: Int): QariDownloadInfo? {
    return withContext(Dispatchers.IO) {
      qariDownloadInfoManager.downloadedQariInfo()
        .map { qariDownloadInfo ->
          qariDownloadInfo.firstOrNull { it.qari.id == qariId }
        }
        .firstOrNull()
    }
  }

  private fun DownloadInfo.asSuraDownloadedStatusEvent(sura: Int): SuraDownloadStatusEvent {
    return when (this) {
      is DownloadInfo.DownloadComplete -> SuraDownloadStatusEvent.Downloaded(sura)
      is DownloadInfo.DownloadError -> SuraDownloadStatusEvent.Canceled(sura)
    }
  }
}
