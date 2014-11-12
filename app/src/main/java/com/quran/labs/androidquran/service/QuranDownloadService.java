package com.quran.labs.androidquran.service;

import com.quran.labs.androidquran.QuranDataActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.task.TranslationListTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class QuranDownloadService extends Service {

   public static final String TAG = "QuranDownloadService";
   
   // intent actions
   public static final String ACTION_DOWNLOAD_URL =
         "com.quran.labs.androidquran.DOWNLOAD_URL";
   public static final String ACTION_DOWNLOAD_RANGE =
         "com.quran.labs.androidquran.DOWNLOAD_RANGE";
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

   private static final int BUFFER_SIZE = 4096 * 2;
   private static final int WAIT_TIME = 15 * 1000;
   private static final int RETRY_COUNT = 3;
   private static final String PARTIAL_EXT = ".part";
   
   // notification ids
   private static final int DOWNLOADING_NOTIFICATION = 1;
   public static final int DOWNLOADING_COMPLETE_NOTIFICATION = 2;
   private static final int DOWNLOADING_ERROR_NOTIFICATION = 3;
   
   // download method return values
   private static final int DOWNLOAD_SUCCESS = 0;
   // error messages
   public static final int ERROR_DISK_SPACE = 1;
   public static final int ERROR_PERMISSIONS = 2;
   public static final int ERROR_NETWORK = 3;
   public static final int ERROR_INVALID_DOWNLOAD = 4;
   public static final int ERROR_CANCELLED = 5;
   public static final int ERROR_GENERAL = 6;

   private Looper mServiceLooper;
   private ServiceHandler mServiceHandler;
   
   // written from ui thread and read by download thread
   private volatile boolean mIsDownloadCanceled;
   private NotificationManager mNotificationManager;
   private LocalBroadcastManager mBroadcastManager;
   private SharedPreferences mSharedPreferences;
   private WifiLock mWifiLock;
   
   private Intent mLastSentIntent = null;
   private Map<String, Boolean> mSuccessfulZippedDownloads = null;
   private Map<String, Intent> mRecentlyFailedDownloads = null;

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
   }
   
   // states for the intent maybe one of these values
   public static final String STATE_DOWNLOADING = "downloading";
   public static final String STATE_PROCESSING = "processing";
   public static final String STATE_SUCCESS = "success";
   public static final String STATE_ERROR = "error";
   public static final String STATE_ERROR_WILL_RETRY = "errorWillRetry";
   
   public class NotificationDetails {
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
   
   private final class ServiceHandler extends Handler {
      public ServiceHandler(Looper looper){
         super(looper);
      }
      
      @Override
      public void handleMessage(Message msg){
         if (msg.what == TRANSLATIONS_UPDATE){
            updateTranslations();
         }
         else if (msg.obj != null){
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
      
      mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "downloadLock");
      
      mServiceLooper = thread.getLooper();
      mServiceHandler = new ServiceHandler(mServiceLooper);
      mIsDownloadCanceled = false;
      mSuccessfulZippedDownloads = new HashMap<String, Boolean>();
      mRecentlyFailedDownloads = new HashMap<String, Intent>();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());
      
      String ns = Context.NOTIFICATION_SERVICE;
      mNotificationManager = (NotificationManager)getSystemService(ns);
      mBroadcastManager = LocalBroadcastManager.getInstance(
            getApplicationContext());
      
      // work around connection reuse bug in froyo
      // android-developers.blogspot.com/2011/09/androids-http-clients.html
      if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) {
         System.setProperty("http.keepAlive", "false");
     }
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
                       STATE_DOWNLOADING);
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
               if (!STATE_SUCCESS.equals(state) && !STATE_ERROR.equals(state)){
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
            broadcastDownloadSuccessful(details);
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

         // get the start/end ayah info if it's a ranged download
         Serializable startAyah = intent.getSerializableExtra(EXTRA_START_VERSE);
         Serializable endAyah = intent.getSerializableExtra(EXTRA_END_VERSE);
         boolean isGapless = intent.getBooleanExtra(EXTRA_IS_GAPLESS, false);

         String outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE_NAME);
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
      notifyProgress(details, 0, 0);
      boolean result = downloadFileWrapper(urlString, destination,
              outputFile, details);
      if (result){ notifyDownloadSuccessful(details); }
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
      notifyProgress(details, 0, 0);

      // extension and filename template don't change
      String filename = QuranDownloadService.getFilenameFromUrl(urlString);
      int extLocation = filename.lastIndexOf(".");
      String extension = filename.substring(extLocation);

      boolean result = true;
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
            result = downloadFileWrapper(url, destDir, null, details);
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

      if (result){
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

         notifyDownloadSuccessful(details);
      }

      return result;
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
            catch (InterruptedException exception){}
         }
         
         mWifiLock.acquire();
         res = downloadFile(urlString, destination, outputFile,  details);
         if (mWifiLock.isHeld()){ mWifiLock.release(); }
         
         if (res == DOWNLOAD_SUCCESS){
            return true;
         }
         else if (res == ERROR_DISK_SPACE || res == ERROR_PERMISSIONS){
            // critical errors
            notifyError(res, true, details);
            return false;
         }
         else if (res == ERROR_INVALID_DOWNLOAD){
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
      
      if (mIsDownloadCanceled){ res = ERROR_CANCELLED; }
      notifyError(res, true, details);
      return false;
   }
   
   private int downloadFile(String urlString, String destination,
         String outputFile, NotificationDetails notificationInfo){
      HttpURLConnection connection = null;

      try {         
         long downloaded = 0;

         URL url = new URL(urlString);
         String filename = QuranDownloadService.getFilenameFromUrl(urlString);

         String fileToCheck = filename;
         if (outputFile != null){ fileToCheck = outputFile; }

         File partialFile = new File(destination, fileToCheck + PARTIAL_EXT);
         if (partialFile.exists()){
            downloaded = partialFile.length();
         }

         if (!haveInternet()){
            notifyError(ERROR_NETWORK, false, notificationInfo);
            return ERROR_NETWORK;
         }
         
         connection = (HttpURLConnection)url
               .openConnection();
         connection.setRequestProperty("Range",
               "bytes=" + downloaded + "-");
         connection.setDoInput(true);

         long contentLength = connection.getContentLength();
         String lengthHeader = connection.getHeaderField("Content-Length");
         if (lengthHeader != null){
            try { contentLength = Long.parseLong(lengthHeader); }
            catch (NumberFormatException nfe){ }
         }
        
         int rc = connection.getResponseCode();
         android.util.Log.d(TAG, "got content length: " + contentLength +
               ", rc: " + connection.getResponseCode());         

         File actualFile = new File(destination, fileToCheck);
         android.util.Log.d(TAG, "actualFile: " + actualFile.getPath() +
               ", " + actualFile.getAbsolutePath() + ", " +
               actualFile.getName());

         // check for 200 response code - happens on some devices
         if (rc == HttpURLConnection.HTTP_OK){
            rc = HttpURLConnection.HTTP_PARTIAL;
            if (downloaded != 0){
               // just in case, remove the actual file if exists
               if (actualFile.exists()){
                  if (!actualFile.delete()){
                     return ERROR_PERMISSIONS;
                  }
               }
               // just in case, remove the partial file
               if (partialFile.exists()){
                  if (!partialFile.delete()){
                     return ERROR_PERMISSIONS;
                  }
               }
               downloaded = 0;
            }
         }
         
         long fileLength = downloaded +
                 (rc == HttpURLConnection.HTTP_PARTIAL? contentLength : 0);
         if (rc == HttpURLConnection.HTTP_PARTIAL &&
                 (!actualFile.exists() || actualFile.length() != fileLength)){

            if (!isSpaceAvailable(downloaded, fileLength,
                    filename.endsWith(".zip"))){
               return ERROR_DISK_SPACE;
            }
            
            if (actualFile.exists()){
               if (!actualFile.delete()){
                  return ERROR_PERMISSIONS;
               }
            }
            
            BufferedInputStream inputStream =
                  new BufferedInputStream(connection.getInputStream(),
                        BUFFER_SIZE);
            FileOutputStream fileOutputStream =
                  new FileOutputStream(partialFile.getAbsolutePath(),
                        downloaded != 0);
            BufferedOutputStream bufferedOutputStream =
                  new BufferedOutputStream(fileOutputStream, BUFFER_SIZE);
            byte[] data = new byte[BUFFER_SIZE];            
            
            int x = 0;
            int updateCount = 0;
            while (!mIsDownloadCanceled &&
                  (x = inputStream.read(data, 0, BUFFER_SIZE)) >= 0){
               bufferedOutputStream.write(data, 0, x);
               downloaded += x;
               if (updateCount % 5 == 0){
                  notifyProgress(notificationInfo, downloaded, fileLength);
               }
               updateCount++;
            }
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
         }
         else if (rc != HttpURLConnection.HTTP_PARTIAL && rc != 416){
            Log.d(TAG, "got unexpected response code: " + rc);
            notifyError(ERROR_NETWORK, false, notificationInfo);
            return ERROR_NETWORK;
         }
         
         if (!mIsDownloadCanceled){
            notifyDownloadProcessing(notificationInfo, 0, 0);
         }
         
         if (!actualFile.exists() && downloaded == fileLength){
            android.util.Log.d(TAG, "moving file...");
            if (!partialFile.renameTo(actualFile)){
               notifyError(ERROR_PERMISSIONS, true, notificationInfo);
               return ERROR_PERMISSIONS;
            }
         }
         
         if (actualFile.exists()){
            if (actualFile.getName().endsWith(".zip")){
               if (!unzipFile(actualFile.getAbsolutePath(), destination,
                     notificationInfo)){
                  if (!actualFile.delete()){
                     return ERROR_PERMISSIONS;
                  }
                  return ERROR_INVALID_DOWNLOAD;
               }
            }
            
            return DOWNLOAD_SUCCESS;
         }
         
         return ERROR_GENERAL;
      }
      catch (Exception e){
         Log.d(TAG, "exception while downloading: " + e);
         notifyError(ERROR_NETWORK, false, notificationInfo);
         return ERROR_NETWORK;
      }
      finally {
         if (connection != null){ connection.disconnect(); }
      }
   }
   
   protected boolean unzipFile(String zipFile, String destDirectory,
                               NotificationDetails notificationInfo){
      try {
         Log.d(TAG, "Unziping file: " +  zipFile + "to: " + destDirectory);
         
         File file = new File(zipFile);
         ZipFile zip = new ZipFile(file, ZipFile.OPEN_READ);
         int numberOfFiles = zip.size();
         Enumeration<? extends ZipEntry> entries = zip.entries();
         
         int processedFiles = 0;
         while (entries.hasMoreElements()) {
            processedFiles++;
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
               File f = new File(destDirectory, entry.getName());
               if (!f.exists()){
                  f.mkdirs();
               }
               continue;
            }

            // delete files that already exist
            File f = new File(destDirectory, entry.getName());
            if (f.exists()){
               f.delete();
            }

            InputStream is = zip.getInputStream(entry);
            FileOutputStream ostream = new FileOutputStream(f);

            int size;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((size = is.read(buf)) > 0)
               ostream.write(buf, 0, size);
            is.close();
            ostream.close();
            
            //android.util.Log.d(TAG, "progress: " + processedFiles +
            //      " from " + numberOfFiles);
            notifyDownloadProcessing(notificationInfo,
                    processedFiles, numberOfFiles);
         }
         
         zip.close();
         file.delete();         
         return true;
      }
      catch (IOException ioe) {
         Log.e(TAG, "Error unzipping file: ", ioe);
         return false;
      }
   }
   
   protected boolean haveInternet() {
      ConnectivityManager cm = (ConnectivityManager)getSystemService(
            Context.CONNECTIVITY_SERVICE);
      if (cm != null && cm.getActiveNetworkInfo() != null) 
         return cm.getActiveNetworkInfo().isConnectedOrConnecting();
      return false;
   }
   
   protected boolean isSpaceAvailable(long downloaded,
                                      long fileLength, boolean isZipFile){
      StatFs fsStats = new StatFs(
            Environment.getExternalStorageDirectory().getAbsolutePath());
      double availableSpace = (double)fsStats.getAvailableBlocks() *
            (double)fsStats.getBlockSize();
      
      long length = isZipFile? (fileLength + (fileLength - downloaded)) :
              (fileLength - downloaded);
      return availableSpace > length;
   }
   
   public void notifyProgress(NotificationDetails details,
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

      showNotification(details.title, getString(R.string.downloading_title),
            DOWNLOADING_NOTIFICATION, true, max, progress, isIndeterminate);
      
      // send broadcast
      Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
      progressIntent.putExtra(ProgressIntent.NAME, details.title);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
      progressIntent.putExtra(ProgressIntent.STATE, STATE_DOWNLOADING);
      if (!isIndeterminate){
         progressIntent.putExtra(ProgressIntent.DOWNLOADED_SIZE,
               downloadedSize);
         progressIntent.putExtra(ProgressIntent.TOTAL_SIZE, totalSize);
         progressIntent.putExtra(ProgressIntent.PROGRESS, progress);
      }
      mBroadcastManager.sendBroadcast(progressIntent);
      mLastSentIntent = progressIntent;
   }
   
   public void notifyDownloadProcessing(NotificationDetails details,
                                        int done, int total){
      if (details.totalFiles > 1){ return; }

      String processingString = getString(R.string.download_processing);
      showNotification(details.title, processingString,
            DOWNLOADING_NOTIFICATION, true);
      
      // send broadcast
      Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
      progressIntent.putExtra(ProgressIntent.NAME, details.title);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
      progressIntent.putExtra(ProgressIntent.STATE, STATE_PROCESSING);

      if (total > 0){
         int progress = (int)((100.0 * done) / (1.0 * total));
         progressIntent.putExtra(ProgressIntent.PROGRESS, progress);
         progressIntent.putExtra(ProgressIntent.PROCESSED_FILES, done);
         progressIntent.putExtra(ProgressIntent.TOTAL_FILES, total);
      }
      
      mBroadcastManager.sendBroadcast(progressIntent);
      mLastSentIntent = progressIntent;
   }
   
   public void notifyDownloadSuccessful(NotificationDetails details){
      String successString = getString(R.string.download_successful);
      mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
      mNotificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);
      showNotification(details.title, successString,
            DOWNLOADING_COMPLETE_NOTIFICATION, false);
      broadcastDownloadSuccessful(details);
   }
    
   public void broadcastDownloadSuccessful(NotificationDetails details){
      // send broadcast
      Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
      progressIntent.putExtra(ProgressIntent.NAME, details.title);
      progressIntent.putExtra(ProgressIntent.STATE, STATE_SUCCESS);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
      mBroadcastManager.sendBroadcast(progressIntent);
      mLastSentIntent = progressIntent;
   }
   
   public void notifyError(int errorCode, boolean isFatal,
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
      
      String errorString = getString(errorId);    
      mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
      showNotification(details.title, errorString,
            DOWNLOADING_ERROR_NOTIFICATION, false);
      
      if (isFatal){
         // write last error in prefs
         mSharedPreferences.edit()
         .putString(PREF_LAST_DOWNLOAD_ITEM, details.key)
         .putInt(PREF_LAST_DOWNLOAD_ERROR, errorCode)
         .commit();
      }
      
      String state = isFatal? STATE_ERROR : STATE_ERROR_WILL_RETRY;
      
      // send broadcast
      Intent progressIntent = new Intent(ProgressIntent.INTENT_NAME);
      progressIntent.putExtra(ProgressIntent.NAME, details.title);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_KEY, details.key);
      progressIntent.putExtra(ProgressIntent.DOWNLOAD_TYPE, details.type);
      progressIntent.putExtra(ProgressIntent.STATE, state);
      progressIntent.putExtra(ProgressIntent.ERROR_CODE, errorCode);
      mBroadcastManager.sendBroadcast(progressIntent);
      mLastSentIntent = progressIntent;
   }

   private void showNotification(String titleString,
       String statusString, int notificationId, boolean isOnGoing) {
      showNotification(titleString, statusString, notificationId,
          isOnGoing, 0, 0, false);
   }

   private void showNotification(String titleString,
         String statusString, int notificationId, boolean isOnGoing,
         int maximum, int progress, boolean isIndeterminate){

      NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
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

      Intent notificationIntent = new Intent(getApplicationContext(),
          QuranDataActivity.class);
      PendingIntent contentIntent = PendingIntent.getActivity(
          getApplicationContext(), 0, notificationIntent, 0);
      builder.setContentIntent(contentIntent);
      mNotificationManager.notify(notificationId, builder.build());
   }
   
   public static String getFilenameFromUrl(String url){
      int slashIndex = url.lastIndexOf("/");
      if (slashIndex != -1){
         return url.substring(slashIndex + 1);
      }
      
      // should never happen
      return url;
   }
}
