package com.quran.labs.androidquran.service.download

import android.content.Intent
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier

class NoOpQuranDownloadNotifier : QuranDownloadNotifier {
  override fun resetNotifications() {
  }

  override fun notifyProgress(
    details: QuranDownloadNotifier.NotificationDetails?,
    downloadedSize: Long,
    totalSize: Long
  ): Intent = Intent()

  override fun notifyDownloadProcessing(
    details: QuranDownloadNotifier.NotificationDetails?,
    done: Int,
    total: Int
  ): Intent = Intent()

  override fun notifyDownloadSuccessful(details: QuranDownloadNotifier.NotificationDetails?): Intent {
    return Intent()
  }

  override fun notifyFileDownloaded(
    details: QuranDownloadNotifier.NotificationDetails?,
    filename: String?
  ) {
  }

  override fun broadcastDownloadSuccessful(details: QuranDownloadNotifier.NotificationDetails?): Intent {
    return Intent()
  }

  override fun notifyError(
    errorCode: Int,
    isFatal: Boolean,
    filename: String?,
    details: QuranDownloadNotifier.NotificationDetails?
  ): Intent {
    return Intent()
  }

  override fun notifyDownloadStarting() {
  }

  override fun stopForeground() {
  }
}
