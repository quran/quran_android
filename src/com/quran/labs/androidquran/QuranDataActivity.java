package com.quran.labs.androidquran;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockActivity;
import com.quran.labs.androidquran.service.QuranDataService;
import com.quran.labs.androidquran.service.QuranDownloadService;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;

public class QuranDataActivity extends SherlockActivity {
   
   private boolean mRegisteredReceiver = false;
   private AsyncTask<Void, Void, Boolean> mCheckPagesTask;

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState) {
      setTheme(R.style.Theme_Sherlock);
      super.onCreate(savedInstanceState);
      requestWindowFeature(Window.FEATURE_NO_TITLE);
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
      
      // check whether or not we need to download
      mCheckPagesTask = new CheckPagesAsyncTask();
      mCheckPagesTask.execute();
   }
   
   @Override
   protected void onDestroy() {
      cleanup();
      super.onDestroy();
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
      }
   };
   
   class CheckPagesAsyncTask extends AsyncTask<Void, Void, Boolean> {

      @Override
      protected Boolean doInBackground(Void... params) {
         return (QuranFileUtils.getQuranDirectory() != null) &&
                (!QuranFileUtils.haveAllImages());
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         mCheckPagesTask = null;
         
         if (result != null && result){
            promptForDownload();
         }
         else { runListView(); }
      }      
   }

   private void downloadQuranImages(){
      if (!mRegisteredReceiver){
         mRegisteredReceiver = true;
         String action = QuranDownloadService.ProgressIntent.INTENT_NAME;
         LocalBroadcastManager.getInstance(this).registerReceiver(
               mMessageReceiver,
               new IntentFilter(action));
      }
      
      String url = QuranFileUtils.getZipFileUrl();
      String destination = QuranFileUtils.getQuranBaseDirectory();
      
      Intent intent = new Intent(this, QuranDownloadService.class);
      intent.putExtra(QuranDownloadService.EXTRA_URL, url);
      intent.putExtra(QuranDownloadService.EXTRA_DESTINATION, destination);
      intent.putExtra(QuranDownloadService.EXTRA_NOTIFICATION_NAME,
            getString(R.string.app_name));
      intent.setAction(QuranDownloadService.ACTION_DOWNLOAD_URL);
      startService(intent);
   }
   
   private void promptForDownload(){
      AlertDialog.Builder dialog = new AlertDialog.Builder(this);
      dialog.setMessage(R.string.downloadPrompt);
      dialog.setCancelable(false);
      dialog.setPositiveButton(R.string.downloadPrompt_ok,
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
            downloadQuranImages();
         }
      });

      dialog.setNegativeButton(R.string.downloadPrompt_no, 
            new DialogInterface.OnClickListener() {
         public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
            runListView();
         }
      });

      AlertDialog alert = dialog.create();
      alert.setTitle(R.string.downloadPrompt_title);
      alert.show();
   }

   protected void initializeQuranScreen() {
      // get the screen size
      WindowManager w = getWindowManager();
      Display d = w.getDefaultDisplay();
      int width = d.getWidth();
      int height = d.getHeight();
      Log.d("quran", "screen size: width [" + width + "], height: [" + height + "]");
      QuranScreenInfo.initialize(width, height);
      QuranDataService.qsi = QuranScreenInfo.getInstance();
   }

   protected void runListView(){
      cleanup();
      Intent i = new Intent(this, QuranActivity.class);
      startActivity(i);
   }
}
