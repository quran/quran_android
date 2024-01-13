package com.quran.mobile.feature.downloadmanager.presenter

import com.quran.data.core.QuranFileManager
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidator
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.mobile.common.download.DownloadConstants
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
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
  private val selectedSurasFlow = MutableStateFlow<List<SuraForQari>>(emptyList())
  private val currentDialogFlow = MutableStateFlow<SheikhDownloadDialog>(SheikhDownloadDialog.None)

  fun sheikhInfo(qariId: Int): Flow<SheikhUiModel> {
    return sheikhInfoFlow(qariId)
  }

  private fun sheikhInfoFlow(qariId: Int): Flow<SheikhUiModel> {
    return combine(qariDownloadInfoManager.downloadedQariInfo(), selectedSurasFlow, currentDialogFlow) {
        downloadInfo, selectedSuras, currentDialog ->
        val qariInfo = downloadInfo.firstOrNull { it.qari.id == qariId }
        if (qariInfo == null) {
          null
        } else {
          val suraModels = (1..114)
            .map { sura -> SuraForQari(sura, qariInfo.fullyDownloadedSuras.contains(sura)) }
          SheikhUiModel(qariInfo.qari, suraModels, selectedSuras, currentDialog)
        }
      }
      .filterNotNull()
  }

  private fun downloadInfo(qariId: Int): Flow<SuraDownloadStatusEvent> {
    return downloadInfoStream.downloadInfoStream()
      .mapNotNull { download ->
        (download.metadata as? AudioDownloadMetadata)?.let { metadata -> download to metadata }
      }
      .filter { (_, audioDownloadMetadata) -> audioDownloadMetadata.qariId == qariId }
      .map { (downloadInfo, _) -> downloadInfo }
      .mapNotNull { it.asSuraDownloadedStatusEvent() }
  }

  fun selectSura(sura: SuraForQari) {
    selectedSurasFlow.value = selectedSurasFlow.value + sura
  }

  fun toggleSuraSelection(sura: SuraForQari) {
    val currentSelection = selectedSurasFlow.value
    if (sura in currentSelection) {
      selectedSurasFlow.value = currentSelection - sura
    } else {
      selectedSurasFlow.value = currentSelection + sura
    }
  }

  fun clearSelection() {
    selectedSurasFlow.value = emptyList()
  }

  fun showPostNotificationsRationaleDialog() {
    currentDialogFlow.value = SheikhDownloadDialog.PostNotificationsPermission
  }

  suspend fun onSuraAction(qariId: Int, sura: SuraForQari) {
    if (sura.isDownloaded) {
      onRemoveSelection()
    } else {
      onDownloadSelection(qariId)
    }
  }

  suspend fun onDownloadRange(qariId: Int, toDownload: List<Int>) {
    onDownload(qariId, toDownload)
  }

  suspend fun onDownloadSelection(qariId: Int) {
    val currentSelection = selectedSurasFlow.value
    if (currentSelection.isEmpty()) {
      currentDialogFlow.value = SheikhDownloadDialog.DownloadRangeSelection
    } else {
      selectedSurasFlow.value = emptyList()

      val suras = currentSelection.map { it.sura }
      onDownload(qariId, suras)
    }
  }

  private suspend fun onDownload(qariId: Int, toDownload: List<Int>) {
    val flow = downloadInfo(qariId)
      .onEach {
        if (it is SuraDownloadStatusEvent.Done) {
          currentDialogFlow.value = SheikhDownloadDialog.None
        } else if (it is SuraDownloadStatusEvent.Error) {
          if (it.errorCode == DownloadConstants.ERROR_CANCELLED) {
            currentDialogFlow.value = SheikhDownloadDialog.None
          } else {
            currentDialogFlow.value = SheikhDownloadDialog.DownloadError(it.errorCode, it.errorMessage)
          }
        }
      }
      .filterIsInstance<SuraDownloadStatusEvent.Progress>()
    currentDialogFlow.value = SheikhDownloadDialog.DownloadStatus(flow)
    downloadSuras(qariId, toDownload)
  }

  private suspend fun downloadSuras(qariId: Int, suras: List<Int>) {
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

  fun cancelDownloads() {
    downloader.cancelDownloads()
  }

  fun onRemoveSelection() {
    currentDialogFlow.value = SheikhDownloadDialog.RemoveConfirmation(selectedSurasFlow.value)
  }

  fun onCancelDialog() {
    currentDialogFlow.value = SheikhDownloadDialog.None
  }

  suspend fun removeSuras(qariId: Int, suras: List<Int>) {
    selectedSurasFlow.value = emptyList()
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
    currentDialogFlow.value = SheikhDownloadDialog.None
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

  private fun DownloadInfo.asSuraDownloadedStatusEvent(): SuraDownloadStatusEvent? {
    return when (this) {
      is DownloadInfo.FileDownloaded -> null
      is DownloadInfo.DownloadBatchError -> SuraDownloadStatusEvent.Error(this.errorId, this.errorString)
      is DownloadInfo.DownloadBatchSuccess -> SuraDownloadStatusEvent.Done
      is DownloadInfo.FileDownloadProgress ->
        SuraDownloadStatusEvent.Progress(
          this.progress,
          this.sura ?: -1,
          this.ayah ?: -1,
          this.downloadedSize ?: -1,
          this.totalSize ?: -1
        )
    }
  }
}
