package com.quran.mobile.feature.downloadmanager.presenter

import com.quran.data.core.QuranFileManager
import com.quran.data.di.ActivityScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidator
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.util.AudioExtensionDecider
import com.quran.mobile.common.download.DownloadConstants
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
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
  private val audioExtensionDecider: AudioExtensionDecider,
  private val downloader: Downloader
) {
  private val selectedEntriesFlow = MutableStateFlow<List<EntryForQari>>(emptyList())
  private val currentDialogFlow = MutableStateFlow<SheikhDownloadDialog>(SheikhDownloadDialog.None)

  fun sheikhInfo(qariId: Int): Flow<SheikhUiModel> {
    return sheikhInfoFlow(qariId)
  }

  private fun sheikhInfoFlow(qariId: Int): Flow<SheikhUiModel> {
    return combine(qariDownloadInfoManager.downloadedQariInfo(), selectedEntriesFlow, currentDialogFlow) {
        downloadInfo, selectedSuras, currentDialog ->
        val qariInfo = downloadInfo.firstOrNull { it.qari.id == qariId }
        if (qariInfo == null) {
          null
        } else {
          val suraModels = (1..114)
            .map { sura ->
              EntryForQari.SuraForQari(
                sura,
                qariInfo.fullyDownloadedSuras.contains(sura)
              )
            }
          val databaseState = qariInfo.asDatabaseForQariEntry()
          val databases = if (databaseState != null) {
            listOf(databaseState)
          } else {
            listOf()
          }

          SheikhUiModel(qariInfo.qari, databases + suraModels, selectedSuras, currentDialog)
        }
      }
      .filterNotNull()
  }

  private fun downloadInfo(qariId: Int): Flow<SuraDownloadStatusEvent> {
    return downloadInfoStream.downloadInfoStream()
      .filterIsInstance<DownloadInfo.DownloadEvent>()
      .mapNotNull { download ->
        (download.metadata as? AudioDownloadMetadata)?.let { metadata -> download to metadata }
      }
      .filter { (_, audioDownloadMetadata) -> audioDownloadMetadata.qariId == qariId }
      .map { (downloadInfo, _) -> downloadInfo }
      .mapNotNull { it.asSuraDownloadedStatusEvent() }
  }

  fun selectEntry(entry: EntryForQari) {
    selectedEntriesFlow.value += entry
  }

  fun toggleEntrySelection(entry: EntryForQari) {
    val currentSelection = selectedEntriesFlow.value
    if (entry in currentSelection) {
      selectedEntriesFlow.value = currentSelection - entry
    } else {
      selectedEntriesFlow.value = currentSelection + entry
    }
  }

  fun clearSelection() {
    selectedEntriesFlow.value = emptyList()
  }

  fun showPostNotificationsRationaleDialog() {
    currentDialogFlow.value = SheikhDownloadDialog.PostNotificationsPermission
  }

  suspend fun onSuraAction(qariId: Int, entry: EntryForQari) {
    if (entry.isDownloaded) {
      onRemoveSelection()
    } else {
      onDownloadSelection(qariId)
    }
  }

  suspend fun onDownloadRange(qariId: Int, toDownload: List<Int>, downloadDatabase: Boolean) {
    onDownload(qariId, toDownload, downloadDatabase)
  }

  suspend fun onDownloadSelection(qariId: Int) {
    val currentSelection = selectedEntriesFlow.value
    if (currentSelection.isEmpty()) {
      currentDialogFlow.value = SheikhDownloadDialog.DownloadRangeSelection
    } else {
      selectedEntriesFlow.value = emptyList()

      val suras = currentSelection.filterIsInstance<EntryForQari.SuraForQari>().map { it.sura }
      val downloadDatabase = currentSelection.filterIsInstance<EntryForQari.DatabaseForQari>().isNotEmpty()
      onDownload(qariId, suras, downloadDatabase)
    }
  }

  private suspend fun onDownload(qariId: Int, toDownload: List<Int>, downloadDatabase: Boolean) {
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
    downloadSuras(qariId, toDownload, downloadDatabase)
  }

  private suspend fun downloadSuras(qariId: Int, suras: List<Int>, downloadDatabase: Boolean) {
    withContext(Dispatchers.IO) {
      val qariInfo = qariInfoForId(qariId)
      if (qariInfo != null) {
        val qari = qariInfo.qari
        val qariDatabase = qari.databasePath()
        val alreadyDownloaded = qariInfo.fullyDownloadedSuras.toSet()
        val sorted = (suras - alreadyDownloaded).sorted()
        if (sorted.isNotEmpty()) {
          downloader.downloadCompleteSuras(qari, sorted, downloadDatabase)
        } else if (downloadDatabase) {
          if (qariDatabase != null && !qariDatabase.exists()) {
            downloader.downloadAudioDatabase(qari)
          }
        }
      }
    }
  }

  fun cancelDownloads() {
    downloader.cancelDownloads()
  }

  fun onRemoveSelection() {
    currentDialogFlow.value = SheikhDownloadDialog.RemoveConfirmation(selectedEntriesFlow.value)
  }

  fun onCancelDialog() {
    currentDialogFlow.value = SheikhDownloadDialog.None
  }

  suspend fun removeSuras(qariId: Int, suras: List<Int>, removeDatabase: Boolean) {
    selectedEntriesFlow.value = emptyList()
    withContext(Dispatchers.IO) {
      val qariInfo = qariInfoForId(qariId)
      val directory = quranFileManager.audioFileDirectory()
      if (qariInfo != null && directory != null) {
        val qari = qariInfo.qari
        if (qari.isGapless) {
          if (removeDatabase) {
            val databasePath = qari.databasePath()
            if (databasePath?.exists() == true) {
              databasePath.delete()
            }
          }
          val allowedExtensions = audioExtensionDecider.allowedAudioExtensions(qari)
          suras.flatMap {
            val paddedSura = it.toString().padStart(3, '0')
            allowedExtensions.map { ext ->
              File(File(directory, qari.path), "$paddedSura.$ext")
            }
          }.filter { it.exists() }
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
      is DownloadInfo.DownloadBatchError -> SuraDownloadStatusEvent.Error(errorCode = this.errorId, this.errorString)
      is DownloadInfo.DownloadBatchSuccess -> SuraDownloadStatusEvent.Done
      is DownloadInfo.FileDownloadProgress ->
        SuraDownloadStatusEvent.Progress(
          this.progress,
          this.sura ?: -1,
          this.ayah ?: -1,
          this.downloadedSize ?: -1,
          this.totalSize ?: -1
        )

      is DownloadInfo.DownloadEvent, DownloadInfo.DownloadRequested, DownloadInfo.RequestDownloadNetworkPermission -> null
    }
  }

  private fun QariDownloadInfo.asDatabaseForQariEntry(): EntryForQari.DatabaseForQari? {
    val databaseFile = qari.databasePath()
    return if (databaseFile == null) {
      null
    } else {
      if (databaseFile.exists()) {
        EntryForQari.DatabaseForQari(true)
      } else {
        EntryForQari.DatabaseForQari(false)
      }
    }
  }

  private fun Qari.databasePath(): File? {
    val database = databaseName
    return if (database != null) {
      val path = quranFileManager.audioFileDirectory()
      if (path != null) {
        File(File(path, this.path), "$database.db")
      } else {
        null
      }
    } else {
      null
    }
  }
}
