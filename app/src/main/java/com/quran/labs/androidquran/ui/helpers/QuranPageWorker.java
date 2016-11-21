package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;

import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.util.QuranExecutorService;
import com.quran.labs.androidquran.util.QuranPageTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class QuranPageWorker {
  private static final int MSG_IMAGE_LOADED = 1;

  private volatile static QuranPageWorker sInstance;

  private Context mContext;
  private Resources mResources;

  private static final ExecutorService sExecutorService =
      new QuranExecutorService();

  private static class WorkerHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_IMAGE_LOADED && sInstance != null) {
        sInstance.onImageLoaded((QuranPageTask.QuranTaskData) msg.obj);
      }
    }
  }

  private static final Handler sHandler = new WorkerHandler();

  public static synchronized QuranPageWorker getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new QuranPageWorker(context);
    }
    return sInstance;
  }

  public static void submitResult(QuranPageTask.QuranTaskData quranTaskData) {
    final Message message = sHandler.obtainMessage(
        MSG_IMAGE_LOADED, quranTaskData);
    sHandler.sendMessage(message);
  }

  private QuranPageWorker(Context context) {
    mContext = context.getApplicationContext();
    mResources = mContext.getResources();
  }

  public Future<?> loadPage(String widthParam, int page, AyahTracker tracker) {
    QuranPageTask task = new QuranPageTask(mContext,
        widthParam, tracker, page);
    return sExecutorService.submit(task);
  }

  // once complete, see if ImageView is still around and set bitmap.
  private void onImageLoaded(QuranPageTask.QuranTaskData quranTaskData) {
    final Response response = quranTaskData.getResponse();
    BitmapDrawable drawable = null;
    if (response != null) {
      final Bitmap bitmap = response.getBitmap();
      if (bitmap != null) {
        drawable = new BitmapDrawable(mResources, bitmap);
      }
    }

    final AyahTracker ayahTracker =
        quranTaskData.getAyahTrackerReference().get();
    if (ayahTracker != null) {
      ayahTracker.onLoadImageResponse(drawable,
          Response.lightResponse(response));
    }
  }
}
