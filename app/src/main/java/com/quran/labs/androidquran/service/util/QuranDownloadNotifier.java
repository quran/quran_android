package com.quran.labs.androidquran.service.util;

import android.content.Intent;
import android.os.Parcelable;

import com.quran.labs.androidquran.data.Constants;
import com.quran.mobile.common.download.DownloadConstants;

public interface QuranDownloadNotifier {
  // error messages
  int ERROR_DISK_SPACE = DownloadConstants.ERROR_DISK_SPACE;
  int ERROR_PERMISSIONS = DownloadConstants.ERROR_PERMISSIONS;
  int ERROR_NETWORK = DownloadConstants.ERROR_NETWORK;
  int ERROR_INVALID_DOWNLOAD = DownloadConstants.ERROR_INVALID_DOWNLOAD;
  int ERROR_CANCELLED = DownloadConstants.ERROR_CANCELLED;
  int ERROR_GENERAL = DownloadConstants.ERROR_GENERAL;

  // notification ids
  int DOWNLOADING_COMPLETE_NOTIFICATION = Constants.NOTIFICATION_ID_DOWNLOADING_COMPLETE;

  class ProgressIntent {
    public static final String INTENT_NAME =
        "com.quran.labs.androidquran.download.ProgressUpdate";
    static final String NAME = "notificationTitle";
    public static final String DOWNLOAD_KEY = "downloadKey";
    public static final String DOWNLOAD_TYPE = "downloadType";
    public static final String STATE = "state";
    public static final String PROGRESS = "progress";
    public static final String TOTAL_SIZE = "totalSize";
    public static final String DOWNLOADED_SIZE = "downloadedSize";
    public static final String PROCESSED_FILES = "processedFiles";
    public static final String TOTAL_FILES = "totalFiles";
    public static final String ERROR_CODE = "errorCode";
    public static final String SURA = "sura";
    public static final String AYAH = "ayah";

    // states for the intent maybe one of these values
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_PROCESSING = "processing";
    public static final String STATE_SUCCESS = "success";
    public static final String STATE_ERROR = "error";
    public static final String STATE_ERROR_WILL_RETRY = "errorWillRetry";
  }

  class NotificationDetails {
    public String title;
    public String key;
    public int type;
    public int currentFile;
    public int totalFiles;
    public int sura;
    public int ayah;
    boolean sendIndeterminate;
    public boolean isGapless;
    public Parcelable metadata;

    public NotificationDetails(String title, String key, int type, Parcelable metadata){
      this.key = key;
      this.title = title;
      this.type = type;
      this.metadata = metadata;
      sendIndeterminate = false;
    }

    public void setFileStatus(int current, int total){
      totalFiles = total;
      currentFile = current;
    }

    public void setIsGapless(boolean isGapless) {
      this.isGapless = isGapless;
    }
  }

  void resetNotifications();
  Intent notifyProgress(NotificationDetails details, long downloadedSize, long totalSize);
  Intent notifyDownloadProcessing(NotificationDetails details, int done, int total);
  Intent notifyDownloadSuccessful(NotificationDetails details);
  void notifyFileDownloaded(NotificationDetails details, String filename);
  Intent broadcastDownloadSuccessful(NotificationDetails details);
  Intent notifyError(int errorCode, boolean isFatal, String filename, NotificationDetails details);
  void notifyDownloadStarting();
  void stopForeground();
}
