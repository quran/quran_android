package com.quran.labs.androidquran.service.util;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import com.quran.labs.androidquran.service.QuranDownloadService;

public class DefaultDownloadReceiver extends BroadcastReceiver {
   private int mDownloadType = -1;
   private DownloadListener mListener;

   public DefaultDownloadReceiver(int downloadType){
      mDownloadType = downloadType;
   }

   @Override
   public void onReceive(Context context, Intent intent){
      if (intent == null){ return; }
      int type = intent.getIntExtra(
              QuranDownloadService.ProgressIntent.DOWNLOAD_TYPE,
              QuranDownloadService.DOWNLOAD_TYPE_UNDEF);
      String state = intent.getStringExtra(
              QuranDownloadService.ProgressIntent.STATE);

      if (mDownloadType != type || state == null){
         return;
      }

      Message msg = mHandler.obtainMessage();
      msg.obj = intent;

      // only care about the latest download progress
      mHandler.removeCallbacksAndMessages(null);

      // send the message at the front of the queue
      mHandler.sendMessageAtFrontOfQueue(msg);
   }

   private Handler mHandler = new Handler(){
      @Override
      public void handleMessage(Message msg){
         if (mListener == null){ return; }

         Intent intent = (Intent)msg.obj;
         String state = intent.getStringExtra(
                 QuranDownloadService.ProgressIntent.STATE);
         if (QuranDownloadService.STATE_SUCCESS.equals(state)){
            mListener.handleDownloadSuccess();
         }
         else if (QuranDownloadService.STATE_ERROR.equals(state)){
            int msgId = ServiceIntentHelper.
                    getErrorResourceFromDownloadIntent(intent, true);
            mListener.handleDownloadError(msgId, false);
         }
         else if (QuranDownloadService.STATE_DOWNLOADING.equals(state)){
            int progress = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROGRESS, -1);
            long downloadedSize = intent.getLongExtra(
                    QuranDownloadService.ProgressIntent.DOWNLOADED_SIZE, -1);
            long totalSize = intent.getLongExtra(
                    QuranDownloadService.ProgressIntent.TOTAL_SIZE, -1);
            mListener.updateDownloadProgress(progress,
                    downloadedSize, totalSize);
         }
         else if (QuranDownloadService.STATE_PROCESSING.equals(state)){
            int progress = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROGRESS, -1);
            int processedFiles = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROCESSED_FILES, 0);
            int totalFiles = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.TOTAL_FILES, 0);
            mListener.updateProcessingProgress(progress,
                    processedFiles, totalFiles);
         }
         else if (QuranDownloadService
                 .STATE_ERROR_WILL_RETRY.equals(state)){
            int msgId = ServiceIntentHelper.
                    getErrorResourceFromDownloadIntent(intent, true);
            mListener.handleDownloadError(msgId, true);
         }
      }
   };

   public void setListener(DownloadListener listener){
      mListener = listener;
   }

   public interface DownloadListener {
      public void updateDownloadProgress(int progress,
                                         long downloadedSize, long totalSize);
      public void updateProcessingProgress(int progress,
                                           int processFiles, int totalFiles);
      public void handleDownloadError(int errorId, boolean willRetry);
      public void handleDownloadSuccess();
   }
}
