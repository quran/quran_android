package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

import kotlinx.coroutines.flow.Flow

sealed class SheikhDownloadDialog {
  object None : SheikhDownloadDialog()
  data class RemoveConfirmation(val surasToRemove: List<SuraForQari>): SheikhDownloadDialog()
  object DownloadRangeSelection: SheikhDownloadDialog()
  data class DownloadStatus(val statusFlow: Flow<SuraDownloadStatusEvent.Progress>): SheikhDownloadDialog()
  data class DownloadError(val errorCode: Int, val errorString: String): SheikhDownloadDialog()
}
