package com.quran.mobile.feature.downloadmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
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
import com.quran.mobile.feature.downloadmanager.ui.sheikhdownload.RequestPostNotificationsPermissionDialog
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

  private var didRequestPostNotificationsPermission: Boolean = false

  private val requestPermissionLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
      // do nothing for now
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    qariId = intent.getIntExtra(EXTRA_QARI_ID, -1)
    if (qariId < 0) {
      finish()
      return
    }

    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? DownloadManagerComponentInterface
    injector?.downloadManagerComponentFactory()?.generate()?.inject(this)

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
            is SheikhDownloadDialog.PostNotificationsPermission ->
              RequestPostNotificationsPermissionDialog(::onCanRequestPermissions, ::onDoNotRequestPermissions)
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
        processPostNotificationsPermission {
          scope.launch {
            sheikhAudioPresenter.onSuraAction(qariId, sura)
          }
        }
      }
    } else {
      sheikhAudioPresenter.toggleSuraSelection(sura)
    }
  }

  private fun needPostNotificationsPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    } else {
      false
    }
  }

  private fun processPostNotificationsPermission(lambda: (() -> Unit)) {
    if (needPostNotificationsPermission() &&
      !didRequestPostNotificationsPermission &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      didRequestPostNotificationsPermission = true
      if (ActivityCompat.shouldShowRequestPermissionRationale(
          this,
          Manifest.permission.POST_NOTIFICATIONS
        )
      ) {
        sheikhAudioPresenter.showPostNotificationsRationaleDialog()
      } else {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      lambda()
    }
  }

  private fun onDownloadSelectionSelected() {
    processPostNotificationsPermission {
      scope.launch {
        sheikhAudioPresenter.onDownloadSelection(qariId)
      }
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

  private fun onCanRequestPermissions() {
    sheikhAudioPresenter.onCancelDialog()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private fun onDoNotRequestPermissions() {
    sheikhAudioPresenter.onCancelDialog()
  }

  companion object {
    const val EXTRA_QARI_ID = "SheikhAudioDownloadsActivity.extra_qari_id"
  }
}
