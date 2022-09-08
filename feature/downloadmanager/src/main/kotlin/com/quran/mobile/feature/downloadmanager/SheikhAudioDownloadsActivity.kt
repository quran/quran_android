package com.quran.mobile.feature.downloadmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.downloadmanager.di.DownloadManagerComponentInterface
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraOption
import com.quran.mobile.feature.downloadmanager.presenter.SearchTextUtil
import com.quran.mobile.feature.downloadmanager.presenter.SheikhAudioPresenter
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.DownloadProgressDialog
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.RemoveConfirmationDialog
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.SheikhDownloadToolbar
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.SheikhSuraInfoList
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.SuraRangeDialog
import com.quran.page.common.data.QuranNaming
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class SheikhAudioDownloadsActivity : ComponentActivity() {
  @Inject
  lateinit var quranNaming: QuranNaming

  @Inject
  lateinit var sheikhAudioPresenter: SheikhAudioPresenter

  private var qariId: Int = -1
  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    qariId = intent.getIntExtra(EXTRA_QARI_ID, -1)
    if (qariId < 0) {
      finish()
      return
    }

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DownloadManagerComponentInterface
    injector?.downloadManagerComponentBuilder()?.build()?.inject(this)

    val sheikhDownloadsFlow = sheikhAudioPresenter.sheikhInfo(qariId)
    val suras = (1..114).map {
      quranNaming.getSuraNameWithNumber(this, it, false)
    }.mapIndexed { suraIndex, name ->
      SuraOption(suraIndex + 1, name, SearchTextUtil.asSearchableString(name))
    }

    setContent {
      val singleItemState = remember { mutableStateOf<SuraForQari?>(null) }
      val selectionState = remember { mutableStateOf(listOf<SuraForQari>()) }
      val sheikhDownloadsState = sheikhDownloadsFlow.collectAsState(null)
      val currentDialog = remember { mutableStateOf(SheikhDownloadDialog.NONE) }
      val shouldWaitForDownloadStateDialog = remember { mutableStateOf(false) }

      val downloadProgressState =
        sheikhAudioPresenter.subscribeToDownloadInfo(qariId).collectAsState(null)
      if (downloadProgressState.value !is SuraDownloadStatusEvent.Done &&
          downloadProgressState.value !is SuraDownloadStatusEvent.Error) {
        shouldWaitForDownloadStateDialog.value = false
      }

      QuranTheme {
        val selectionInfo = selectionState.value
        Box(modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .fillMaxSize()) {
          Column {
            val currentDownloadState = sheikhDownloadsState.value
            val titleResource =
              currentDownloadState?.qariItem?.nameResource ?: R.string.audio_manager

            SheikhDownloadToolbar(
              titleResource = titleResource,
              isContextual = selectionInfo.isNotEmpty(),
              selectionInfo.isEmpty() || selectionInfo.any { !it.isDownloaded },
              selectionInfo.any { it.isDownloaded },
              downloadAction = {
                if (selectionInfo.isEmpty()) {
                  currentDialog.value = SheikhDownloadDialog.DOWNLOAD_RANGE_SELECTION
                } else {
                  onDownloadSelected(selectionInfo)
                  selectionState.value = emptyList()
                  shouldWaitForDownloadStateDialog.value =
                    downloadProgressState.value is SuraDownloadStatusEvent.Done ||
                        downloadProgressState.value is SuraDownloadStatusEvent.Error
                  currentDialog.value = SheikhDownloadDialog.DOWNLOAD_STATUS
                }
              },
              eraseAction = {
                if (selectionInfo.isNotEmpty()) {
                  currentDialog.value = SheikhDownloadDialog.REMOVE_CONFIRMATION
                } else {
                  onRemoveSelected(selectionInfo)
                  selectionState.value = emptyList()
                }
              },
              onBackAction = {
                if (selectionInfo.isEmpty()) {
                  finish()
                } else {
                  // end contextual mode on back with suras selected
                  selectionState.value = emptyList()
                }
              }
            )

            if (currentDownloadState != null) {
              SheikhSuraInfoList(
                sheikhUiModel = currentDownloadState,
                currentSelection = selectionState.value,
                quranNaming = quranNaming,
                onSelectionInfoChanged = { selectionState.value = it },
                onSuraActionRequested = {
                  if (it.isDownloaded) {
                    singleItemState.value = it
                    currentDialog.value = SheikhDownloadDialog.REMOVE_CONFIRMATION
                  } else {
                    onDownloadSelected(listOf(it))
                    shouldWaitForDownloadStateDialog.value =
                      downloadProgressState.value is SuraDownloadStatusEvent.Done ||
                          downloadProgressState.value is SuraDownloadStatusEvent.Error
                    currentDialog.value = SheikhDownloadDialog.DOWNLOAD_STATUS
                  }
                }
              )
            }
          }

          when (currentDialog.value) {
            SheikhDownloadDialog.REMOVE_CONFIRMATION ->
              RemoveConfirmationDialog(
                title = suraNameForRemovalOrNull(listOfNotNull(singleItemState.value) + selectionInfo),
                onConfirmation = {
                  currentDialog.value = SheikhDownloadDialog.NONE
                  onRemoveSelected(listOfNotNull(singleItemState.value) + selectionInfo)
                  singleItemState.value = null
                  selectionState.value = emptyList()
                },
                onDismiss = {
                  currentDialog.value = SheikhDownloadDialog.NONE
                  singleItemState.value = null
                }
              )
            SheikhDownloadDialog.DOWNLOAD_RANGE_SELECTION ->
              SuraRangeDialog(
                suras,
                onDownloadSelected = { start, end ->
                  currentDialog.value = SheikhDownloadDialog.NONE
                  val toDownload = (min(start, end)..max(start, end)).map {
                    SuraForQari(it, false)
                  }
                  onDownloadSelected(toDownload)
                  selectionState.value = emptyList()
                  shouldWaitForDownloadStateDialog.value =
                    downloadProgressState.value is SuraDownloadStatusEvent.Done ||
                        downloadProgressState.value is SuraDownloadStatusEvent.Error
                  currentDialog.value = SheikhDownloadDialog.DOWNLOAD_STATUS
                },
                onDismiss = { currentDialog.value = SheikhDownloadDialog.NONE }
              )
            SheikhDownloadDialog.DOWNLOAD_STATUS ->
              if (!shouldWaitForDownloadStateDialog.value) {
                DownloadProgressDialog(
                  currentEvent = downloadProgressState.value,
                  onDownloadDone = { currentDialog.value = SheikhDownloadDialog.NONE },
                  onDownloadError = { /* TODO */ currentDialog.value = SheikhDownloadDialog.NONE },
                  onCancel = ::onCancelSelected
                )
              }
            SheikhDownloadDialog.NONE -> {}
          }
        }
      }
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun suraNameForRemovalOrNull(items: List<SuraForQari>): String? {
    val removals = items.filter { it.isDownloaded }
    return if (removals.size == 1) {
      quranNaming.getSuraName(this, items.first().sura)
    } else {
      null
    }
  }

  private fun onDownloadSelected(selectedSuras: List<SuraForQari>) {
    val surasToDownload = selectedSuras.map { it.sura }
    scope.launch {
      sheikhAudioPresenter.downloadSuras(qariId, surasToDownload)
    }
  }

  private fun onRemoveSelected(selectedSuras: List<SuraForQari>) {
    val surasToRemove = selectedSuras.map { it.sura }
    scope.launch {
      sheikhAudioPresenter.removeSuras(qariId, surasToRemove)
    }
  }

  private fun onCancelSelected() {
    sheikhAudioPresenter.cancelDownloads()
  }

  companion object {
    const val EXTRA_QARI_ID = "SheikhAudioDownloadsActivity.extra_qari_id"
  }
}
