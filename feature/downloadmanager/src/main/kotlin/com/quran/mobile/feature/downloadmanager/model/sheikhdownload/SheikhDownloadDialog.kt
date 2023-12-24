package com.quran.mobile.feature.downloadmanager.model.sheikhdownload

import kotlinx.coroutines.flow.Flow

sealed class SheikhDownloadDialog {
  data object None : SheikhDownloadDialog()
  data class RemoveConfirmation(val surasToRemove: List<SuraForQari>): SheikhDownloadDialog()
  data object DownloadRangeSelection: SheikhDownloadDialog()
  data object PostNotificationsPermission : SheikhDownloadDialog()
  data class DownloadStatus(val statusFlow: Flow<SuraDownloadStatusEvent.Progress>): SheikhDownloadDialog()
  data class DownloadError(val errorCode: Int, val errorString: String): SheikhDownloadDialog()
}
