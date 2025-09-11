package com.quran.labs.androidquran.service.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.quran.labs.androidquran.QuranDataActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.NotificationChannelUtil;
import com.quran.mobile.common.download.DownloadInfo;
import com.quran.mobile.common.download.DownloadInfoStreams;

import timber.log.Timber;

public class QuranDownloadNotifierImpl implements QuranDownloadNotifier {
  // notification ids
  private static final int DOWNLOADING_NOTIFICATION = Constants.NOTIFICATION_ID_DOWNLOADING;
  private static final int DOWNLOADING_ERROR_NOTIFICATION
      = Constants.NOTIFICATION_ID_DOWNLOADING_ERROR;

  private static final String NOTIFICATION_CHANNEL_ID = Constants.DOWNLOAD_CHANNEL;

  private final Context appContext;
  private final Service service;
  private final NotificationManager notificationManager;
  private final LocalBroadcastManager broadcastManager;
  private final DownloadInfoStreams downloadInfoStreams;
  private final int notificationColor;
  private int lastProgress;
  private int lastMaximum;
  private boolean isForeground;

  public QuranDownloadNotifierImpl(Context context, Service service, DownloadInfoStreams downloadInfoStreams) {
    appContext = context.getApplicationContext();
    notificationManager = (NotificationManager) appContext
        .getSystemService(Context.NOTIFICATION_SERVICE);
    broadcastManager = LocalBroadcastManager.getInstance(appContext);
    notificationColor = ContextCompat.getColor(appContext, R.color.notification_color);
    lastProgress = -1;
    lastMaximum = -1;
    this.service = service;
    this.downloadInfoStreams = downloadInfoStreams;

    final String channelName = appContext.getString(R.string.notification_channel_download);
    NotificationChannelUtil.INSTANCE.setupNotificationChannel(
        notificationManager, NOTIFICATION_CHANNEL_ID, channelName);
  }

