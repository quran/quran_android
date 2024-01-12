package com.quran.labs.androidquran.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.StatFs;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.quran.data.core.QuranInfo;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.extension.CloseableExtensionKt;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.UrlUtil;
import com.quran.labs.androidquran.util.ZipUtils;
import com.quran.mobile.common.download.DownloadInfoStreams;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import timber.log.Timber;

public class QuranDownloadService extends Service implements
    ZipUtils.ZipListener<NotificationDetails> {

  public static final String TAG = "QuranDownloadService";
  public static final String DEFAULT_TAG = "QuranDownload";

  // intent actions
  public static final String ACTION_DOWNLOAD_URL =
      "com.quran.labs.androidquran.DOWNLOAD_URL";
  public static final String ACTION_CANCEL_DOWNLOADS =
      "com.quran.labs.androidquran.CANCEL_DOWNLOADS";
  public static final String ACTION_RECONNECT =
      "com.quran.labs.androidquran.RECONNECT";

  // extras
  public static final String EXTRA_URL = "url";
  public static final String EXTRA_DESTINATION = "destination";
  public static final String EXTRA_NOTIFICATION_NAME = "notificationName";
  public static final String EXTRA_DOWNLOAD_KEY = "downloadKey";
  public static final String EXTRA_REPEAT_LAST_ERROR = "repeatLastError";
  public static final String EXTRA_DOWNLOAD_TYPE = "downloadType";
  public static final String EXTRA_OUTPUT_FILE_NAME = "outputFileName";
  public static final String EXTRA_METADATA= "metadata";

  // extras for range downloads
  public static final String EXTRA_START_VERSE = "startVerse";
  public static final String EXTRA_END_VERSE = "endVerse";
  public static final String EXTRA_IS_GAPLESS = "isGapless";

  // download types (also handler message types)
  public static final int DOWNLOAD_TYPE_UNDEF = 0;
  public static final int DOWNLOAD_TYPE_PAGES = 1;
  public static final int DOWNLOAD_TYPE_AUDIO = 2;
  public static final int DOWNLOAD_TYPE_TRANSLATION = 3;
  public static final int DOWNLOAD_TYPE_ARABIC_SEARCH_DB = 4;

  // continuation of handler message types
  public static final int NO_OP = 9;

  // error prefs
  public static final String PREF_LAST_DOWNLOAD_ERROR = "lastDownloadError";
  public static final String PREF_LAST_DOWNLOAD_ITEM = "lastDownloadItem";

  public static final int BUFFER_SIZE = 4096 * 2;
  private static final int WAIT_TIME = 15 * 1000;
  private static final int RETRY_COUNT = 3;
  private static final String PARTIAL_EXT = ".part";

  // download method return values
  private static final int DOWNLOAD_SUCCESS = 0;

  private Looper serviceLooper;
  private ServiceHandler serviceHandler;
  private QuranDownloadNotifier notifier;

  private static boolean fallbackByDefault = false;

  // written from ui thread and read by download thread
  private volatile boolean isDownloadCanceled;
  private LocalBroadcastManager broadcastManager;
  private QuranSettings quranSettings;
  private WifiLock wifiLock;

  private Intent lastSentIntent = null;
  private Map<String, Boolean> successfulZippedDownloads = null;
  private Map<String, Intent> recentlyFailedDownloads = null;

  // incremented from ui thread and decremented by download thread
  private final AtomicInteger currentOperations = new AtomicInteger(0);

  @Inject QuranInfo quranInfo;
  @Inject OkHttpClient okHttpClient;
  @Inject UrlUtil urlUtil;
  @Inject DownloadInfoStreams downloadInfoStreams;

  private final class ServiceHandler extends Handler {

    ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.obj != null) {
        onHandleIntent((Intent) msg.obj);
        if (0 == currentOperations.decrementAndGet()) {
          notifier.stopForeground();
        }
      }
      stopSelf(msg.arg1);
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    HandlerThread thread = new HandlerThread(TAG);
    thread.start();

    Context appContext = getApplicationContext();
    wifiLock = ((WifiManager) appContext.getSystemService(Context.WIFI_SERVICE))
        .createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadLock");

    serviceLooper = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);
    isDownloadCanceled = false;
    successfulZippedDownloads = new HashMap<>();
    recentlyFailedDownloads = new HashMap<>();
    quranSettings = QuranSettings.getInstance(this);

    ((QuranApplication) getApplication()).getApplicationComponent().inject(this);
    broadcastManager = LocalBroadcastManager.getInstance(appContext);
    notifier = new QuranDownloadNotifier(this, this, downloadInfoStreams);
  }

  private void handleOnStartCommand(Intent intent, int startId) {
    if (intent != null) {
      if (ACTION_CANCEL_DOWNLOADS.equals(intent.getAction())) {
        serviceHandler.removeCallbacksAndMessages(null);
        isDownloadCanceled = true;
        sendNoOpMessage(startId);
      } else if (ACTION_RECONNECT.equals(intent.getAction())) {
        int type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE,
            DOWNLOAD_TYPE_UNDEF);
        Intent currentLast = lastSentIntent;
        int lastType = currentLast == null ? -1 :
            currentLast.getIntExtra(EXTRA_DOWNLOAD_TYPE, DOWNLOAD_TYPE_UNDEF);

        if (type == lastType) {
          if (currentLast != null) {
            broadcastManager.sendBroadcast(currentLast);
          }
        } else if (serviceHandler.hasMessages(type)) {
          Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
          progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, type);
          progressIntent.putExtra(ProgressIntent.STATE,
              ProgressIntent.STATE_DOWNLOADING);
          broadcastManager.sendBroadcast(progressIntent);
        }
        sendNoOpMessage(startId);
      } else {
        // if we are currently downloading, resend the last broadcast
        // and don't queue anything
        String download = intent.getStringExtra(EXTRA_DOWNLOAD_KEY);
        Intent currentLast = lastSentIntent;
        String currentDownload = currentLast == null ? null :
            currentLast.getStringExtra(ProgressIntent.DOWNLOAD_KEY);
        if (download != null && download.equals(currentDownload)) {
          Timber.d("resending last broadcast...");
          broadcastManager.sendBroadcast(currentLast);

          String state = currentLast.getStringExtra(ProgressIntent.STATE);
          if (!ProgressIntent.STATE_SUCCESS.equals(state) &&
              !ProgressIntent.STATE_ERROR.equals(state)) {
            // re-queue fatal errors and success cases again just in case
            // of a race condition in which we miss the error pref and
            // miss the success/failure notification and this re-play
            sendNoOpMessage(startId);
            Timber.d("leaving...");
            return;
          }
        }

        int what = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, DOWNLOAD_TYPE_UNDEF);
        currentOperations.incrementAndGet();
        // put the message in the queue
        Message msg = serviceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        msg.what = what;
        serviceHandler.sendMessage(msg);
      }
    }
  }

  /**
   * send a no-op message to the handler to ensure
   * that the service isn't left running.
   *
   * @param id the start id
   */
  private void sendNoOpMessage(int id) {
    Message msg = serviceHandler.obtainMessage();
    msg.arg1 = id;
    msg.obj = null;
    msg.what = NO_OP;
    serviceHandler.sendMessage(msg);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent != null) {
      // if it's a download, it wants to be a foreground service.
      // quickly start as foreground before actually enqueueing the request.
      if (ACTION_DOWNLOAD_URL.equals(intent.getAction())) {
        notifier.notifyDownloadStarting();
      }

      handleOnStartCommand(intent, startId);
    }
    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    if (wifiLock.isHeld()) {
      wifiLock.release();
    }
    serviceLooper.quit();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void onHandleIntent(Intent intent) {
    if (ACTION_DOWNLOAD_URL.equals(intent.getAction())) {
      String url = intent.getStringExtra(EXTRA_URL);
      String key = intent.getStringExtra(EXTRA_DOWNLOAD_KEY);
      int type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, 0);
      String notificationTitle =
          intent.getStringExtra(EXTRA_NOTIFICATION_NAME);
      Parcelable metadata = intent.getParcelableExtra(EXTRA_METADATA);

      NotificationDetails details =
          new NotificationDetails(notificationTitle, key, type, metadata);
      // check if already downloaded, and if so, send broadcast
      boolean isZipFile = url.endsWith(".zip");
      if (isZipFile && successfulZippedDownloads.containsKey(url)) {
        lastSentIntent = notifier.broadcastDownloadSuccessful(details);
        return;
      } else if (recentlyFailedDownloads.containsKey(url)) {
        // if recently failed and we want to repeat the last error...
        if (intent.getBooleanExtra(EXTRA_REPEAT_LAST_ERROR, false)) {
          Intent failedIntent = recentlyFailedDownloads.get(url);
          if (failedIntent != null) {
            // re-broadcast and leave - just in case of race condition
            broadcastManager.sendBroadcast(failedIntent);
            return;
          }
        }
        // otherwise, remove the fact it was an error and retry
        else {
          recentlyFailedDownloads.remove(url);
        }
      }
      notifier.resetNotifications();

      // get the start/end ayah info if it's a ranged download
      SuraAyah startAyah = (SuraAyah) intent.getSerializableExtra(EXTRA_START_VERSE);
      SuraAyah endAyah = (SuraAyah) intent.getSerializableExtra(EXTRA_END_VERSE);
      boolean isGapless = intent.getBooleanExtra(EXTRA_IS_GAPLESS, false);

      String outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME);
      if (outputFile == null) {
        outputFile = getFilenameFromUrl(url);
      }
      String destination = intent.getStringExtra(EXTRA_DESTINATION);
      lastSentIntent = null;

      if (destination == null) {
        return;
      }

      boolean result;
      if (startAyah != null && endAyah != null) {
        result = downloadRange(url, destination, startAyah, endAyah, isGapless, details);
      } else {
        result = download(url, destination, outputFile, details);
      }
      if (result && isZipFile) {
        successfulZippedDownloads.put(url, true);
      } else if (!result) {
        recentlyFailedDownloads.put(url, lastSentIntent);
      }
      lastSentIntent = null;
    }
  }

  private boolean download(String urlString, String destination,
      String outputFile,
      NotificationDetails details) {
    // make the directory if it doesn't exist
    new File(destination).mkdirs();
    Timber.d("making directory %s", destination);

    details.setFileStatus(1, 1);

    // notify download starting
    lastSentIntent = notifier.notifyProgress(details, 0, 0);
    boolean result = downloadFileWrapper(urlString, destination, outputFile, details);
    if (result) {
      lastSentIntent = notifier.notifyDownloadSuccessful(details);
    }
    notifier.notifyFileDownloaded(details, outputFile);
    return result;
  }

  private boolean downloadRange(String urlString,
                                String destination,
                                SuraAyah startVerse,
                                SuraAyah endVerse,
                                boolean isGapless,
      NotificationDetails details) {
    details.setIsGapless(isGapless);
    new File(destination).mkdirs();

    int totalAyahs = 0;
    int startSura = startVerse.sura;
    int startAyah = startVerse.ayah;
    int endSura = endVerse.sura;
    int endAyah = endVerse.ayah;

    if (isGapless) {
      totalAyahs = endSura - startSura + 1;
      if (endAyah == 0) {
        totalAyahs--;
      }
    } else {
      if (startSura == endSura) {
        totalAyahs = endAyah - startAyah + 1;
      } else {
        // add the number ayahs from suras in between start and end
        for (int i = startSura + 1; i < endSura; i++) {
          totalAyahs += quranInfo.getNumberOfAyahs(i);
        }

        // add the number of ayahs from the start sura
        totalAyahs += quranInfo.getNumberOfAyahs(startSura) - startAyah + 1;

        // add the number of ayahs from the last sura
        totalAyahs += endAyah;
      }
    }

    Timber.d("downloadRange for %d between %d:%d to %d:%d, gaplessFlag: %s",
        totalAyahs, startSura, startAyah, endSura, endAyah, isGapless ? "true" : "false");

    details.setFileStatus(1, totalAyahs);
    lastSentIntent = notifier.notifyProgress(details, 0, 0);

    // extension and filename template don't change
    final String singleFileName =
        QuranDownloadService.getFilenameFromUrl(urlString);
    final int extLocation = singleFileName.lastIndexOf(".");
    final String extension = singleFileName.substring(extLocation);

    boolean result;
    for (int i = startSura; i <= endSura; i++) {
      int lastAyah = quranInfo.getNumberOfAyahs(i);
      if (i == endSura) {
        lastAyah = endAyah;
      }
      int firstAyah = 1;
      if (i == startSura) {
        firstAyah = startAyah;
      }

      details.sura = i;
      if (isGapless) {
        if (i == endSura && endAyah == 0) {
          continue;
        }
        String destDir = destination + File.separator;
        String url = String.format(Locale.US, urlString, i);
        Timber.d("gapless asking to download %s to %s", url, destDir);
        final String filename = QuranDownloadService.getFilenameFromUrl(url);
        if (!new File(destDir, filename).exists()) {
          result = downloadFileWrapper(url, destDir, filename, details);
          if (!result) {
            return false;
          }
          notifier.notifyFileDownloaded(details, filename);
        }
        details.currentFile++;
        continue;
      }

      // same destination directory for ayahs within the same sura
      String destDir = destination + File.separator + i + File.separator;
      new File(destDir).mkdirs();

      for (int j = firstAyah; j <= lastAyah; j++) {
        details.ayah = j;
        String url = String.format(Locale.US, urlString, i, j);
        String destFile = j + extension;
        if (!new File(destDir, destFile).exists()) {
          result = downloadFileWrapper(url, destDir, destFile, details);
          if (!result) {
            return false;
          }
          notifier.notifyFileDownloaded(details, destFile);
        }

        details.currentFile++;
      }
    }

    if (!isGapless) {
      // attempt to download basmallah if it doesn't exist
      String destDir = destination + File.separator + 1 + File.separator;
      new File(destDir).mkdirs();
      File basmallah = new File(destDir, "1" + extension);
      if (!basmallah.exists()) {
        Timber.d("basmallah doesn't exist, downloading...");
        String url = String.format(Locale.US, urlString, 1, 1);
        String destFile = 1 + extension;
        result = downloadFileWrapper(url, destDir, destFile, details);
        if (!result) {
          return false;
        }
      }
    }

    lastSentIntent = notifier.notifyDownloadSuccessful(details);

    return true;
  }

  private boolean downloadFileWrapper(String urlString, String destination,
      String outputFile, NotificationDetails details) {
    boolean previouslyCorrupted = false;

    int res = DOWNLOAD_SUCCESS;
    for (int i = 0; i < RETRY_COUNT; i++) {
      if (isDownloadCanceled) {
        break;
      }

      String url = urlString;
      if (fallbackByDefault || i > 0) {
        url = urlUtil.fallbackUrl(url);

        // want to wait before retrying again
        try {
          Thread.sleep(WAIT_TIME);
        } catch (InterruptedException exception) {
          // no op
        }
        notifier.resetNotifications();
      }

      wifiLock.acquire();
      res = startDownload(url, destination, outputFile, details);
      if (wifiLock.isHeld()) {
        wifiLock.release();
      }

      if (res == DOWNLOAD_SUCCESS) {
        fallbackByDefault = (i > 0);
        return true;
      } else if (res == QuranDownloadNotifier.ERROR_DISK_SPACE ||
          res == QuranDownloadNotifier.ERROR_PERMISSIONS) {
        // critical errors
        notifier.notifyError(res, true, outputFile, details);
        return false;
      } else if (res == QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD) {
        // corrupted download
        if (!previouslyCorrupted) {
          // give one more chance if this is the first time
          // this file was corrupted
          i--;
          previouslyCorrupted = true;
        }

        if (i + 1 < RETRY_COUNT) {
          notifyError(res, false, outputFile, details);
        }
      }
    }

    if (isDownloadCanceled) {
      res = QuranDownloadNotifier.ERROR_CANCELLED;
    }
    notifyError(res, true, outputFile, details);
    return false;
  }

  private int startDownload(String url, String path,
      String filename, NotificationDetails notificationInfo) {
    if (!QuranUtils.haveInternet(this)) {
      notifyError(QuranDownloadNotifier.ERROR_NETWORK, false, filename, notificationInfo);
      return QuranDownloadNotifier.ERROR_NETWORK;
    }
    final int result = downloadUrl(url, path, filename, notificationInfo);
    if (result == DOWNLOAD_SUCCESS) {
      if (filename.endsWith("zip")) {
        final File actualFile = new File(path, filename);
        if (!ZipUtils.unzipFile(actualFile.getAbsolutePath(),
            path, notificationInfo, this)) {
          return !actualFile.delete() ?
              QuranDownloadNotifier.ERROR_PERMISSIONS :
              QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD;
        } else {
          actualFile.delete();
        }
      }
    }
    return result;
  }

  private int downloadUrl(String url, String path, String filename,
      NotificationDetails notificationInfo) {
    Timber.d("downloading %s", url);
    final Request.Builder builder = new Request.Builder()
        .url(url).tag(DEFAULT_TAG);
    final File partialFile = new File(path, filename + PARTIAL_EXT);
    final File actualFile = new File(path, filename);

    Timber.d("downloadUrl: trying to download - file %s",
        actualFile.exists() ? "exists" : "doesn't exist");
    long downloadedAmount = 0;
    if (partialFile.exists()) {
      downloadedAmount = partialFile.length();
      Timber.d("downloadUrl: partialFile exists, length: %d", downloadedAmount);
      builder.addHeader("Range", "bytes=" + downloadedAmount + "-");
    }
    final boolean isZip = filename.endsWith(".zip");

    Call call = null;
    BufferedSource source = null;
    try {
      final Request request = builder.build();
      call = okHttpClient.newCall(request);
      final Response response = call.execute();
      if (response.isSuccessful()) {
        Timber.d("successful response: " + response.code() + " - " + downloadedAmount);
        final BufferedSink sink = Okio.buffer(Okio.appendingSink(partialFile));
        final ResponseBody body = response.body();
        source = body.source();
        final long size = body.contentLength() + downloadedAmount;

        if (!isSpaceAvailable(size + (isZip ? downloadedAmount + size : 0))) {
          return QuranDownloadNotifier.ERROR_DISK_SPACE;
        } else if (actualFile.exists()) {
          if (actualFile.length() == (size + downloadedAmount)) {
            // we already downloaded, why are we re-downloading?
            return DOWNLOAD_SUCCESS;
          } else if (!actualFile.delete()) {
            return QuranDownloadNotifier.ERROR_PERMISSIONS;
          }
        }

        long read;
        int loops = 0;
        long totalRead = downloadedAmount;

        while (!isDownloadCanceled && !source.exhausted() &&
            ((read = source.read(sink.getBuffer(), BUFFER_SIZE)) > 0)) {
          totalRead += read;
          if (loops++ % 5 == 0) {
            lastSentIntent = notifier.notifyProgress(notificationInfo, totalRead, size);
          }
          sink.flush();
        }
        CloseableExtensionKt.closeQuietly(sink);

        if (isDownloadCanceled) {
          return QuranDownloadNotifier.ERROR_CANCELLED;
        } else if (!partialFile.renameTo(actualFile)) {
          return notifyError(QuranDownloadNotifier.ERROR_PERMISSIONS, true, filename, notificationInfo);
        }
        return DOWNLOAD_SUCCESS;
      } else if (response.code() == 416) {
        if (!partialFile.delete()) {
          return QuranDownloadNotifier.ERROR_PERMISSIONS;
        }
        return downloadUrl(url, path, filename, notificationInfo);
      } else {
        Timber.e(new Exception("Unable to download file - code: " + response.code()));
      }
    } catch (IOException exception) {
      Timber.d(exception, "Failed to download file");
    } catch (SecurityException se) {
      Timber.e(se, "Security exception while downloading file");
    } finally {
      CloseableExtensionKt.closeQuietly(source);
    }

    return (call != null && call.isCanceled()) ?
        QuranDownloadNotifier.ERROR_CANCELLED :
        notifyError(QuranDownloadNotifier.ERROR_NETWORK,
            false, filename, notificationInfo);
  }

  @Override
  public void onProcessingProgress(NotificationDetails details, int processed, int total) {
    if (details.totalFiles == 1) {
      lastSentIntent = notifier.notifyDownloadProcessing(
          details, processed, total);
    }
  }

  private int notifyError(int errorCode, boolean isFatal, String filename, NotificationDetails details) {
    lastSentIntent = notifier.notifyError(errorCode, isFatal, filename, details);

    if (isFatal) {
      // write last error in prefs
      quranSettings.setLastDownloadError(details.key, errorCode);
    }
    return errorCode;
  }

  // TODO: this is actually a bug - we may not be using /sdcard...
  // we may not have permission, etc - some devices get a IllegalArgumentException
  // because the path passed is /storage/emulated/0, for example.
  private boolean isSpaceAvailable(long spaceNeeded) {
    try {
      StatFs fsStats = new StatFs(
          Environment.getExternalStorageDirectory().getAbsolutePath());
      long availableSpace = fsStats.getAvailableBlocksLong() * fsStats.getBlockSizeLong();

      return availableSpace > spaceNeeded;
    } catch (Exception e) {
      Timber.e(e);
      return true;
    }
  }

  private static String getFilenameFromUrl(String url) {
    int slashIndex = url.lastIndexOf("/");
    if (slashIndex != -1) {
      return url.substring(slashIndex + 1);
    }

    // should never happen
    return url;
  }
}
