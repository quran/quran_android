package com.quran.labs.androidquran;

import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.service.util.DefaultDownloadReceiver;
import com.quran.labs.androidquran.service.util.ServiceIntentHelper;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.widgets.QuranMaxImageView;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.Date;

public class QuranDataActivity extends ActionBarActivity implements
        DefaultDownloadReceiver.SimpleDownloadListener {

   public static final String TAG =
           "com.quran.labs.androidquran.QuranDataActivity";
   public static final String PAGES_DOWNLOAD_KEY = "PAGES_DOWNLOAD_KEY";
   private static final int MSG_REFRESH_MAX_HEIGHT = 1;

   private boolean mIsPaused = false;
   private AsyncTask<Void, Void, Boolean> mCheckPagesTask;
   private AlertDialog mErrorDialog = null;
   private AlertDialog mPromptForDownloadDialog = null;
   private SharedPreferences mSharedPreferences = null;
   private DefaultDownloadReceiver mDownloadReceiver = null;
   private boolean mNeedPortraitImages = false;
   private boolean mNeedLandscapeImages = false;
   private String mPatchUrl;
   private int mRefreshHeightTries;
   private QuranMaxImageView mSplashView;

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.splash_screen);

      mSplashView = (QuranMaxImageView)findViewById(R.id.splashview);
      if (Build.VERSION.SDK_INT >= 14){
        setSplashViewHardwareAcceleratedICS();
      }

      if (mSplashView != null){
         try {
            mSplashView.setImageResource(R.drawable.splash);
         }
         catch (OutOfMemoryError error){
            mSplashView.setBackgroundColor(Color.BLACK);
         }
      }

      /*
        // remove files for debugging purposes
        QuranUtils.debugRmDir(QuranUtils.getQuranBaseDirectory(), false);
        QuranUtils.debugLsDir(QuranUtils.getQuranBaseDirectory());
       */

      initializeQuranScreen();
      mSharedPreferences = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());

      // one time upgrade to v2.4.3
      if (!mSharedPreferences.contains(Constants.PREF_UPGRADE_TO_243)){
         String baseDir = QuranFileUtils.getQuranBaseDirectory(this);
         if (baseDir != null){
            baseDir = baseDir + File.separator;
            try {
               File f = new File(baseDir);
               if (f.exists() && f.isDirectory()){
                  String[] files = f.list();
                  if (files != null){
                     for (String file : files){
                        if (file.endsWith(".part")){
                           try {
                              new File(baseDir + file).delete();
                           }
                           catch (Exception e){}
                        }
                     }
                  }
               }
            }
            catch (Exception e){
            }
         }

         // update night mode preference and mark that we upgraded to 2.4.2ts
         mSharedPreferences.edit()
                 .putInt(Constants.PREF_NIGHT_MODE_TEXT_BRIGHTNESS,
                         Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS)
                 .remove(Constants.PREF_UPGRADE_TO_242)
                 .putBoolean(Constants.PREF_UPGRADE_TO_243, true).commit();
      }
   }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
  private void setSplashViewHardwareAcceleratedICS() {
    // actually requires 11+, but the other call we need
    // for getting max bitmap height requires 14+
    mSplashView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
  }
   
   @Override
   protected void onResume(){
      super.onResume();

      mIsPaused = false;
      mDownloadReceiver = new DefaultDownloadReceiver(this,
              QuranDownloadService.DOWNLOAD_TYPE_PAGES);
      mDownloadReceiver.setCanCancelDownload(true);
      String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
      LocalBroadcastManager.getInstance(this).registerReceiver(
            mDownloadReceiver,
            new IntentFilter(action));
      mDownloadReceiver.setListener(this);

      if (mSharedPreferences.getInt(
          Constants.PREF_MAX_BITMAP_HEIGHT, -1) == -1){
        if (Build.VERSION.SDK_INT >= 14){
          int height = mSplashView.getMaxBitmapHeight();
          if (height == -1){
            Log.d(TAG, "retrying to get max height in 500...");
            mHandler.sendEmptyMessageDelayed(MSG_REFRESH_MAX_HEIGHT, 500);
            return;
          }

          Log.d(TAG, "got max height height of " + height);
          mSharedPreferences.edit().putInt(
              Constants.PREF_MAX_BITMAP_HEIGHT, height).commit();
          QuranScreenInfo.getInstance().setBitmapMaxHeight(height);
        }
      }

      // check whether or not we need to download
      mCheckPagesTask = new CheckPagesAsyncTask(this);
      mCheckPagesTask.execute();
   }
   
   @Override
   protected void onPause() {
      // one more attempt to get the max height if we
      // haven't gotten it already...
      if (mSharedPreferences.getInt(
          Constants.PREF_MAX_BITMAP_HEIGHT, -1) == -1){
        if (Build.VERSION.SDK_INT >= 14){
          int height = mSplashView.getMaxBitmapHeight();
          if (height > 0){
            Log.d(TAG, "got max height height of " + height);
            mSharedPreferences.edit().putInt(
              Constants.PREF_MAX_BITMAP_HEIGHT, height).commit();
            QuranScreenInfo.getInstance().setBitmapMaxHeight(height);
          }
        }
      }

      mIsPaused = true;
      if (mDownloadReceiver != null) {
        mDownloadReceiver.setListener(null);
        LocalBroadcastManager.getInstance(this).
            unregisterReceiver(mDownloadReceiver);
        mDownloadReceiver = null;
      }

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

   private Handler mHandler = new Handler(){
     @Override
     public void handleMessage(Message msg) {
       if (msg.what == MSG_REFRESH_MAX_HEIGHT){
         if (mSplashView == null || isFinishing() || mIsPaused){
           return;
         }

         int height = mSplashView.getMaxBitmapHeight();
         if (height > -1){
           android.util.Log.d(TAG, "in handler, got max height: " + height);
           mSharedPreferences.edit().putInt(
               Constants.PREF_MAX_BITMAP_HEIGHT, height).commit();
           QuranScreenInfo.getInstance().setBitmapMaxHeight(height);
           // check whether or not we need to download
           if (!mIsPaused) {
             mCheckPagesTask = new CheckPagesAsyncTask(QuranDataActivity.this);
             mCheckPagesTask.execute();
           }
           return;
         }

         mRefreshHeightTries++;
         if (mRefreshHeightTries == 5){
           android.util.Log.d(TAG, "giving up on getting the max height...");
           if (!mIsPaused) {
             mCheckPagesTask = new CheckPagesAsyncTask(QuranDataActivity.this);
             mCheckPagesTask.execute();
           }
         }
         else {
           android.util.Log.d(TAG, "trying to get the max height in a sec...");
           mHandler.sendEmptyMessageDelayed(MSG_REFRESH_MAX_HEIGHT, 1000);
         }
       }
     }
   };

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
      private final Context mAppContext;
      public CheckPagesAsyncTask(Context context) {
        mAppContext = context.getApplicationContext();
      }

      @Override
      protected Boolean doInBackground(Void... params) {
         // intentionally not sleeping because waiting
         // for the splash screen is not cool.
         QuranFileUtils.migrateAudio(mAppContext);

         if (QuranScreenInfo.getInstance().isTablet(mAppContext)){
            boolean haveLandscape = QuranFileUtils.haveAllImages(mAppContext,
                    QuranScreenInfo.getInstance().getTabletWidthParam());
            boolean havePortrait = QuranFileUtils.haveAllImages(mAppContext,
                    QuranScreenInfo.getInstance().getWidthParam());
            mNeedPortraitImages = !havePortrait;
            mNeedLandscapeImages = !haveLandscape;
            return haveLandscape && havePortrait;
         }
         else {
            boolean haveAll = QuranFileUtils.haveAllImages(mAppContext,
                        QuranScreenInfo.getInstance().getWidthParam());
            mNeedPortraitImages = !haveAll;
            mNeedLandscapeImages = false;
            return haveAll;
         }
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         mCheckPagesTask = null;
         mPatchUrl = null;
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
            // force a check for the images version 3, if it's not
            // there, download the patch.
            QuranScreenInfo qsi = QuranScreenInfo.getInstance();
            String widthParam = qsi.getWidthParam();
            if (qsi.isTablet(QuranDataActivity.this)){
               String tabletWidth = qsi.getTabletWidthParam();
               if ((!QuranFileUtils.isVersion(QuranDataActivity.this,
                       widthParam, 3)) ||
                   (!QuranFileUtils.isVersion(QuranDataActivity.this,
                       tabletWidth, 3))){
                  widthParam += tabletWidth;
                  // get patch for both landscape/portrait tablet images
                  mPatchUrl = QuranFileUtils.getPatchFileUrl(widthParam, 3);
                  promptForDownload();
                  return;
               }
            }
            else if (!QuranFileUtils.isVersion(QuranDataActivity.this,
                    widthParam, 3)){
               // explicitly check whether we need to fix the images
               mPatchUrl = QuranFileUtils.getPatchFileUrl(widthParam, 3);
               promptForDownload();
               return;
            }

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

      QuranScreenInfo qsi = QuranScreenInfo.getInstance();
      
      String url;
      if (mNeedPortraitImages && !mNeedLandscapeImages){
         // phone (and tablet when upgrading on some devices, ex n10)
         url = QuranFileUtils.getZipFileUrl();
      }
      else if (mNeedLandscapeImages && !mNeedPortraitImages){
         // tablet (when upgrading from pre-tablet on some devices, ex n7).
         url = QuranFileUtils.getZipFileUrl(qsi.getTabletWidthParam());
      }
      else {
         // new tablet installation - if both image sets are the same
         // size, then just get the correct one only
         if (qsi.getTabletWidthParam().equals(qsi.getWidthParam())){
            url = QuranFileUtils.getZipFileUrl();
         }
         else {
            // otherwise download one zip with both image sets
            String widthParam = qsi.getWidthParam() +
                    qsi.getTabletWidthParam();
            url = QuranFileUtils.getZipFileUrl(widthParam);
         }
      }

      // if we have a patch url, just use that
      if (!TextUtils.isEmpty(mPatchUrl)){
         url = mPatchUrl;
      }

      String destination = QuranFileUtils.getQuranBaseDirectory(
              QuranDataActivity.this);
      
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
      int message = R.string.downloadPrompt;
      if (QuranScreenInfo.getInstance().isTablet(this) &&
              (mNeedPortraitImages != mNeedLandscapeImages)){
         message = R.string.downloadTabletPrompt;
      }

      if (!TextUtils.isEmpty(mPatchUrl)){
         // patch message if applicable
         message = R.string.downloadImportantPrompt;
      }

      AlertDialog.Builder dialog = new AlertDialog.Builder(this);
      dialog.setMessage(message);
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
      QuranScreenInfo.getOrMakeInstance(this);
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