  @Override
  public void resetNotifications() {
    // hide any previous errors, canceled, etc
    lastMaximum = -1;
    lastProgress = -1;
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);
    notificationManager.cancel(DOWNLOADING_COMPLETE_NOTIFICATION);
  }

  @Override
  public Intent notifyProgress(NotificationDetails details,
      long downloadedSize, long totalSize){

    int max = 100;
    int progress = 0;

    // send indeterminate if total size is 0 or less or the notification
    // details says to always send indeterminate (never happens right now,
    // so only when the total size is 0 or less).
    boolean isIndeterminate = details.sendIndeterminate;
    if (!isIndeterminate && totalSize <= 0){
      isIndeterminate = true;
    }

    if (!isIndeterminate){
      // calculate percentage based on files downloaded, files left,
      // and percentage of this file that is left
      double percent = (1.0 * downloadedSize) / (1.0 * totalSize);
      if (details.isGapless) {
        progress = (int)(percent * 100);
      } else {
        double percentPerFile = 100.0f / details.totalFiles;
        progress = (int)((percentPerFile * (details.currentFile - 1)) +
                (percent * percentPerFile));
      }

      if (details.sura > 0 && details.ayah > 0) {
        progress = (int) (((float) details.currentFile / (float) details.totalFiles) * 100.0f);
      }
    }

    showNotification(details.title,
        appContext.getString(com.quran.mobile.common.download.R.string.downloading),
        DOWNLOADING_NOTIFICATION, true, max, progress, isIndeterminate, !isForeground);

    final DownloadInfo downloadInfo =
        new DownloadInfo.FileDownloadProgress(
            details.key,
            details.type,
            details.metadata,
            isIndeterminate ? -1 : progress,
            details.sura > 0 ? details.sura : null,
            details.ayah > 0 ? details.ayah : null,
            isIndeterminate ? null : downloadedSize,
            isIndeterminate ? null : totalSize,
            // currentFile is 0-based, but we want to send 1-based since it doesn't make sense
            // to say "downloading file 0/1"
            details.currentFile + 1,
            details.totalFiles
        );
    downloadInfoStreams.emitEvent(downloadInfo);

    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    progressIntent.putExtra(ProgressIntent.STATE,
        ProgressIntent.STATE_DOWNLOADING);
    if (details.sura > 0) {
      progressIntent.putExtra(ProgressIntent.SURA, details.sura);
      progressIntent.putExtra(ProgressIntent.AYAH, details.ayah);
    }

    if (!isIndeterminate){
      progressIntent.putExtra(ProgressIntent.DOWNLOADED_SIZE,
          downloadedSize);
      progressIntent.putExtra(ProgressIntent.TOTAL_SIZE, totalSize);
      progressIntent.putExtra(ProgressIntent.PROGRESS, progress);
    }
    broadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  @Override
  public Intent notifyDownloadProcessing(
      NotificationDetails details, int done, int total){
    String processingString =
        appContext.getString(R.string.download_processing);
    showNotification(details.title, processingString,
        DOWNLOADING_NOTIFICATION, true, !isForeground);

    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    progressIntent.putExtra(ProgressIntent.STATE,
        ProgressIntent.STATE_PROCESSING);

    if (total > 0){
      int progress = (int)((100.0 * done) / (1.0 * total));
      progressIntent.putExtra(ProgressIntent.PROGRESS, progress);
      progressIntent.putExtra(ProgressIntent.PROCESSED_FILES, done);
      progressIntent.putExtra(ProgressIntent.TOTAL_FILES, total);
    }

    broadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  @Override
  public Intent notifyDownloadSuccessful(NotificationDetails details){
    String successString = appContext.getString(R.string.download_successful);
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);

    lastMaximum = -1;
    lastProgress = -1;
    showNotification(details.title, successString,
        DOWNLOADING_COMPLETE_NOTIFICATION, false, false);

    // this emission is once per set of downloads (per batch). notifyFileDownload is per file.
    final DownloadInfo downloadInfo =
        new DownloadInfo.DownloadBatchSuccess(details.key, details.type, details.metadata);
    downloadInfoStreams.emitEvent(downloadInfo);


    return broadcastDownloadSuccessful(details);
  }

  @Override
  public void notifyFileDownloaded(NotificationDetails details, String filename) {
    final DownloadInfo downloadInfo = new DownloadInfo.FileDownloaded(details.key, details.type, details.metadata, filename, details.sura, details.ayah);
    downloadInfoStreams.emitEvent(downloadInfo);
  }

  @Override
  public Intent broadcastDownloadSuccessful(NotificationDetails details){
    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.STATE,
        ProgressIntent.STATE_SUCCESS);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    broadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  @Override
  public Intent notifyError(int errorCode, boolean isFatal, String filename, NotificationDetails details){
    int errorId;
    switch (errorCode){
      case ERROR_DISK_SPACE:
        errorId = R.string.download_error_disk;
        break;
      case ERROR_NETWORK:
        errorId = R.string.download_error_network;
        break;
      case ERROR_PERMISSIONS:
        errorId = R.string.download_error_perms;
        break;
      case ERROR_INVALID_DOWNLOAD:
        errorId = R.string.download_error_invalid_download;
        if (!isFatal){
          errorId = R.string.download_error_invalid_download_retry;
        }
        break;
      case ERROR_CANCELLED:
        errorId = R.string.notification_download_canceled;
        break;
      case ERROR_GENERAL:
      default:
        errorId = R.string.download_error_general;
        break;
    }

    String errorString = appContext.getString(errorId);
    if (isFatal) {
      service.stopForeground(true);
      isForeground = false;
    }

    showNotification(details.title, errorString,
        DOWNLOADING_ERROR_NOTIFICATION, false, false);

    String state = isFatal? ProgressIntent.STATE_ERROR :
        ProgressIntent.STATE_ERROR_WILL_RETRY;

    if (isFatal) {
      final DownloadInfo downloadInfo =
          new DownloadInfo.DownloadBatchError(details.key, details.type, details.metadata, errorCode, errorId, errorString);
      downloadInfoStreams.emitEvent(downloadInfo);
    }

    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    progressIntent.putExtra(ProgressIntent.STATE, state);
    progressIntent.putExtra(ProgressIntent.ERROR_CODE, errorCode);
    broadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  @Override
  public void notifyDownloadStarting(){
    String title = appContext.getString(com.quran.mobile.common.download.R.string.downloading);
    notificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);

    lastMaximum = -1;
    lastProgress = -1;
    showNotification(title, appContext.getString(R.string.downloading_message),
        DOWNLOADING_NOTIFICATION, true, true);
  }

  @Override
  public void stopForeground() {
    if (isForeground) {
      service.stopForeground(true);
      isForeground = false;
    } else {
      notificationManager.cancel(DOWNLOADING_NOTIFICATION);
    }
  }

  private void showNotification(String titleString,
      String statusString, int notificationId, boolean isOnGoing, boolean shouldForeground) {
    showNotification(titleString, statusString, notificationId,
        isOnGoing, 0, 0, false, shouldForeground);
  }

  private void showNotification(String titleString,
      String statusString, int notificationId, boolean isOnGoing,
      int maximum, int progress, boolean isIndeterminate, boolean shouldForeground) {
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID);
    builder.setSmallIcon(R.drawable.ic_notification)
        .setColor(notificationColor)
        .setAutoCancel(true)
        .setOngoing(isOnGoing)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentTitle(titleString)
        .setContentText(statusString);

    boolean wantProgress = maximum > 0 && maximum >= progress;
    if (lastProgress == progress && lastMaximum == maximum) {
      // don't keep sending repeat notifications
      return;
    }
    lastProgress = progress;
    lastMaximum = maximum;

    if (wantProgress) {
      builder.setProgress(maximum, progress, isIndeterminate);
    }

    Intent notificationIntent = new Intent(appContext, QuranDataActivity.class);
    PendingIntent contentIntent =
        PendingIntent.getActivity(appContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
    builder.setContentIntent(contentIntent);

    try {
      if (shouldForeground && !isForeground) {
        service.startForeground(notificationId, builder.build());
        isForeground = true;
       } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || notificationManager.areNotificationsEnabled()) {
        notificationManager.notify(notificationId, builder.build());
      }
    } catch (SecurityException | IllegalStateException se) {
      Timber.e(se);
    }
  }
}
