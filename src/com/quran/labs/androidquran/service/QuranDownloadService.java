package com.quran.labs.androidquran.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.QuranActivity;

public class QuranDownloadService extends Service {

   public static final String TAG = "QuranDownloadService";
   
   // intent actions
   public static final String ACTION_DOWNLOAD_URL =
         "com.quran.labs.androidquran.DOWNLOAD_URL";
   public static final String ACTION_CANCEL_DOWNLOADS =
         "com.quran.labs.androidquran.CANCEL_DOWNLOADS";
   
   // extras
   public static final String EXTRA_URL = "url";
   public static final String EXTRA_DESTINATION = "destination";
   public static final String EXTRA_NOTIFICATION_NAME = "notificationName";
   
   private static final int BUFFER_SIZE = 4096;
   private static final int WAIT_TIME = 15 * 1000;
   private static final int RETRY_COUNT = 3;
   private static final String PARTIAL_EXT = ".part";
   
   // notification ids
   private static final int DOWNLOADING_NOTIFICATION = 1;
   private static final int DOWNLOADING_COMPLETE_NOTIFICATION = 2;
   private static final int DOWNLOADING_ERROR_NOTIFICATION = 3;
   
   // error messages
   public static final int ERROR_GENERAL = 1;
   public static final int ERROR_DISK_SPACE = 2;
   public static final int ERROR_NETWORK = 3;
   public static final int ERROR_PERMISSIONS = 4;
   public static final int ERROR_INVALID_DOWNLOAD = 5;
   
   // download method return values
   private static final int DOWNLOAD_SUCCESS = 1;
   private static final int DOWNLOAD_SHOULD_RETRY = 2;
   private static final int DOWNLOAD_FAILED_NO_RETRY = 3;
   
   private Looper mServiceLooper;
   private ServiceHandler mServiceHandler;
   
   // written from ui thread and read by download thread
   private volatile boolean mIsDownloadCanceled;
   private NotificationManager mNotificationManager;
   
   // to handle notification styling issues pre-gingerbread
   private Integer mNotificationTitleColor = null;
   private Integer mNotificationTextColor = null;
   private float mNotificationTitleSize = 14.0f;
   private float mNotificationTextSize = 12.0f;
   
   private final class ServiceHandler extends Handler {
      public ServiceHandler(Looper looper){
         super(looper);
      }
      
      @Override
      public void handleMessage(Message msg){
         onHandleIntent((Intent)msg.obj);
         stopSelf(msg.arg1);
      }
   }
   
   @Override
   public void onCreate() {
      super.onCreate();
      HandlerThread thread = new HandlerThread(TAG);
      thread.start();
      
      mServiceLooper = thread.getLooper();
      mServiceHandler = new ServiceHandler(mServiceLooper);
      mIsDownloadCanceled = false;
      
      String ns = Context.NOTIFICATION_SERVICE;
      mNotificationManager = (NotificationManager)getSystemService(ns);
   }
   
