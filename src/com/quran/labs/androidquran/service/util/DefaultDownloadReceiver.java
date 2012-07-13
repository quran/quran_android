package com.quran.labs.androidquran.service.util;

import java.text.DecimalFormat;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.service.QuranDownloadService;

public class DefaultDownloadReceiver extends BroadcastReceiver {
   private int mDownloadType = -1;
   private SimpleDownloadListener mListener;
   private ProgressDialog mProgressDialog = null;
   private Context mContext = null;
   private boolean mDidReceiveBroadcast = false;

   public DefaultDownloadReceiver(Context context, int downloadType){
      mContext = context;
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

      mDidReceiveBroadcast = true;
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
            if (mProgressDialog != null){
               mProgressDialog.dismiss();
               mProgressDialog = null;
            }
            mListener.handleDownloadSuccess();
         }
         else if (QuranDownloadService.STATE_ERROR.equals(state)){
            int msgId = ServiceIntentHelper.
                    getErrorResourceFromDownloadIntent(intent, true);
            if (mProgressDialog != null){
               mProgressDialog.dismiss();
               mProgressDialog = null;
            }
            mListener.handleDownloadFailure(msgId);
         }
         else if (QuranDownloadService.STATE_DOWNLOADING.equals(state)){
            int progress = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROGRESS, -1);
            long downloadedSize = intent.getLongExtra(
                    QuranDownloadService.ProgressIntent.DOWNLOADED_SIZE, -1);
            long totalSize = intent.getLongExtra(
                    QuranDownloadService.ProgressIntent.TOTAL_SIZE, -1);
            if (mListener instanceof DownloadListener){
               ((DownloadListener)mListener).updateDownloadProgress(progress,
                    downloadedSize, totalSize);
            }
            else {
               updateDownloadProgress(progress, downloadedSize, totalSize);
            }
         }
         else if (QuranDownloadService.STATE_PROCESSING.equals(state)){
            int progress = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROGRESS, -1);
            int processedFiles = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.PROCESSED_FILES, 0);
            int totalFiles = intent.getIntExtra(
                    QuranDownloadService.ProgressIntent.TOTAL_FILES, 0);
            if (mListener instanceof DownloadListener){
               ((DownloadListener)mListener).updateProcessingProgress(progress,
                    processedFiles, totalFiles);
            }
            else {
               updateProcessingProgress(progress, processedFiles, totalFiles);
            }
         }
         else if (QuranDownloadService
                 .STATE_ERROR_WILL_RETRY.equals(state)){
            int msgId = ServiceIntentHelper.
                    getErrorResourceFromDownloadIntent(intent, true);
            if (mListener instanceof DownloadListener){
               ((DownloadListener)mListener)
                       .handleDownloadTemporaryError(msgId);
            }
            else { handleNonFatalError(msgId); }
         }
      }
   };

   public boolean didReceieveBroadcast(){
      return mDidReceiveBroadcast;
   }

   private void makeAndShowProgressDialog(){
      makeProgressDialog();
      if (mProgressDialog != null){ mProgressDialog.show(); }
   }

   private void makeProgressDialog(){
      if (mProgressDialog == null){
         mProgressDialog = new ProgressDialog(mContext);
         mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
         mProgressDialog.setCancelable(false);
         mProgressDialog.setTitle(R.string.downloading_title);
         mProgressDialog.setMessage(mContext.getString(
                 R.string.downloading_message));
      }
   }

   private void updateDownloadProgress(int progress,
                                       long downloadedSize, long totalSize){
      if (mProgressDialog == null){ makeAndShowProgressDialog(); }
      if (mProgressDialog != null){
         if (!mProgressDialog.isShowing()){ mProgressDialog.show(); }
         if (progress == -1){
            int titleId = R.string.downloading_title;
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage(mContext.getString(titleId));
            return;
         }

         mProgressDialog.setIndeterminate(false);
         mProgressDialog.setMax(100);
         mProgressDialog.setProgress(progress);

         DecimalFormat df = new DecimalFormat("###.00");
         int mb = 1024 * 1024;
         String downloaded = df.format((1.0 * downloadedSize / mb)) + " MB";
         String total = df.format((1.0 * totalSize / mb)) + " MB";

         String message = String.format(
                 mContext.getString(R.string.download_progress),
                 downloaded, total);
         mProgressDialog.setMessage(message);
      }
   }

   private void updateProcessingProgress(int progress,
                                         int processedFiles, int totalFiles){
      if (mProgressDialog == null){ makeAndShowProgressDialog(); }
      if (mProgressDialog != null){
         if (!mProgressDialog.isShowing()){ mProgressDialog.show(); }
         if (progress == -1){
            int titleId = R.string.extracting_title;
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setMessage(mContext.getString(titleId));
            return;
         }

         mProgressDialog.setIndeterminate(false);
         mProgressDialog.setMax(100);
         mProgressDialog.setProgress(progress);

         mProgressDialog.setMessage(mContext.getString(R.string.extracting_title));

         String message = String.format(
                 mContext.getString(R.string.process_progress),
                 processedFiles, totalFiles);
         mProgressDialog.setMessage(message);
      }
   }

   private void handleNonFatalError(int msgId){
      if (mProgressDialog == null){ makeAndShowProgressDialog(); }
      if (mProgressDialog != null){
         if (!mProgressDialog.isShowing()){ mProgressDialog.show(); }
         mProgressDialog.setMessage(mContext.getString(msgId));
      }
   }

   public void setListener(SimpleDownloadListener listener){
      mListener = listener;
      if (mListener == null && mProgressDialog != null){
         mProgressDialog.dismiss();
         mProgressDialog = null;
      }
      else if (mListener != null &&
              mListener instanceof SimpleDownloadListener){
         makeProgressDialog();
      }
   }

   public interface SimpleDownloadListener {
      public void handleDownloadSuccess();
      public void handleDownloadFailure(int errId);
   }

   public interface DownloadListener extends SimpleDownloadListener {
      public void updateDownloadProgress(int progress,
                                         long downloadedSize, long totalSize);
      public void updateProcessingProgress(int progress,
                                           int processFiles, int totalFiles);
      public void handleDownloadTemporaryError(int errorId);
   }
}
