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

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;

public class DefaultDownloadReceiver extends BroadcastReceiver {

  private int mDownloadType = -1;
  private SimpleDownloadListener mListener;
  private ProgressDialog mProgressDialog;
  private Context mContext = null;
  private boolean mDidReceiveBroadcast;
  private boolean mCanCancelDownload;

  private static class DownloadReceiverHandler extends Handler {
    private final WeakReference<DefaultDownloadReceiver> mReceiverRef;

    public DownloadReceiverHandler(DefaultDownloadReceiver receiver) {
      mReceiverRef = new WeakReference<>(receiver);
    }

    @Override
    public void handleMessage(Message msg) {
      final DefaultDownloadReceiver receiver = mReceiverRef.get();
      if (receiver == null || receiver.mListener == null) {
        return;
      }

      Intent intent = (Intent) msg.obj;
      String state = intent.getStringExtra(
          QuranDownloadNotifier.ProgressIntent.STATE);
      switch (state) {
        case QuranDownloadNotifier.ProgressIntent.STATE_SUCCESS:
          receiver.dismissDialog();
          receiver.mListener.handleDownloadSuccess();
          break;
        case QuranDownloadNotifier.ProgressIntent.STATE_ERROR: {
          int msgId = ServiceIntentHelper.
              getErrorResourceFromDownloadIntent(intent, true);
          receiver.dismissDialog();
          receiver.mListener.handleDownloadFailure(msgId);
          break;
        }
        case QuranDownloadNotifier.ProgressIntent.STATE_DOWNLOADING: {
          int progress = intent.getIntExtra(
              QuranDownloadNotifier.ProgressIntent.PROGRESS, -1);
          long downloadedSize = intent.getLongExtra(
              QuranDownloadNotifier.ProgressIntent.DOWNLOADED_SIZE, -1);
          long totalSize = intent.getLongExtra(
              QuranDownloadNotifier.ProgressIntent.TOTAL_SIZE, -1);
          int sura = intent.getIntExtra(QuranDownloadNotifier.ProgressIntent.SURA, -1);
          int ayah = intent.getIntExtra(QuranDownloadNotifier.ProgressIntent.AYAH, -1);
          if (receiver.mListener instanceof DownloadListener) {
            ((DownloadListener) receiver.mListener).updateDownloadProgress(progress,
                downloadedSize, totalSize);
          } else {
            receiver.updateDownloadProgress(progress, downloadedSize, totalSize, sura, ayah);
          }
          break;
        }
        case QuranDownloadNotifier.ProgressIntent.STATE_PROCESSING: {
          int progress = intent.getIntExtra(
              QuranDownloadNotifier.ProgressIntent.PROGRESS, -1);
          int processedFiles = intent.getIntExtra(
              QuranDownloadNotifier.ProgressIntent.PROCESSED_FILES, 0);
          int totalFiles = intent.getIntExtra(
              QuranDownloadNotifier.ProgressIntent.TOTAL_FILES, 0);
          if (receiver.mListener instanceof DownloadListener) {
            ((DownloadListener) receiver.mListener).updateProcessingProgress(progress,
                processedFiles, totalFiles);
          } else {
            receiver.updateProcessingProgress(progress, processedFiles, totalFiles);
          }
          break;
        }
        case QuranDownloadNotifier.ProgressIntent
            .STATE_ERROR_WILL_RETRY: {
          int msgId = ServiceIntentHelper.
              getErrorResourceFromDownloadIntent(intent, true);
          if (receiver.mListener instanceof DownloadListener) {
            ((DownloadListener) receiver.mListener)
                .handleDownloadTemporaryError(msgId);
          } else {
            receiver.handleNonFatalError(msgId);
          }
          break;
        }
      }
    }
  }

  private final Handler mHandler;

  public DefaultDownloadReceiver(Context context, int downloadType) {
    mContext = context;
    mDownloadType = downloadType;
    mHandler = new DownloadReceiverHandler(this);
  }

