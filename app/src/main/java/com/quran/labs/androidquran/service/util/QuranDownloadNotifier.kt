package com.quran.labs.androidquran.service.util

import android.os.Parcelable
import com.quran.labs.androidquran.data.Constants
import com.quran.mobile.common.download.DownloadConstants

interface QuranDownloadNotifier {

  class NotificationDetails(
    @JvmField var title: String?,
    @JvmField var key: String?,
    @JvmField var type: Int,
    @JvmField var metadata: Parcelable?
  ) {
    @JvmField var currentFile: Int = 0
    @JvmField var totalFiles: Int = 0
    @JvmField var sura: Int = 0
    @JvmField var ayah: Int = 0
    @JvmField internal var sendIndeterminate: Boolean = false
    @JvmField var isGapless: Boolean = false

    fun setFileStatus(current: Int, total: Int) {
      totalFiles = total
      currentFile = current
    }

    fun setIsGapless(isGapless: Boolean) {
      this.isGapless = isGapless
    }
  }

  fun resetNotifications()
  fun notifyProgress(
    details: NotificationDetails,
    downloadedSize: Long,
    totalSize: Long
  ): DownloadProgressEvent

  fun notifyDownloadProcessing(
    details: NotificationDetails,
    done: Int,
    total: Int
  ): DownloadProgressEvent

  fun notifyDownloadSuccessful(details: NotificationDetails): DownloadProgressEvent
  fun notifyFileDownloaded(details: NotificationDetails, filename: String)
  fun broadcastDownloadSuccessful(details: NotificationDetails): DownloadProgressEvent
  fun notifyError(
    errorCode: Int,
    isFatal: Boolean,
    filename: String?,
    details: NotificationDetails
  ): DownloadProgressEvent

  fun notifyDownloadStarting()
  fun stopForeground()

  companion object {
    // error messages
    const val ERROR_DISK_SPACE = DownloadConstants.ERROR_DISK_SPACE
    const val ERROR_PERMISSIONS = DownloadConstants.ERROR_PERMISSIONS
    const val ERROR_NETWORK = DownloadConstants.ERROR_NETWORK
    const val ERROR_INVALID_DOWNLOAD = DownloadConstants.ERROR_INVALID_DOWNLOAD
    const val ERROR_CANCELLED = DownloadConstants.ERROR_CANCELLED
    const val ERROR_GENERAL = DownloadConstants.ERROR_GENERAL

    // notification ids
    const val DOWNLOADING_COMPLETE_NOTIFICATION = Constants.NOTIFICATION_ID_DOWNLOADING_COMPLETE
  }
}
