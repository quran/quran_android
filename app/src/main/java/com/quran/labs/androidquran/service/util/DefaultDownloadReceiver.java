package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.service.QuranDownloadService;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import java.text.DecimalFormat;

public class DefaultDownloadReceiver extends BroadcastReceiver {
   private int mDownloadType = -1;
   private SimpleDownloadListener mListener;
   private ProgressDialog mProgressDialog = null;
   private Context mContext = null;
   private boolean mDidReceiveBroadcast = false;
   private boolean mCanCancelDownload = false;

   public DefaultDownloadReceiver(Context context, int downloadType){
      mContext = context;
      mDownloadType = downloadType;
   }

   public void setCanCancelDownload(boolean canCancel){
      mCanCancelDownload = canCancel;
   }

   @Override
   public void onReceive(Context context, Intent intent){
      if (intent == null){ return; }
      int type = intent.getIntExtra(
              QuranDownloadNotifier.ProgressIntent.DOWNLOAD_TYPE,
              QuranDownloadService.DOWNLOAD_TYPE_UNDEF);
      String state = intent.getStringExtra(
          QuranDownloadNotifier.ProgressIntent.STATE);

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
             QuranDownloadNotifier.ProgressIntent.STATE);
         if (QuranDownloadNotifier.ProgressIntent.STATE_SUCCESS.equals(state)){
            dismissDialog();
            mListener.handleDownloadSuccess();
         }
         else if (QuranDownloadNotifier.ProgressIntent.STATE_ERROR.equals(state)){
            int msgId = ServiceIntentHelper.
                    getErrorResourceFromDownloadIntent(intent, true);
            dismissDialog();
            mListener.handleDownloadFailure(msgId);
         }
         else if (QuranDownloadNotifier.ProgressIntent.STATE_DOWNLOADING.equals(state)){
            int progress = intent.getIntExtra(
                QuranDownloadNotifier.ProgressIntent.PROGRESS, -1);
            long downloadedSize = intent.getLongExtra(
                QuranDownloadNotifier.ProgressIntent.DOWNLOADED_SIZE, -1);
            long totalSize = intent.getLongExtra(
                QuranDownloadNotifier.ProgressIntent.TOTAL_SIZE, -1);
            if (mListener instanceof DownloadListener){
               ((DownloadListener)mListener).updateDownloadProgress(progress,
                   downloadedSize, totalSize);
            }
            else {
               updateDownloadProgress(progress, downloadedSize, totalSize);
            }
         }
         else if (QuranDownloadNotifier.ProgressIntent.STATE_PROCESSING.equals(state)){
            int progress = intent.getIntExtra(
                QuranDownloadNotifier.ProgressIntent.PROGRESS, -1);
            int processedFiles = intent.getIntExtra(
                QuranDownloadNotifier.ProgressIntent.PROCESSED_FILES, 0);
            int totalFiles = intent.getIntExtra(
                QuranDownloadNotifier.ProgressIntent.TOTAL_FILES, 0);
            if (mListener instanceof DownloadListener){
               ((DownloadListener)mListener).updateProcessingProgress(progress,
                   processedFiles, totalFiles);
            }
            else {
               updateProcessingProgress(progress, processedFiles, totalFiles);
            }
         }
         else if (QuranDownloadNotifier.ProgressIntent
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

   private void dismissDialog(){
     if (mProgressDialog != null){
       try {
         mProgressDialog.dismiss();
       }
       catch (Exception e){
       }
       mProgressDialog = null;
     }
   }

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
         mProgressDialog.setCancelable(mCanCancelDownload);
         if (mCanCancelDownload){
            mProgressDialog.setOnCancelListener(
                    new DialogInterface.OnCancelListener(){
               @Override
               public void onCancel(DialogInterface dialog) {
                  cancelDownload();
               }
            });
            
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    mContext.getString(R.string.cancel),
                    new DialogInterface.OnClickListener() {
               @Override
               public void onClick(DialogInterface dialog, int which) {
                  cancelDownload();
               }
            });
         }

         mProgressDialog.setTitle(R.string.downloading_title);
         mProgressDialog.setMessage(mContext.getString(
                 R.string.downloading_message));
      }
   }

   private void cancelDownload(){
      Intent i = new Intent(mContext, QuranDownloadService.class);
      i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS);
      mContext.startService(i);
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
      else if (mListener != null){
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