  public void setCanCancelDownload(boolean canCancel) {
    mCanCancelDownload = canCancel;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }
    int type = intent.getIntExtra(
        QuranDownloadNotifier.ProgressIntent.DOWNLOAD_TYPE,
        QuranDownloadService.DOWNLOAD_TYPE_UNDEF);
    String state = intent.getStringExtra(
        QuranDownloadNotifier.ProgressIntent.STATE);

    if (mDownloadType != type || state == null) {
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

  private void dismissDialog() {
    if (mProgressDialog != null) {
      try {
        mProgressDialog.dismiss();
      } catch (Exception e) {
        // no op
      }
      mProgressDialog = null;
    }
  }

  public boolean didReceiveBroadcast() {
    return mDidReceiveBroadcast;
  }

  private void makeAndShowProgressDialog() {
    makeProgressDialog();
    if (mProgressDialog != null) {
      mProgressDialog.show();
    }
  }

  private void makeProgressDialog() {
    if (mProgressDialog == null) {
      mProgressDialog = new ProgressDialog(mContext);
      mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      mProgressDialog.setCancelable(mCanCancelDownload);
      if (mCanCancelDownload) {
        mProgressDialog.setOnCancelListener(
            new DialogInterface.OnCancelListener() {
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

  private void cancelDownload() {
    Intent i = new Intent(mContext, QuranDownloadService.class);
    i.setAction(QuranDownloadService.ACTION_CANCEL_DOWNLOADS);
    mContext.startService(i);
  }

  private void updateDownloadProgress(int progress,
      long downloadedSize, long totalSize, int currentSura, int currentAyah) {
    if (mProgressDialog == null) {
      makeAndShowProgressDialog();
    }
    if (mProgressDialog != null) {
      if (!mProgressDialog.isShowing()) {
        mProgressDialog.show();
      }
      if (progress == -1) {
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
      String downloaded = mContext.getString(R.string.prefs_megabytes_str,
          df.format((1.0 * downloadedSize / mb)));
      String total = mContext.getString(R.string.prefs_megabytes_str,
          df.format((1.0 * totalSize / mb)));

      String message;
      if (currentSura < 1) {
        message = String.format(
            mContext.getString(R.string.download_progress),
            downloaded, total);
      } else if (currentAyah <= 0) {
        message = String.format(
            mContext.getString(R.string.download_sura_progress),
            downloaded, total, currentSura);
      } else {
        message = String.format(mContext.getString(R.string.download_sura_ayah_progress),
            currentSura, currentAyah);
      }
      mProgressDialog.setMessage(message);
    }
  }

  private void updateProcessingProgress(int progress,
      int processedFiles, int totalFiles) {
    if (mProgressDialog == null) {
      makeAndShowProgressDialog();
    }
    if (mProgressDialog != null) {
      if (!mProgressDialog.isShowing()) {
        mProgressDialog.show();
      }
      if (progress == -1) {
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

  private void handleNonFatalError(int msgId) {
    if (mProgressDialog == null) {
      makeAndShowProgressDialog();
    }
    if (mProgressDialog != null) {
      if (!mProgressDialog.isShowing()) {
        mProgressDialog.show();
      }
      mProgressDialog.setMessage(mContext.getString(msgId));
    }
  }

  public void setListener(SimpleDownloadListener listener) {
    mListener = listener;
    if (mListener == null && mProgressDialog != null) {
      mProgressDialog.dismiss();
      mProgressDialog = null;
    } else if (mListener != null) {
      makeProgressDialog();
    }
  }

  public interface SimpleDownloadListener {

    void handleDownloadSuccess();

    void handleDownloadFailure(int errId);
  }

  public interface DownloadListener extends SimpleDownloadListener {

    void updateDownloadProgress(int progress,
        long downloadedSize, long totalSize);

    void updateProcessingProgress(int progress,
        int processFiles, int totalFiles);

    void handleDownloadTemporaryError(int errorId);
  }
}
