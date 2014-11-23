package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.QuranDataActivity;
import com.quran.labs.androidquran.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;

public class QuranDownloadNotifier {
  // error messages
  public static final int ERROR_DISK_SPACE = 1;
  public static final int ERROR_PERMISSIONS = 2;
  public static final int ERROR_NETWORK = 3;
  public static final int ERROR_INVALID_DOWNLOAD = 4;
  public static final int ERROR_CANCELLED = 5;
  public static final int ERROR_GENERAL = 6;

  // notification ids
  private static final int DOWNLOADING_NOTIFICATION = 1;
  public static final int DOWNLOADING_COMPLETE_NOTIFICATION = 2;
  private static final int DOWNLOADING_ERROR_NOTIFICATION = 3;

  public static class ProgressIntent {
    public static final String INTENT_NAME =
        "com.quran.labs.androidquran.download.ProgressUpdate";
    public static final String NAME = "notificationTitle";
    public static final String DOWNLOAD_KEY = "downloadKey";
    public static final String DOWNLOAD_TYPE = "downloadType";
    public static final String STATE = "state";
    public static final String PROGRESS = "progress";
    public static final String TOTAL_SIZE = "totalSize";
    public static final String DOWNLOADED_SIZE = "downloadedSize";
    public static final String PROCESSED_FILES = "processedFiles";
    public static final String TOTAL_FILES = "totalFiles";
    public static final String ERROR_CODE = "errorCode";

    // states for the intent maybe one of these values
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_PROCESSING = "processing";
    public static final String STATE_SUCCESS = "success";
    public static final String STATE_ERROR = "error";
    public static final String STATE_ERROR_WILL_RETRY = "errorWillRetry";
  }

  public static class NotificationDetails {
    public String title;
    public String key;
    public int type;
    public int currentFile;
    public int totalFiles;
    public boolean sendIndeterminate;

    public NotificationDetails(String title, String key, int type){
      this.key = key;
      this.title = title;
      this.type = type;
      sendIndeterminate = false;
    }

    public void setFileStatus(int current, int total){
      totalFiles = total;
      currentFile = current;
    }
  }

  private Context mAppContext;
  private NotificationManager mNotificationManager;
  private LocalBroadcastManager mBroadcastManager;

  public QuranDownloadNotifier(Context context) {
    mAppContext = context.getApplicationContext();
    mNotificationManager = (NotificationManager) context
        .getSystemService(Context.NOTIFICATION_SERVICE);
    mBroadcastManager = LocalBroadcastManager.getInstance(mAppContext);
  }

  public void resetNotifications() {
    // hide any previous errors, canceled, etc
    mNotificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);
    mNotificationManager.cancel(DOWNLOADING_COMPLETE_NOTIFICATION);
  }

  public Intent notifyProgress(NotificationDetails details,
      long downloadedSize, long totalSize){

    int max = 100;
    int progress = 0;

    // send indeterminate if total size is 0 or less or the notification
    // details says to always send indeterminate
    boolean isIndeterminate = details.sendIndeterminate;
    if (!isIndeterminate && totalSize <= 0){
      isIndeterminate = true;
    }

    if (!isIndeterminate){
      // calculate percentage based on files downloaded, files left,
      // and percentage of this file that is left
      double percent = (1.0 * downloadedSize) / (1.0 * totalSize);
      double percentPerFile = 100 / details.totalFiles;
      progress = (int)((percentPerFile * (details.currentFile - 1)) +
          (percent * percentPerFile));
    }

    showNotification(details.title,
        mAppContext.getString(R.string.downloading_title),
        DOWNLOADING_NOTIFICATION, true, max, progress, isIndeterminate);

    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    progressIntent.putExtra(ProgressIntent.STATE,
        ProgressIntent.STATE_DOWNLOADING);
    if (!isIndeterminate){
      progressIntent.putExtra(ProgressIntent.DOWNLOADED_SIZE,
          downloadedSize);
      progressIntent.putExtra(ProgressIntent.TOTAL_SIZE, totalSize);
      progressIntent.putExtra(ProgressIntent.PROGRESS, progress);
    }
    mBroadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  public Intent notifyDownloadProcessing(
      NotificationDetails details, int done, int total){
    String processingString =
        mAppContext.getString(R.string.download_processing);
    showNotification(details.title, processingString,
        DOWNLOADING_NOTIFICATION, true);

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

    mBroadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  public Intent notifyDownloadSuccessful(NotificationDetails details){
    String successString = mAppContext.getString(R.string.download_successful);
    mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
    mNotificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);
    showNotification(details.title, successString,
        DOWNLOADING_COMPLETE_NOTIFICATION, false);
    return broadcastDownloadSuccessful(details);
  }

  public Intent broadcastDownloadSuccessful(NotificationDetails details){
    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.STATE,
        ProgressIntent.STATE_SUCCESS);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    mBroadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  public Intent notifyError(int errorCode, boolean isFatal,
      NotificationDetails details){
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

    String errorString = mAppContext.getString(errorId);
    mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
    showNotification(details.title, errorString,
        DOWNLOADING_ERROR_NOTIFICATION, false);

    String state = isFatal? ProgressIntent.STATE_ERROR :
        ProgressIntent.STATE_ERROR_WILL_RETRY;

    // send broadcast
    Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
    progressIntent.putExtra(ProgressIntent.NAME, details.title);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
    progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
    progressIntent.putExtra(ProgressIntent.STATE, state);
    progressIntent.putExtra(ProgressIntent.ERROR_CODE, errorCode);
    mBroadcastManager.sendBroadcast(progressIntent);
    return progressIntent;
  }

  private void showNotification(String titleString,
      String statusString, int notificationId, boolean isOnGoing) {
    showNotification(titleString, statusString, notificationId,
        isOnGoing, 0, 0, false);
  }

  private void showNotification(String titleString,
      String statusString, int notificationId, boolean isOnGoing,
      int maximum, int progress, boolean isIndeterminate){

    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(mAppContext);
    builder.setSmallIcon(R.drawable.icon)
        .setAutoCancel(true)
        .setOngoing(isOnGoing)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setContentTitle(titleString);

    String status = statusString;
    if (maximum > 0) {
      builder.setProgress(maximum, progress, isIndeterminate);
      if (!isIndeterminate &&
          Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        status += " (" + progress + "/" + maximum + ")";
      }
    }
    builder.setContentText(status);

    Intent notificationIntent = new Intent(mAppContext,
        QuranDataActivity.class);
    PendingIntent contentIntent = PendingIntent.getActivity(
        mAppContext, 0, notificationIntent, 0);
    builder.setContentIntent(contentIntent);
    mNotificationManager.notify(notificationId, builder.build());
  }
}