   @Override
   public void onStart(Intent intent, int startId){
      if (intent != null){
         if (ACTION_CANCEL_DOWNLOADS.equals(intent.getAction())){
            mServiceHandler.removeCallbacksAndMessages(null);
            mIsDownloadCanceled = true;
         }
         else {
            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.obj = intent;
            mServiceHandler.sendMessage(msg);
         }
      }
   }
   
   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      onStart(intent, startId);
      return START_NOT_STICKY;
   }
   
   @Override
   public void onDestroy() {
      mServiceLooper.quit();
   }
   
   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }
   
   private void onHandleIntent(Intent intent){
      if (ACTION_DOWNLOAD_URL.equals(intent.getAction())){
         String url = intent.getStringExtra(EXTRA_URL);
         String destination = intent.getStringExtra(EXTRA_DESTINATION);
         String notificationTitle =
               intent.getStringExtra(EXTRA_NOTIFICATION_NAME);

         download(url, destination, notificationTitle);
      }
   }
   
   private boolean download(String urlString, String destination,
         String notificationTitle){
      // make the directory if it doesn't exist
      new File(destination).mkdirs();
      android.util.Log.d(TAG, "making directory: " + destination);
      
      for (int i = 0; i < RETRY_COUNT; i++){
         if (mIsDownloadCanceled){ return false;  }

         if (i > 0){
            // want to wait before retrying again
            try { Thread.sleep(WAIT_TIME); }
            catch (InterruptedException exception){}
         }
         
         int res = downloadFile(urlString, destination, notificationTitle);
         if (res == DOWNLOAD_SUCCESS){
            return true;
         }
         else if (res == DOWNLOAD_FAILED_NO_RETRY){
            return false;
         }
      }
      
      if (!mIsDownloadCanceled){
         notifyError(ERROR_GENERAL, notificationTitle);
      }
      return false;
   }
   
   private int downloadFile(String urlString, String destination,
         String notificationTitle){
      HttpURLConnection connection = null;
      try {         
         long downloaded = 0;
         
         // notify download starting
         notifyProgress(notificationTitle, true, 0, 100);
         
         URL url = new URL(urlString);
         String filename = QuranDownloadService.getFilenameFromUrl(urlString);
         File partialFile = new File(destination, filename + PARTIAL_EXT);
         if (partialFile.exists()){
            downloaded = partialFile.length();
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
         
         File actualFile = new File(destination, filename);
         android.util.Log.d(TAG, "actualFile: " + actualFile.getPath() +
               ", " + actualFile.getAbsolutePath() + ", " +
               actualFile.getName());
         
         long fileLength = downloaded + (rc == 416? 0 : contentLength);
         if (rc != 416 && (!actualFile.exists() ||
               actualFile.length() != fileLength)){
            
            if (!isSpaceAvailable(fileLength, filename.endsWith(".zip"))){
               notifyError(ERROR_DISK_SPACE, notificationTitle);
               return DOWNLOAD_FAILED_NO_RETRY;
            }
            
            if (actualFile.exists()){
               if (!actualFile.delete()){
                  notifyError(ERROR_PERMISSIONS, notificationTitle);
                  return DOWNLOAD_FAILED_NO_RETRY;
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
            while (!mIsDownloadCanceled &&
                  (x = inputStream.read(data, 0, BUFFER_SIZE)) >= 0){
               bufferedOutputStream.write(data, 0, x);
               downloaded += x;
               
               double percent = 100.0 * ((1.0 * downloaded) /
                     (1.0 * fileLength));
               notifyProgress(notificationTitle, false, (int)percent, 100);

            }
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
         }
         
         if (!mIsDownloadCanceled){
            notifyDownloadProcessing(notificationTitle);
         }
         
         if (!actualFile.exists() && downloaded == fileLength){
            android.util.Log.d(TAG, "moving file...");
            if (!partialFile.renameTo(actualFile)){
               notifyError(ERROR_PERMISSIONS, notificationTitle);
               return DOWNLOAD_FAILED_NO_RETRY;
            }
         }
         
         if (actualFile.exists()){
            if (actualFile.getName().endsWith(".zip")){
               if (!unzipFile(actualFile.getAbsolutePath(), destination)){
                  if (!actualFile.delete()){
                     notifyError(ERROR_PERMISSIONS, notificationTitle);
                     return DOWNLOAD_FAILED_NO_RETRY;
                  }
                  notifyError(ERROR_INVALID_DOWNLOAD, notificationTitle);
                  return DOWNLOAD_SHOULD_RETRY;
               }
            }
            
            notifyDownloadSuccessful(notificationTitle);
            return DOWNLOAD_SUCCESS;
         }
         
         return DOWNLOAD_SHOULD_RETRY;
      }
      catch (Exception e){
         android.util.Log.d(TAG, "exception while downloading: " + e);
         notifyError(ERROR_NETWORK, notificationTitle);
         return DOWNLOAD_SHOULD_RETRY;
      }
      finally {
         connection.disconnect();
      }
   }
   
   protected boolean unzipFile(String zipFile, String destDirectory){
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

            // ignore files that already exist
            File f = new File(destDirectory, entry.getName());
            if (!f.exists()) {
               InputStream is = zip.getInputStream(entry);
               FileOutputStream ostream = new FileOutputStream(f);

               int size;
               byte[] buf = new byte[BUFFER_SIZE];
               while ((size = is.read(buf)) > 0)
                  ostream.write(buf, 0, size);
               is.close();
               ostream.close();
            }
            
            android.util.Log.d(TAG, "progress: " + processedFiles +
                  " from " + numberOfFiles);
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
   
   protected boolean isSpaceAvailable(long fileLength, boolean isZipFile){
      StatFs fsStats = new StatFs(
            Environment.getExternalStorageDirectory().getAbsolutePath());
      double availableSpace = (double)fsStats.getAvailableBlocks() *
            (double)fsStats.getBlockSize();
      
      long length = isZipFile? fileLength * 2 : fileLength;
      return availableSpace > length;
   }
   
   public void notifyProgress(String notificationTitle,
         boolean isIndeterminate, int progress, int max){
      showNotification(notificationTitle, null,
            DOWNLOADING_NOTIFICATION, true, max, progress, isIndeterminate);
   }
   
   public void notifyDownloadProcessing(String notificationTitle){
      // TODO make this processing or installing %s instead
      showNotification(notificationTitle, null,
            DOWNLOADING_NOTIFICATION, true, 0, 100, true);
   }
   
   public void notifyDownloadSuccessful(String notificationTitle){
      String successString = "";
      mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
      mNotificationManager.cancel(DOWNLOADING_ERROR_NOTIFICATION);
      showNotification(notificationTitle, successString,
            DOWNLOADING_COMPLETE_NOTIFICATION, false, 0, 0, false);
   }
   
   public void notifyError(int errorCode, String notificationTitle){
      String errorString = "";
      switch (errorCode){
      // TODO fill these error messages
      case ERROR_GENERAL:
      case ERROR_DISK_SPACE:
      case ERROR_NETWORK:
      case ERROR_PERMISSIONS:
      case ERROR_INVALID_DOWNLOAD:
      }
      
      mNotificationManager.cancel(DOWNLOADING_NOTIFICATION);
      showNotification(notificationTitle, errorString,
            DOWNLOADING_ERROR_NOTIFICATION, false, 0, 0, false);
   }
   
   private void showNotification(String titleString,
         String statusString, int notificationId, boolean isOnGoing,
         int maximum, int progress, boolean isIndeterminate){
      
      if (Build.VERSION.SDK_INT < 9){
         if (mNotificationTitleColor == null){
            computeNotificationStyles();
         }
      }
      
      // we recreate the notification each time to work around
      // http://code.google.com/p/android/issues/detail?id=13941
      Notification notification = new Notification(R.drawable.icon,
            titleString, System.currentTimeMillis());
      notification.flags |= Notification.FLAG_AUTO_CANCEL;
      if (isOnGoing){
         notification.flags |= Notification.FLAG_ONGOING_EVENT;
      }
      
      notification.defaults = Notification.DEFAULT_LIGHTS; 
      
      RemoteViews contentView = new RemoteViews(getPackageName(),
            R.layout.notification_layout);
      contentView.setImageViewResource(R.id.image, R.drawable.icon);
      contentView.setTextViewText(R.id.title, titleString);
      
      if (statusString != null){
         contentView.setViewVisibility(R.id.text, View.VISIBLE);
         contentView.setViewVisibility(R.id.progress_bar_wrapper, View.GONE);
         contentView.setTextViewText(R.id.text, statusString);
      }
      else {
         contentView.setViewVisibility(R.id.text, View.GONE);
         contentView.setViewVisibility(R.id.progress_bar_wrapper, View.VISIBLE);
         contentView.setProgressBar(R.id.progress_bar,
               maximum, progress, isIndeterminate);
      }
      
      // set the styles for pre-gingerbread phones
      if (mNotificationTitleColor != null){
         contentView.setTextColor(R.id.title, mNotificationTitleColor);
         contentView.setFloat(R.id.title, "setTextSize", mNotificationTitleSize);
         contentView.setTextColor(R.id.text, mNotificationTextColor);
         contentView.setFloat(R.id.text, "setTextSize", mNotificationTextSize);
      }
      
      notification.contentView = contentView;
      Intent notificationIntent = new Intent(getApplicationContext(),
            QuranActivity.class);
      PendingIntent contentIntent = PendingIntent.getActivity(
            getApplicationContext(), 0, notificationIntent, 0);
      notification.contentIntent = contentIntent;
      
      mNotificationManager.notify(notificationId, notification);
   }
   
   // http://stackoverflow.com/a/7320604/314324
   private void computeNotificationStyles(){
      Notification notification = new Notification();
      notification.setLatestEventInfo(this, "title", "text", null);
      
      try {
         LinearLayout group = new LinearLayout(this);
         ViewGroup event = (ViewGroup)notification
               .contentView.apply(this, group);
         populateStyles(event);
         group.removeAllViews();
      }
      catch (Exception e){
         // default to black since android:attr/textColorPrimary
         // is white (at least on 2.1) and unreadable anyways.
         mNotificationTextColor = android.R.color.black;
         mNotificationTitleColor = android.R.color.black;
      }
   }
   
   /**
    * given a notification view group, figures out the text color
    * and text size of the title and status texts.
    * @param group a viewgroup of the notification
    * @return a boolean based on success
    * 
    * credits to the answer and comments here:
    * http://stackoverflow.com/a/7320604/314324
    */
   private boolean populateStyles(ViewGroup group){
      final int count = group.getChildCount();
      for (int i=0; i<count; ++i){
         if (group.getChildAt(i) instanceof TextView){
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm =
                  (WindowManager)getSystemService(Context.WINDOW_SERVICE);
            wm.getDefaultDisplay().getMetrics(metrics);
            
            final TextView tv = (TextView)group.getChildAt(i);
            final String tvText = tv.getText().toString();
            if ("title".equals(tvText)){
               mNotificationTitleColor = tv.getTextColors().getDefaultColor();
               mNotificationTitleSize = tv.getTextSize() / metrics.scaledDensity;
            }
            else if ("text".equals(tvText)){
               mNotificationTextColor = tv.getTextColors().getDefaultColor();
               mNotificationTextSize = tv.getTextSize() / metrics.scaledDensity;
            }
            
            if (mNotificationTitleColor != null &&
                  mNotificationTextColor != null){
               return true;
            }
         }
         else if (group.getChildAt(i) instanceof ViewGroup){
            if (populateStyles((ViewGroup)group.getChildAt(i))){
               return true;
            }
         }
      }
      return false;
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
