package com.quran.labs.androidquran;

import java.text.DecimalFormat;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Display;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.service.QuranDataService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.QuranDownloadService.ProgressIntent;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;

public class QuranDataActivity extends SherlockActivity {
   
   public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";
   
   private boolean mIsPaused = false;
   private boolean mRegisteredReceiver = false;
   private AsyncTask<Void, Void, Boolean> mCheckPagesTask;
   private AlertDialog mErrorDialog = null;
   private AlertDialog mPromptForDownloadDialog = null;
   private ProgressDialog mProgressDialog = null;
   private SharedPreferences mSharedPreferences = null;
   private Resources mResources = null;
   private boolean mDidReceiveBroadcast = false;

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      
      super.onCreate(savedInstanceState);
      getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
      setContentView(R.layout.splash_screen);

      /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
       */

      initializeQuranScreen();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());
      mResources = getResources();
   }
   
   @Override
   protected void onResume(){
      super.onResume();
      
      mIsPaused = false;
      mDidReceiveBroadcast = false;
      initProgressDialog();

      String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
      LocalBroadcastManager.getInstance(this).registerReceiver(
            mMessageReceiver,
            new IntentFilter(action));
      mRegisteredReceiver = true;

      // check whether or not we need to download
      mCheckPagesTask = new CheckPagesAsyncTask();
      mCheckPagesTask.execute();
   }
   
   @Override
   protected void onPause() {
      mIsPaused = true;
      mHandler.removeCallbacksAndMessages(null);
      
      cleanup();
      if (mPromptForDownloadDialog != null){
         mPromptForDownloadDialog.dismiss();
         mPromptForDownloadDialog = null;
      }
      
      if (mErrorDialog != null){
         mErrorDialog.dismiss();
         mErrorDialog = null;
      }
      
      if (mProgressDialog != null){
         mProgressDialog.dismiss();
         mProgressDialog = null;
      }
      
      super.onPause();
   }
   
   private void cleanup(){
      if (mRegisteredReceiver){
         LocalBroadcastManager.getInstance(this).
            unregisterReceiver(mMessageReceiver);
         mRegisteredReceiver = false;
      }
   }
   
   BroadcastReceiver mMessageReceiver = new BroadcastReceiver(){
      @Override
      public void onReceive(Context context, Intent intent){
         if (intent != null && PAGES_DOWNLOAD_KEY.equals(
               intent.getStringExtra(ProgressIntent.DOWNLOAD_KEY))){
            mDidReceiveBroadcast = true;
            // run these on the ui thread
            Message msg = mHandler.obtainMessage();
            msg.obj = intent;

            // only care about the latest download progress, remove queued
            mHandler.removeCallbacksAndMessages(null);

            // send the message at the front of the queue
            mHandler.sendMessageAtFrontOfQueue(msg);
         }
      }
   };
   
   private Handler mHandler = new Handler(){
      @Override
      public void handleMessage(Message msg){
         if (mIsPaused){ return; }
         Intent intent = (Intent)msg.obj;
         String state = intent.getStringExtra(ProgressIntent.STATE);
         if (state != null){
            if (QuranDownloadService.STATE_DOWNLOADING.equals(state)){
               updateProgress(intent, false);
            }
            else if (QuranDownloadService.STATE_PROCESSING.equals(state)){
               updateProgress(intent, true);
            }
            else if (QuranDownloadService.STATE_SUCCESS.equals(state)){
               handleSuccess(intent);
            }
            else if (QuranDownloadService.STATE_ERROR.equals(state)){
               handleFatalError(intent);
            }
            else if (QuranDownloadService.STATE_ERROR_WILL_RETRY.equals(state)){
               handleError(intent);
            }
         }
      }
   };
   
   private void updateProgress(Intent intent, boolean isProcessing){
      if (mProgressDialog != null){
         if (!mProgressDialog.isShowing()){
            mProgressDialog.show();
         }
         
         int progress = intent.getIntExtra(ProgressIntent.PROGRESS, -1);
         if (progress == -1){
            int titleId = isProcessing? R.string.extracting_title
                  : R.string.downloading_title;
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage(getString(titleId));
            return;
         }
         
         mProgressDialog.setIndeterminate(false);
         mProgressDialog.setMax(100);
         mProgressDialog.setProgress(progress);
         
         if (isProcessing){
            mProgressDialog.setMessage(getString(R.string.extracting_title));
            int processedFiles =
                  intent.getIntExtra(ProgressIntent.PROCESSED_FILES, 0);
            int totalFiles = intent.getIntExtra(ProgressIntent.TOTAL_FILES, 0);
            
            String message = String.format(
                  mResources.getString(R.string.process_progress),
                  processedFiles, totalFiles);
            mProgressDialog.setMessage(message);
         }
         else {
            long downloadedSize =
                  intent.getLongExtra(ProgressIntent.DOWNLOADED_SIZE, 0);
            long totalSize = intent.getLongExtra(ProgressIntent.TOTAL_SIZE, 0);
            
            DecimalFormat df = new DecimalFormat("###.00");            
            int mb = 1024 * 1024;
            String downloaded = df.format((1.0 * downloadedSize / mb)) + " MB";
            String total = df.format((1.0 * totalSize / mb)) + " MB";
            
            String message = String.format(
                  mResources.getString(R.string.download_progress),
                  downloaded, total);
            mProgressDialog.setMessage(message);
         }
      }
   }
   
   private void handleSuccess(Intent intent){
      if (mProgressDialog != null){
         mProgressDialog.dismiss();
      }
      mProgressDialog = null;
      mSharedPreferences.edit()
         .remove(ApplicationConstants.PREF_SHOULD_FETCH_PAGES).commit();
      runListView();
   }
   
   private void handleFatalError(Intent intent){
      if (mProgressDialog != null){
         mProgressDialog.dismiss();
         mProgressDialog = null;
      }
      
      if (mErrorDialog != null && mErrorDialog.isShowing()){
         return;
      }
      
      int errorCode = intent.getIntExtra(ProgressIntent.ERROR_CODE, 0);
      showFatalErrorDialog(errorCode);
   }
   
   private void showFatalErrorDialog(int errorCode){
      int errorId = 0;

      switch (errorCode){
      case QuranDownloadService.ERROR_DISK_SPACE:
         errorId = R.string.download_error_disk;
         break;
      case QuranDownloadService.ERROR_NETWORK:
         errorId = R.string.download_error_network;
         break;
      case QuranDownloadService.ERROR_PERMISSIONS:
         errorId = R.string.download_error_perms;
         break;
      case QuranDownloadService.ERROR_INVALID_DOWNLOAD:
         errorId = R.string.download_error_invalid_download;
         break;
      case QuranDownloadService.ERROR_GENERAL:
      default:
         errorId = R.string.download_error_general;
      }
      
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(errorId);
      builder.setCancelable(false);
      builder.setPositiveButton(R.string.download_retry,
            new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            downloadQuranImages(true);
         }
      });
      
      builder.setNegativeButton(R.string.download_cancel,
            new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mErrorDialog = null;
            removeErrorPreferences();
            mSharedPreferences.edit().putBoolean(
                    ApplicationConstants.PREF_SHOULD_FETCH_PAGES, false)
                    .commit();
            runListView();
         }
      });
      
      mErrorDialog = builder.create();
      mErrorDialog.show();
   }
   
   private void handleError(Intent intent){
      if (mProgressDialog != null){
         int errorCode = intent.getIntExtra(ProgressIntent.ERROR_CODE, 0);
         if (errorCode == QuranDownloadService.ERROR_INVALID_DOWNLOAD){
            mProgressDialog.setMessage(
                  getString(R.string.download_error_invalid_download_retry));
         }
         else if (errorCode == QuranDownloadService.ERROR_NETWORK){
            mProgressDialog.setMessage(
                  getString(R.string.download_error_network_retry));
         }
      }
   }
   
   private void removeErrorPreferences(){
      mSharedPreferences.edit()
      .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR)
      .remove(QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM)
      .commit();
   }
   
   class CheckPagesAsyncTask extends AsyncTask<Void, Void, Boolean> {

      @Override
      protected Boolean doInBackground(Void... params) {
         return QuranFileUtils.getQuranDirectory() != null &&
                QuranFileUtils.haveAllImages();
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         mCheckPagesTask = null;
                  
         if (result == null || !result){
            String lastErrorItem = mSharedPreferences.getString(
                        QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, "");
            if (PAGES_DOWNLOAD_KEY.equals(lastErrorItem)){
               int lastError = mSharedPreferences.getInt(
                     QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0);
               showFatalErrorDialog(lastError);
            }
            else if (mSharedPreferences.getBoolean(
                    ApplicationConstants.PREF_SHOULD_FETCH_PAGES, false)){
               downloadQuranImages(false);
            }
            else {
               promptForDownload();
            }
         }
         else { runListView(); }
      }      
   }

   /**
    * this method asks the service to download quran images.
    * 
    * there are two possible cases - the first is one in which we are not
    * sure if a download is going on or not (ie we just came in the app,
    * the files aren't all there, so we want to start downloading).  in
    * this case, we start the download only if we didn't receive any
    * broadcasts before starting it.
    * 
    * in the second case, we know what we are doing (either because the user
    * just clicked "download" for the first time or the user asked to retry
    * after an error), then we pass the force parameter, which asks the
    * service to just restart the download irrespective of anything else.
    * 
    * @param force whether to force the download to restart or not
    */
   private void downloadQuranImages(boolean force){
      // if any broadcasts were received, then we are already downloading
      // so unless we know what we are doing (via force), don't ask the
      // service to restart the download
      if (mDidReceiveBroadcast && !force){ return; }
      
      if (!haveInternet()){
         mProgressDialog = null;
         showFatalErrorDialog(QuranDownloadService.ERROR_NETWORK);
         return;
      }
      
      // this is safe because we didn't get any broadcasts so we can't be
      // showing any dialogs at the moment.  we initialized this in onResume
      initProgressDialog();
      mProgressDialog.show();
      
      String url = QuranFileUtils.getZipFileUrl();
      String destination = QuranFileUtils.getQuranBaseDirectory();
      
      // start service
      Intent intent = new Intent(this, QuranDownloadService.class);
      intent.putExtra(QuranDownloadService.EXTRA_URL, url);
      intent.putExtra(QuranDownloadService.EXTRA_DESTINATION, destination);
      intent.putExtra(QuranDownloadService.EXTRA_NOTIFICATION_NAME,
            getString(R.string.app_name));
      intent.putExtra(QuranDownloadService.EXTRA_DOWNLOAD_KEY,
            PAGES_DOWNLOAD_KEY);
      intent.putExtra(QuranDownloadService.EXTRA_DOWNLOAD_TYPE,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      
      if (!force){
         // handle race condition in which we missed the error preference and
         // the broadcast - if so, just rebroadcast errors so we handle them
         intent.putExtra(QuranDownloadService.EXTRA_REPEAT_LAST_ERROR, true);
      }

      intent.setAction(QuranDownloadService.ACTION_DOWNLOAD_URL);
      startService(intent);
   }
   
   public boolean haveInternet() {
      ConnectivityManager cm = (ConnectivityManager)getSystemService(
            Context.CONNECTIVITY_SERVICE);
      if (cm != null && cm.getActiveNetworkInfo() != null) 
         return cm.getActiveNetworkInfo().isConnectedOrConnecting();
      return false;
   }
   
   private void initProgressDialog(){
      if (mProgressDialog == null){
         mProgressDialog = new ProgressDialog(this);
         mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
         mProgressDialog.setCancelable(false);
         mProgressDialog.setTitle(R.string.downloading_title);
         mProgressDialog.setMessage(getString(R.string.downloading_message));
         mProgressDialog.setIndeterminate(true);
      }
   }
   
   private void promptForDownload(){
      AlertDialog.Builder dialog = new AlertDialog.Builder(this);
      dialog.setMessage(R.string.downloadPrompt);
      dialog.setCancelable(false);
      dialog.setPositiveButton(R.string.downloadPrompt_ok,
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            mSharedPreferences.edit().putBoolean(
                    ApplicationConstants.PREF_SHOULD_FETCH_PAGES, true)
                    .commit();
            downloadQuranImages(true);
         }
      });

      dialog.setNegativeButton(R.string.downloadPrompt_no, 
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.dismiss();
            mPromptForDownloadDialog = null;
            runListView();
         }
      });

      mPromptForDownloadDialog = dialog.create();
      mPromptForDownloadDialog.setTitle(R.string.downloadPrompt_title);
      mPromptForDownloadDialog.show();
   }

   protected void initializeQuranScreen() {
      // get the screen size
      WindowManager w = getWindowManager();
      Display d = w.getDefaultDisplay();
      int width = d.getWidth();
      int height = d.getHeight();
      QuranScreenInfo.initialize(width, height);
      QuranDataService.qsi = QuranScreenInfo.getInstance();
   }

   protected void runListView(){
      cleanup();
      Intent i = new Intent(this, QuranActivity.class);
      startActivity(i);
      finish();
   }
}
