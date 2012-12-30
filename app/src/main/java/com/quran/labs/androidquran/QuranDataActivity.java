package com.quran.labs.androidquran;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import java.util.Date;

public class QuranDataActivity extends SherlockActivity implements
        DefaultDownloadReceiver.SimpleDownloadListener {

   public static final String TAG =
           "com.quran.labs.androidquran.QuranDataActivity";
   public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";

   private boolean mIsPaused = false;
   private AsyncTask<Void, Void, Boolean> mCheckPagesTask;
   private AlertDialog mErrorDialog = null;
   private AlertDialog mPromptForDownloadDialog = null;
   private SharedPreferences mSharedPreferences = null;
   private DefaultDownloadReceiver mDownloadReceiver = null;

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
      requestWindowFeature(Window.FEATURE_NO_TITLE);

      super.onCreate(savedInstanceState);
      setContentView(R.layout.splash_screen);

      /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
       */

      initializeQuranScreen();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());
   }
   
   @Override
   protected void onResume(){
      super.onResume();
      mIsPaused = false;
      mDownloadReceiver = new DefaultDownloadReceiver(this,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
      LocalBroadcastManager.getInstance(this).registerReceiver(
            mDownloadReceiver,
            new IntentFilter(action));
      mDownloadReceiver.setListener(this);

      // check whether or not we need to download
      mCheckPagesTask = new CheckPagesAsyncTask();
      mCheckPagesTask.execute();
   }
   
   @Override
   protected void onPause() {
      mIsPaused = true;
      mDownloadReceiver.setListener(null);
      LocalBroadcastManager.getInstance(this).
              unregisterReceiver(mDownloadReceiver);
      mDownloadReceiver = null;

      if (mPromptForDownloadDialog != null){
         mPromptForDownloadDialog.dismiss();
         mPromptForDownloadDialog = null;
      }
      
      if (mErrorDialog != null){
         mErrorDialog.dismiss();
         mErrorDialog = null;
      }
      
      super.onPause();
   }

   @Override
   public void handleDownloadSuccess(){
      mSharedPreferences.edit()
         .remove(Constants.PREF_SHOULD_FETCH_PAGES).commit();
      runListView();
   }

   @Override
   public void handleDownloadFailure(int errId){
      if (mErrorDialog != null && mErrorDialog.isShowing()){
         return;
      }
      
      showFatalErrorDialog(errId);
   }
   
   private void showFatalErrorDialog(int errorId){
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
                    Constants.PREF_SHOULD_FETCH_PAGES, false)
                    .commit();
            runListView();
         }
      });
      
      mErrorDialog = builder.create();
      mErrorDialog.show();
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
         // intentionally not sleeping because waiting
         // for the splash screen is not cool.
         QuranFileUtils.migrateAudio(QuranDataActivity.this);
         return QuranFileUtils.getQuranDirectory() != null &&
                QuranFileUtils.haveAllImages();
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         mCheckPagesTask = null;
         if (mIsPaused){ return; }
                  
         if (result == null || !result){
            String lastErrorItem = mSharedPreferences.getString(
                        QuranDownloadService.PREF_LAST_DOWNLOAD_ITEM, "");
            if (PAGES_DOWNLOAD_KEY.equals(lastErrorItem)){
               int lastError = mSharedPreferences.getInt(
                     QuranDownloadService.PREF_LAST_DOWNLOAD_ERROR, 0);
               int errorId = ServiceIntentHelper
                       .getErrorResourceFromErrorCode(lastError, false);
               showFatalErrorDialog(errorId);
            }
            else if (mSharedPreferences.getBoolean(
                    Constants.PREF_SHOULD_FETCH_PAGES, false)){
               downloadQuranImages(false);
            }
            else {
               promptForDownload();
            }
         }
         else {
            long time = mSharedPreferences.getLong(
                    Constants.PREF_LAST_UPDATED_TRANSLATIONS, 0);
            Date now = new Date();
            Log.d(TAG, "checking whether we should update translations..");
            if (now.getTime() - time > Constants.TRANSLATION_REFRESH_TIME){
               Log.d(TAG, "updating translations list...");
               Intent intent = new Intent(QuranDataActivity.this,
                       QuranDownloadService.class);
               intent.setAction(
                       QuranDownloadService.ACTION_CHECK_TRANSLATIONS);
               startService(intent);
            }
            runListView();
         }
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
      if (mDownloadReceiver != null &&
              mDownloadReceiver.didReceieveBroadcast() && !force){ return; }
      if (mIsPaused){ return; }
      
      String url = QuranFileUtils.getZipFileUrl();
      String destination = QuranFileUtils.getQuranBaseDirectory();
      
      // start service
      Intent intent = ServiceIntentHelper.getDownloadIntent(this, url,
              destination, getString(R.string.app_name), PAGES_DOWNLOAD_KEY,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      
      if (!force){
         // handle race condition in which we missed the error preference and
         // the broadcast - if so, just rebroadcast errors so we handle them
         intent.putExtra(QuranDownloadService.EXTRA_REPEAT_LAST_ERROR, true);
      }

      startService(intent);
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
                    Constants.PREF_SHOULD_FETCH_PAGES, true)
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
   }

   protected void runListView(boolean showTranslations){
      Intent i = new Intent(this, QuranActivity.class);
      if (showTranslations){
         i.putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, true);
      }
      startActivity(i);
      finish();
   }

   protected void runListView(){
      boolean value = (mSharedPreferences.getBoolean(
              Constants.PREF_HAVE_UPDATED_TRANSLATIONS, false));
      runListView(value);
   }
}
