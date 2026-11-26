package com.quran.labs.androidquran.service.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.DOWNLOADING_COMPLETE_NOTIFICATION
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_CANCELLED
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_DISK_SPACE
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_GENERAL
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_INVALID_DOWNLOAD
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_NETWORK
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.Companion.ERROR_PERMISSIONS
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import com.quran.labs.androidquran.util.NotificationChannelUtil
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import timber.log.Timber

class QuranDownloadNotifierImpl(
  context: Context,
  private val service: Service,
  private val downloadInfoStreams: DownloadInfoStreams
) : QuranDownloadNotifier {

  private val appContext: Context = context.applicationContext
  private val notificationManager: NotificationManager =
    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val contentIntent: PendingIntent
  private val notificationColor: Int = ContextCompat.getColor(appContext, R.color.notification_color)
  private var lastProgress: Int = -1
  private var lastMaximum: Int = -1
  private var isForeground: Boolean = false

  init {
    val notificationIntent = Intent(appContext, QuranDataActivity::class.java)
    contentIntent = PendingIntent.getActivity(
      appContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
    )

    val channelName = appContext.getString(R.string.notification_channel_download)
    NotificationChannelUtil.setupNotificationChannel(
      notificationManager, NOTIFICATION_CHANNEL_ID, channelName
    )
  }

  override fun resetNotifications() {
    // hide any previous errors, canceled, etc
    lastMaximum = -1
    lastProgress = -1
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION)
    notificationManager.cancel(DOWNLOADING_COMPLETE_NOTIFICATION)
  }

  override fun notifyProgress(
    details: NotificationDetails,
    downloadedSize: Long,
    totalSize: Long
  ): DownloadProgressEvent {
    val max = 100
    var progress = 0

    // send indeterminate if total size is 0 or less or the notification
    // details says to always send indeterminate.
    var isIndeterminate = details.sendIndeterminate
    if (!isIndeterminate && totalSize <= 0) {
      isIndeterminate = true
    }

    if (!isIndeterminate) {
      // calculate percentage based on files downloaded, files left,
      // and percentage of this file that is left
      val percent = (1.0 * downloadedSize) / (1.0 * totalSize)
      progress = if (details.isGapless) {
        (percent * 100).toInt()
      } else {
        val percentPerFile = 100.0f / details.totalFiles
        ((percentPerFile * (details.currentFile - 1)) + (percent * percentPerFile)).toInt()
      }

      if (details.sura > 0 && details.ayah > 0) {
        progress = ((details.currentFile.toFloat() / details.totalFiles.toFloat()) * 100.0f).toInt()
      }
    }

    showNotification(
      details.title,
      appContext.getString(com.quran.mobile.common.download.R.string.downloading),
      DOWNLOADING_NOTIFICATION, true, max, progress, isIndeterminate, !isForeground
    )

    val downloadInfo = DownloadInfo.FileDownloadProgress(
      details.key ?: "",
      details.type,
      details.metadata,
      if (isIndeterminate) -1 else progress,
      if (details.sura > 0) details.sura else null,
      if (details.ayah > 0) details.ayah else null,
      if (isIndeterminate) null else downloadedSize,
      if (isIndeterminate) null else totalSize,
      // currentFile is 0-based, but we want to send 1-based since it doesn't make sense
      // to say "downloading file 0/1"
      details.currentFile + 1,
      details.totalFiles
    )
    downloadInfoStreams.emitEvent(downloadInfo)

    val event = DownloadProgressEvent.Downloading(
      downloadType = details.type,
      downloadKey = details.key,
      title = details.title,
      progress = if (isIndeterminate) -1 else progress,
      downloadedSize = if (isIndeterminate) -1L else downloadedSize,
      totalSize = if (isIndeterminate) -1L else totalSize,
      sura = if (details.sura > 0) details.sura else -1,
      ayah = if (details.sura > 0) details.ayah else -1
    )
    DownloadProgressBus.emit(event)
    return event
  }

  override fun notifyDownloadProcessing(
    details: NotificationDetails,
    done: Int,
    total: Int
  ): DownloadProgressEvent {
    val processingString = appContext.getString(R.string.download_processing)
    showNotification(
      details.title, processingString,
      DOWNLOADING_NOTIFICATION, true, !isForeground
    )

    val event = if (total > 0) {
      val progress = ((100.0 * done) / (1.0 * total)).toInt()
      DownloadProgressEvent.Processing(
        downloadType = details.type,
        downloadKey = details.key,
        title = details.title,
        progress = progress,
        processedFiles = done,
        totalFiles = total
      )
    } else {
      DownloadProgressEvent.Processing(
        downloadType = details.type,
        downloadKey = details.key,
        title = details.title
      )
    }

    DownloadProgressBus.emit(event)
    return event
  }

  override fun notifyDownloadSuccessful(details: NotificationDetails): DownloadProgressEvent {
    val successString = appContext.getString(R.string.download_successful)
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION)

    lastMaximum = -1
    lastProgress = -1
    showNotification(
      details.title, successString,
      DOWNLOADING_COMPLETE_NOTIFICATION, false, false
    )

    // this emission is once per set of downloads (per batch). notifyFileDownload is per file.
    val downloadInfo = DownloadInfo.DownloadBatchSuccess(
      details.key ?: "", details.type, details.metadata
    )
    downloadInfoStreams.emitEvent(downloadInfo)

    return broadcastDownloadSuccessful(details)
  }

  override fun notifyFileDownloaded(details: NotificationDetails, filename: String) {
    val downloadInfo = DownloadInfo.FileDownloaded(
      details.key ?: "", details.type, details.metadata, filename, details.sura, details.ayah
    )
    downloadInfoStreams.emitEvent(downloadInfo)
  }

  override fun broadcastDownloadSuccessful(details: NotificationDetails): DownloadProgressEvent {
    val event = DownloadProgressEvent.Success(
      downloadType = details.type,
      downloadKey = details.key,
      title = details.title
    )
    DownloadProgressBus.emit(event)
    return event
  }

  override fun notifyError(
    errorCode: Int,
    isFatal: Boolean,
    filename: String?,
    details: NotificationDetails
  ): DownloadProgressEvent {
    val errorId = when (errorCode) {
      ERROR_DISK_SPACE -> R.string.download_error_disk
      ERROR_NETWORK -> R.string.download_error_network
      ERROR_PERMISSIONS -> R.string.download_error_perms
      ERROR_INVALID_DOWNLOAD -> {
        if (!isFatal) {
          R.string.download_error_invalid_download_retry
        } else {
          R.string.download_error_invalid_download
        }
      }
      ERROR_CANCELLED -> R.string.notification_download_canceled
      ERROR_GENERAL -> R.string.download_error_general
      else -> R.string.download_error_general
    }

    val errorString = appContext.getString(errorId)
    if (isFatal) {
      ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
      isForeground = false
    }

    showNotification(
      details.title, errorString,
      DOWNLOADING_ERROR_NOTIFICATION, false, false
    )

    if (isFatal) {
      val downloadInfo = DownloadInfo.DownloadBatchError(
        details.key ?: "", details.type, details.metadata, errorCode, errorId, errorString
      )
      downloadInfoStreams.emitEvent(downloadInfo)
    }

    val event = DownloadProgressEvent.Error(
      downloadType = details.type,
      downloadKey = details.key,
      title = details.title,
      errorCode = errorCode,
      isFatal = isFatal
    )
    DownloadProgressBus.emit(event)
    return event
  }

  override fun notifyDownloadStarting() {
    val title = appContext.getString(com.quran.mobile.common.download.R.string.downloading)
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION)

    lastMaximum = -1
    lastProgress = -1
    showNotification(
      title, appContext.getString(R.string.downloading_message),
      DOWNLOADING_NOTIFICATION, true, true
    )
  }

  override fun stopForeground() {
    if (isForeground) {
      ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
      isForeground = false
    } else {
      notificationManager.cancel(DOWNLOADING_NOTIFICATION)
    }
  }

  private fun showNotification(
    titleString: String?,
    statusString: String,
    notificationId: Int,
    isOnGoing: Boolean,
    shouldForeground: Boolean
  ) {
    showNotification(
      titleString, statusString, notificationId,
      isOnGoing, 0, 0, false, shouldForeground
    )
  }

  private fun showNotification(
    titleString: String?,
    statusString: String,
    notificationId: Int,
    isOnGoing: Boolean,
    maximum: Int,
    progress: Int,
    isIndeterminate: Boolean,
    shouldForeground: Boolean
  ) {
    val wantProgress = maximum > 0 && maximum >= progress
    if (lastProgress == progress && lastMaximum == maximum) {
      // don't keep sending repeat notifications
      return
    }
    lastProgress = progress
    lastMaximum = maximum

    val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(notificationColor)
      .setAutoCancel(true)
      .setOngoing(isOnGoing)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentTitle(titleString)
      .setContentText(statusString)
      .setContentIntent(contentIntent)

    if (wantProgress) {
      builder.setProgress(maximum, progress, isIndeterminate)
    }

    try {
      if (shouldForeground && !isForeground) {
        service.startForeground(notificationId, builder.build())
        isForeground = true
      } else if (notificationManager.areNotificationsEnabled()) {
        notificationManager.notify(notificationId, builder.build())
      }
    } catch (se: SecurityException) {
      Timber.e(se)
    } catch (ise: IllegalStateException) {
      Timber.e(ise)
    }
  }

  companion object {
    private const val DOWNLOADING_NOTIFICATION = Constants.NOTIFICATION_ID_DOWNLOADING
    private const val DOWNLOADING_ERROR_NOTIFICATION = Constants.NOTIFICATION_ID_DOWNLOADING_ERROR
    private const val NOTIFICATION_CHANNEL_ID = Constants.DOWNLOAD_CHANNEL
  }
}
