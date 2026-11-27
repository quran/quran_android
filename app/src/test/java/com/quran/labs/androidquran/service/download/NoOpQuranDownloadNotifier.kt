package com.quran.labs.androidquran.service.download

import com.quran.labs.androidquran.service.util.DownloadProgressEvent
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier

class NoOpQuranDownloadNotifier : QuranDownloadNotifier {
  override fun resetNotifications() {
  }

  override fun notifyProgress(
    details: QuranDownloadNotifier.NotificationDetails,
    downloadedSize: Long,
    totalSize: Long
  ): DownloadProgressEvent = DownloadProgressEvent.Downloading(
    downloadType = details.type,
    downloadKey = details.key,
    title = details.title
  )

  override fun notifyDownloadProcessing(
    details: QuranDownloadNotifier.NotificationDetails,
    done: Int,
    total: Int
  ): DownloadProgressEvent = DownloadProgressEvent.Processing(
    downloadType = details.type,
    downloadKey = details.key,
    title = details.title
  )

  override fun notifyDownloadSuccessful(
    details: QuranDownloadNotifier.NotificationDetails
  ): DownloadProgressEvent = DownloadProgressEvent.Success(
    downloadType = details.type,
    downloadKey = details.key,
    title = details.title
  )

  override fun notifyFileDownloaded(
    details: QuranDownloadNotifier.NotificationDetails,
    filename: String
  ) {
  }

  override fun broadcastDownloadSuccessful(
    details: QuranDownloadNotifier.NotificationDetails
  ): DownloadProgressEvent = DownloadProgressEvent.Success(
    downloadType = details.type,
    downloadKey = details.key,
    title = details.title
  )

  override fun notifyError(
    errorCode: Int,
    isFatal: Boolean,
    filename: String?,
    details: QuranDownloadNotifier.NotificationDetails
  ): DownloadProgressEvent = DownloadProgressEvent.Error(
    downloadType = details.type,
    downloadKey = details.key,
    title = details.title,
    errorCode = errorCode,
    isFatal = isFatal
  )

  override fun notifyDownloadStarting() {
  }

  override fun stopForeground() {
  }
}
