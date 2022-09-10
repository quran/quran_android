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
import androidx.compose.ui.Modifier
import com.quran.common.search.SearchTextUtil
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.di.QuranApplicationComponentProvider
import com.quran.mobile.feature.downloadmanager.di.DownloadManagerComponentInterface
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraOption
import com.quran.mobile.feature.downloadmanager.presenter.SheikhAudioPresenter
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.DownloadErrorDialog
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

    val isRtl = SearchTextUtil.isRtl(quranNaming.getSuraNameWithNumber(this, 1, false))
    val suras = (1..114).map {
      quranNaming.getSuraNameWithNumber(this, it, false)
    }.mapIndexed { suraIndex, name ->
      SuraOption(suraIndex + 1, name, SearchTextUtil.asSearchableString(name, isRtl))
    }
    val sheikhDownloadsFlow = sheikhAudioPresenter.sheikhInfo(qariId)

    setContent {
      val sheikhDownloadsState = sheikhDownloadsFlow.collectAsState(null)

      QuranTheme {
        val currentDownloadState = sheikhDownloadsState.value
        Box(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxSize()
        ) {
          Column {
            val titleResource =
              currentDownloadState?.qariItem?.nameResource ?: R.string.audio_manager
            val selectionInfo = currentDownloadState?.selections ?: emptyList()

            SheikhDownloadToolbar(
              titleResource = titleResource,
              isContextual = selectionInfo.isNotEmpty(),
              selectionInfo.isEmpty() || selectionInfo.any { !it.isDownloaded },
              selectionInfo.any { it.isDownloaded },
              downloadAction = ::onDownloadSelectionSelected,
              eraseAction = sheikhAudioPresenter::onRemoveSelection,
              onBackAction = {
                if (selectionInfo.isEmpty()) {
                  finish()
                } else {
                  // end contextual mode on back with suras selected
                  sheikhAudioPresenter.clearSelection()
                }
              }
            )

            if (currentDownloadState != null) {
              SheikhSuraInfoList(
                sheikhUiModel = currentDownloadState,
                currentSelection = currentDownloadState.selections,
                quranNaming = quranNaming,
                onSuraClicked = { onSuraClicked(currentDownloadState.selections, it) },
                onSelectionStarted = { onSuraClicked(currentDownloadState.selections, it, true) }
              )
            }
          }

          when (val dialog = currentDownloadState?.dialog) {
            is SheikhDownloadDialog.RemoveConfirmation ->
              RemoveConfirmationDialog(
                title = suraNameForRemovalOrNull(dialog.surasToRemove),
                onConfirmation = { onRemoveSelectionConfirmed(currentDownloadState.selections) },
                onDismiss = sheikhAudioPresenter::onCancelDialog
              )
            is SheikhDownloadDialog.DownloadRangeSelection ->
              SuraRangeDialog(
                suras,
                onDownloadSelected = { start, end -> onDownloadRange(start, end) },
                onDismiss = sheikhAudioPresenter::onCancelDialog
              )
            is SheikhDownloadDialog.DownloadStatus ->
              DownloadProgressDialog(
                progressEvents = dialog.statusFlow,
                onCancel = sheikhAudioPresenter::cancelDownloads
              )
            is SheikhDownloadDialog.DownloadError ->
              DownloadErrorDialog(dialog.errorString, sheikhAudioPresenter::onCancelDialog)
            else -> {}
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

  private fun onSuraClicked(
    selectedSuras: List<SuraForQari>,
    sura: SuraForQari,
    isStartSelection: Boolean = false
  ) {
    if (selectedSuras.isEmpty()) {
      sheikhAudioPresenter.selectSura(sura)
      if (!isStartSelection) {
        scope.launch {
          sheikhAudioPresenter.onSuraAction(qariId, sura)
        }
      }
    } else {
      sheikhAudioPresenter.toggleSuraSelection(sura)
    }
  }

  private fun onDownloadSelectionSelected() {
    scope.launch {
      sheikhAudioPresenter.onDownloadSelection(qariId)
    }
  }

  private fun onDownloadRange(start: Int, end: Int) {
    val toDownload = (min(start, end)..max(start, end)).map { it }
    scope.launch {
      sheikhAudioPresenter.onDownloadRange(qariId, toDownload)
    }
  }

  private fun onRemoveSelectionConfirmed(selectedSuras: List<SuraForQari>) {
    val surasToRemove = selectedSuras.map { it.sura }
    scope.launch {
      sheikhAudioPresenter.removeSuras(qariId, surasToRemove)
    }
  }

  companion object {
    const val EXTRA_QARI_ID = "SheikhAudioDownloadsActivity.extra_qari_id"
  }
}
