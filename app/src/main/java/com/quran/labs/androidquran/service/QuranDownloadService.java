package com.quran.labs.androidquran.service;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.ProgressIntent;
import com.quran.labs.androidquran.task.TranslationListTask;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.util.ZipUtils;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

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
   public static final String ACTION_CHECK_TRANSLATIONS =
         "com.quran.labs.androidquran.CHECK_TRANSLATIONS";
   
   // extras
   public static final String EXTRA_URL = "url";
   public static final String EXTRA_DESTINATION = "destination";
   public static final String EXTRA_NOTIFICATION_NAME = "notificationName";
   public static final String EXTRA_DOWNLOAD_KEY = "downloadKey";
   public static final String EXTRA_REPEAT_LAST_ERROR = "repeatLastError";
   public static final String EXTRA_DOWNLOAD_TYPE = "downloadType";
   public static final String EXTRA_OUTPUT_FILE_NAME = "outputFileName";

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
   public static final int TRANSLATIONS_UPDATE = 10;
   
   // error prefs
   public static final String PREF_LAST_DOWNLOAD_ERROR = "lastDownloadError";
   public static final String PREF_LAST_DOWNLOAD_ITEM = "lastDownloadItem";

   public static final int BUFFER_SIZE = 4096 * 2;
   private static final int WAIT_TIME = 15 * 1000;
   private static final int RETRY_COUNT = 3;
   private static final String PARTIAL_EXT = ".part";
   
   // download method return values
   private static final int DOWNLOAD_SUCCESS = 0;

   private Looper mServiceLooper;
   private OkHttpClient mOkHttpClient;
   private ServiceHandler mServiceHandler;
   private QuranDownloadNotifier mNotifier;
   
   // written from ui thread and read by download thread
   private volatile boolean mIsDownloadCanceled;
   private LocalBroadcastManager mBroadcastManager;
   private SharedPreferences mSharedPreferences;
   private WifiLock mWifiLock;
   
   private Intent mLastSentIntent = null;
   private Map<String, Boolean> mSuccessfulZippedDownloads = null;
   private Map<String, Intent> mRecentlyFailedDownloads = null;
   
   private final class ServiceHandler extends Handler {
      public ServiceHandler(Looper looper){
         super(looper);
      }
      
      @Override
      public void handleMessage(Message msg){
         if (msg.what == TRANSLATIONS_UPDATE){
            updateTranslations();
         } else if (msg.obj != null){
            onHandleIntent((Intent)msg.obj);
         }
         stopSelf(msg.arg1);
      }
   }
   
   @Override
   public void onCreate() {
      super.onCreate();
      HandlerThread thread = new HandlerThread(TAG);
      thread.start();

      mNotifier = new QuranDownloadNotifier(this);
      mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadLock");
      
      mServiceLooper = thread.getLooper();
      mServiceHandler = new ServiceHandler(mServiceLooper);
      mIsDownloadCanceled = false;
      mSuccessfulZippedDownloads = new HashMap<>();
      mRecentlyFailedDownloads = new HashMap<>();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());

      mBroadcastManager = LocalBroadcastManager.getInstance(
            getApplicationContext());
      mOkHttpClient = new OkHttpClient();
   }
   
   @Override
   public void onStart(Intent intent, int startId){
      if (intent != null){
         if (ACTION_CANCEL_DOWNLOADS.equals(intent.getAction())){
            mServiceHandler.removeCallbacksAndMessages(null);
            mIsDownloadCanceled = true;
            sendNoOpMessage(startId);
         }
         else if (ACTION_RECONNECT.equals(intent.getAction())){
            int type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE,
                    DOWNLOAD_TYPE_UNDEF);
            Intent currentLast = mLastSentIntent;
            int lastType = currentLast == null? -1 :
                    currentLast.getIntExtra(EXTRA_DOWNLOAD_TYPE,
                            DOWNLOAD_TYPE_UNDEF);

            if (type == lastType){
               mBroadcastManager.sendBroadcast(currentLast);
            }
            else if (mServiceHandler.hasMessages(type)){
               Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
               progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, type);
               progressIntent.putExtra(ProgressIntent.STATE,
                       ProgressIntent.STATE_DOWNLOADING);
               mBroadcastManager.sendBroadcast(progressIntent);
            }
            sendNoOpMessage(startId);
         }
         else if (ACTION_CHECK_TRANSLATIONS.equals(intent.getAction())){
            mServiceHandler.sendEmptyMessage(TRANSLATIONS_UPDATE);
         }
         else {
            // if we are currently downloading, resend the last broadcast
            // and don't queue anything
            String download = intent.getStringExtra(EXTRA_DOWNLOAD_KEY);
            Intent currentLast = mLastSentIntent;
            String currentDownload = currentLast == null? null :
               currentLast.getStringExtra(ProgressIntent.DOWNLOAD_KEY);
            if (download != null && currentDownload != null &&
                  download.equals(currentDownload)){
               android.util.Log.d(TAG, "resending last broadcast...");
               mBroadcastManager.sendBroadcast(currentLast);
               
               String state = currentLast.getStringExtra(ProgressIntent.STATE);
               if (!ProgressIntent.STATE_SUCCESS.equals(state) &&
                   !ProgressIntent.STATE_ERROR.equals(state)){
                  // re-queue fatal errors and success cases again just in case
                  // of a race condition in which we miss the error pref and
                  // miss the success/failure notification and this re-play
                  sendNoOpMessage(startId);
                  android.util.Log.d(TAG, "leaving...");
                  return;
               }
            }

            int what = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE,
                    DOWNLOAD_TYPE_UNDEF);

            // put the message in the queue
            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.obj = intent;
            msg.what = what;
            mServiceHandler.sendMessage(msg);
         }
      }
   }

   /**
    * send a no-op message to the handler to ensure
    * that the service isn't left running.
    * @param id the start id
    */
   private void sendNoOpMessage(int id){
      Message msg = mServiceHandler.obtainMessage();
      msg.arg1 = id;
      msg.obj = null;
      msg.what = NO_OP;
      mServiceHandler.sendMessage(msg);
   }
   
   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      onStart(intent, startId);
      return START_NOT_STICKY;
   }
   
   @Override
   public void onDestroy() {
      if (mWifiLock.isHeld()){ mWifiLock.release(); }
      mServiceLooper.quit();
   }
   
   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }

   private void updateTranslations(){
      List<TranslationItem> items =
              TranslationListTask.downloadTranslations(this, false, TAG);
      if (items == null){ return; }

      boolean needsUpgrade = false;
      for (TranslationItem item : items){
         if (item.exists && item.localVersion != null &&
                 item.latestVersion > 0 &&
                 item.latestVersion > item.localVersion){
            needsUpgrade = true;
            break;
         }
      }

      Log.d(TAG, "done checking translations - " +
              (needsUpgrade? "" : "no ") + "upgrade needed");
      mSharedPreferences.edit().putBoolean(
              Constants.PREF_HAVE_UPDATED_TRANSLATIONS, needsUpgrade).commit();
   }
   
   private void onHandleIntent(Intent intent){
      if (ACTION_DOWNLOAD_URL.equals(intent.getAction())){
         String url = intent.getStringExtra(EXTRA_URL);
         String key = intent.getStringExtra(EXTRA_DOWNLOAD_KEY);
         int type = intent.getIntExtra(EXTRA_DOWNLOAD_TYPE, 0);
         String notificationTitle =
               intent.getStringExtra(EXTRA_NOTIFICATION_NAME);

         NotificationDetails details =
                 new NotificationDetails(notificationTitle, key, type);
         // check if already downloaded, and if so, send broadcast
         boolean isZipFile = url.endsWith(".zip");
         if (isZipFile && mSuccessfulZippedDownloads.containsKey(url)){
            mLastSentIntent = mNotifier.broadcastDownloadSuccessful(details);
            return;
         }
         else if (mRecentlyFailedDownloads.containsKey(url)){
            // if recently failed and we want to repeat the last error...
            if (intent.getBooleanExtra(EXTRA_REPEAT_LAST_ERROR, false)){
               Intent failedIntent = mRecentlyFailedDownloads.get(url);
               if (failedIntent != null){
                  // re-broadcast and leave - just in case of race condition
                  mBroadcastManager.sendBroadcast(failedIntent);
                  return;
               }
            }
            // otherwise, remove the fact it was an error and retry
            else { mRecentlyFailedDownloads.remove(url); }
         }
         mNotifier.resetNotifications();

         // get the start/end ayah info if it's a ranged download
         Serializable startAyah = intent.getSerializableExtra(EXTRA_START_VERSE);
         Serializable endAyah = intent.getSerializableExtra(EXTRA_END_VERSE);
         boolean isGapless = intent.getBooleanExtra(EXTRA_IS_GAPLESS, false);

         String outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME);
         if (outputFile == null) {
            outputFile = getFilenameFromUrl(url);
         }
         String destination = intent.getStringExtra(EXTRA_DESTINATION);
         mLastSentIntent = null;

         if (destination == null){ return; }

         boolean result;
         if (startAyah != null && endAyah != null){
            if (startAyah instanceof QuranAyah &&
                    endAyah instanceof QuranAyah){
               result = downloadRange(url, destination,
                       (QuranAyah)startAyah,
                       (QuranAyah)endAyah, isGapless, details);
            }
            else { return; }
         }
         else {
            result = download(url, destination, outputFile, details);
         }
         if (result && isZipFile){
            mSuccessfulZippedDownloads.put(url, true);
         }
         else if (!result){
            mRecentlyFailedDownloads.put(url, mLastSentIntent);
         }
         mLastSentIntent = null;
      }
   }
   
   private boolean download(String urlString, String destination,
                            String outputFile,
                            NotificationDetails details){
      // make the directory if it doesn't exist
      new File(destination).mkdirs();
      android.util.Log.d(TAG, "making directory: " + destination);

      details.setFileStatus(1, 1);
      
      // notify download starting
      mLastSentIntent = mNotifier.notifyProgress(details, 0, 0);
      boolean result = downloadFileWrapper(urlString, destination,
              outputFile, details);
      if (result){
         mLastSentIntent = mNotifier.notifyDownloadSuccessful(details);
      }
      return result;
   }

   private boolean downloadRange(String urlString, String destination,
                                 QuranAyah startVerse,
                                 QuranAyah endVerse, boolean isGapless,
                                 NotificationDetails details){
      new File(destination).mkdirs();

      int totalAyahs = 0;
      int startSura = startVerse.getSura();
      int startAyah = startVerse.getAyah();
      int endSura = endVerse.getSura();
      int endAyah = endVerse.getAyah();

      if (isGapless){
         totalAyahs = endSura - startSura + 1;
         if (endAyah == 0){ totalAyahs--; }
      }
      else {
         if (startSura == endSura){
            totalAyahs = endAyah - startAyah + 1;
         }
         else {
            // add the number ayahs from suras in between start and end
            for (int i = startSura + 1; i < endSura; i++){
               totalAyahs += QuranInfo.getNumAyahs(i);
            }

            // add the number of ayahs from the start sura
            totalAyahs += QuranInfo.getNumAyahs(startSura) - startAyah + 1;

            // add the number of ayahs from the last sura
            totalAyahs += endAyah;
         }
      }

      Log.d(TAG, "downloadRange for " + totalAyahs + " between " +
              startSura + ":" + startAyah + " to " + endSura + ":" +
              endAyah + ", gaplessFlag: " + isGapless);

      details.setFileStatus(1, totalAyahs);
      mLastSentIntent = mNotifier.notifyProgress(details, 0, 0);

      // extension and filename template don't change
      final String singleFileName =
          QuranDownloadService.getFilenameFromUrl(urlString);
      final int extLocation = singleFileName.lastIndexOf(".");
      final String extension = singleFileName.substring(extLocation);

      boolean result;
      for (int i = startSura; i <= endSura; i++){
         int lastAyah = QuranInfo.getNumAyahs(i);
         if (i == endSura){ lastAyah = endAyah; }
         int firstAyah = 1;
         if (i == startSura){ firstAyah = startAyah; }

         if (isGapless){
            if (i == endSura && endAyah == 0){ continue; }
            String destDir = destination + File.separator;
            String url = String.format(Locale.US,  urlString, i);
            Log.d(TAG, "gapless asking to download " + url + " to " + destDir);
            final String filename = QuranDownloadService.getFilenameFromUrl(url);
            result = downloadFileWrapper(url, destDir, filename, details);
            if (!result){ return false; }
            details.currentFile++;
            continue;
         }

         // same destination directory for ayahs within the same sura
         String destDir = destination + File.separator + i + File.separator;
         new File(destDir).mkdirs();

         for (int j = firstAyah; j <= lastAyah; j++){
            String url = String.format(Locale.US, urlString, i, j);
            String destFile = j + extension;
            result = downloadFileWrapper(url, destDir, destFile, details);
            if (!result){ return false; }

            details.currentFile++;
         }
      }

      if (!isGapless){
         // attempt to download basmallah if it doesn't exist
         String destDir = destination + File.separator + 1 + File.separator;
         new File(destDir).mkdirs();
         File basmallah = new File(destDir, "1" + extension);
         if (!basmallah.exists()){
            Log.d(TAG, "basmallah doesn't exist, downloading...");
            String url = String.format(Locale.US, urlString, 1, 1);
            String destFile = 1 + extension;
            result = downloadFileWrapper(url, destDir, destFile, details);
            if (!result){ return false; }
         }
      }

      mLastSentIntent = mNotifier.notifyDownloadSuccessful(details);

      return true;
   }

   private boolean downloadFileWrapper(String urlString, String destination,
                            String outputFile, NotificationDetails details){
      boolean previouslyCorrupted = false;
      
      int res = DOWNLOAD_SUCCESS;
      for (int i = 0; i < RETRY_COUNT; i++){
         if (mIsDownloadCanceled){ break;  }

         if (i > 0){
            // want to wait before retrying again
            try { Thread.sleep(WAIT_TIME); }
            catch (InterruptedException exception){
               // no op
            }
         }

         mWifiLock.acquire();
         res = startDownload(urlString, destination, outputFile, details);
         if (mWifiLock.isHeld()){ mWifiLock.release(); }
         
         if (res == DOWNLOAD_SUCCESS){
            return true;
         }
         else if (res == QuranDownloadNotifier.ERROR_DISK_SPACE ||
             res == QuranDownloadNotifier.ERROR_PERMISSIONS){
            // critical errors
            mNotifier.notifyError(res, true, details);
            return false;
         }
         else if (res == QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD){
            // corrupted download
            if (!previouslyCorrupted){
               // give one more chance if this is the first time 
               // this file was corrupted
               i--;
               previouslyCorrupted = true;
            }
            
            if (i + 1 < RETRY_COUNT){
               notifyError(res, false, details);
            }
         }
      }
      
      if (mIsDownloadCanceled){ res = QuranDownloadNotifier.ERROR_CANCELLED; }
      notifyError(res, true, details);
      return false;
   }

   private int startDownload(String url, String path,
       String filename, NotificationDetails notificationInfo) {
      if (!QuranUtils.haveInternet(this)){
         notifyError(QuranDownloadNotifier.ERROR_NETWORK,
             false, notificationInfo);
         return QuranDownloadNotifier.ERROR_NETWORK;
      }
      final int result = downloadUrl(url, path, filename, notificationInfo);
      if (result == DOWNLOAD_SUCCESS) {
         if (filename.endsWith("zip")){
            if (notificationInfo.totalFiles == 1){
               mLastSentIntent = mNotifier.notifyDownloadProcessing(
                   notificationInfo, 0, 0);
            }

            final File actualFile = new File(path, filename);
            if (!ZipUtils.unzipFile(actualFile.getAbsolutePath(),
                path, notificationInfo, this)){
               return !actualFile.delete() ?
                   QuranDownloadNotifier.ERROR_PERMISSIONS :
                   QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD;
            }
         }
      }
      return result;
   }

   private int downloadUrl(String url, String path, String filename,
       NotificationDetails notificationInfo) {
      final Request.Builder builder = new Request.Builder()
          .url(url).tag(DEFAULT_TAG);
      final File partialFile = new File(path, filename + PARTIAL_EXT);
      final File actualFile = new File(path, filename);

      long downloadedAmount = 0;
      if (partialFile.exists()) {
         downloadedAmount = partialFile.length();
         builder.addHeader("Range", "bytes=" + downloadedAmount + "-");
      }
      final boolean isZip = filename.endsWith(".zip");

      Call call = null;
      BufferedSource source = null;
      try {
         final Request request = builder.build();
         call = mOkHttpClient.newCall(request);
         final Response response = call.execute();
         if (response.isSuccessful()) {
            final BufferedSink sink =
                Okio.buffer(Okio.appendingSink(partialFile));
            final ResponseBody body = response.body();
            source = body.source();
            final long size = body.contentLength() + downloadedAmount;

            if (!isSpaceAvailable(size +
                (isZip ? downloadedAmount + size : 0))) {
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
            while (!mIsDownloadCanceled &&
                ((read = source.read(sink.buffer(), BUFFER_SIZE)) > 0)) {
               totalRead += read;
               if (loops++ % 5 == 0) {
                  mLastSentIntent = mNotifier.notifyProgress(
                      notificationInfo, totalRead, size);
               }
               sink.flush();
            }
            QuranFileUtils.closeQuietly(sink);

            if (mIsDownloadCanceled) {
               return QuranDownloadNotifier.ERROR_CANCELLED;
            } else if (!partialFile.renameTo(actualFile)){
               return notifyError(QuranDownloadNotifier.ERROR_PERMISSIONS,
                   true, notificationInfo);
            }
            return DOWNLOAD_SUCCESS;
         } else if (response.code() == 416) {
            if (!partialFile.delete()) {
               return QuranDownloadNotifier.ERROR_PERMISSIONS;
            }
            return downloadUrl(url, path, filename, notificationInfo);
         }
      } catch (IOException exception) {
         Log.e(TAG, "Failed to download file", exception);
      } finally {
         QuranFileUtils.closeQuietly(source);
      }

      return (call != null && call.isCanceled()) ?
          QuranDownloadNotifier.ERROR_CANCELLED :
          notifyError(QuranDownloadNotifier.ERROR_NETWORK,
              false, notificationInfo);
   }

   @Override
   public void onProcessingProgress(
       NotificationDetails details, int processed, int total) {
      if (details.totalFiles == 1) {
         mLastSentIntent = mNotifier.notifyDownloadProcessing(
             details, processed, total);
      }
   }

   private int notifyError(int errorCode, boolean isFatal,
       NotificationDetails details){
      mLastSentIntent = mNotifier.notifyError(errorCode, isFatal, details);

      if (isFatal){
         // write last error in prefs
         mSharedPreferences.edit()
             .putString(PREF_LAST_DOWNLOAD_ITEM, details.key)
             .putInt(PREF_LAST_DOWNLOAD_ERROR, errorCode)
             .commit();
      }
      return errorCode;
   }

   // TODO: this is actually a bug - we may not be using /sdcard...
   private boolean isSpaceAvailable(long spaceNeeded){
      StatFs fsStats = new StatFs(
            Environment.getExternalStorageDirectory().getAbsolutePath());
      double availableSpace = (double)fsStats.getAvailableBlocks() *
            (double)fsStats.getBlockSize();

      return availableSpace > spaceNeeded;
   }

   private static String getFilenameFromUrl(String url){
      int slashIndex = url.lastIndexOf("/");
      if (slashIndex != -1){
         return url.substring(slashIndex + 1);
      }
      
      // should never happen
      return url;
   }
}
