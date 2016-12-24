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

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class QuranPageWorker {
  private static final int MSG_IMAGE_LOADED = 1;

  private static final WorkerHandler handler = new WorkerHandler();
  private static final ExecutorService executorService = new QuranExecutorService();

  private final Context appContext;
  private final Resources resources;

  @Inject
  QuranPageWorker(Context context) {
    appContext = context.getApplicationContext();
    resources = appContext.getResources();
    handler.setQuranPageWorker(this);
  }

  public static void submitResult(QuranPageTask.QuranTaskData quranTaskData) {
    final Message message = handler.obtainMessage(MSG_IMAGE_LOADED, quranTaskData);
    handler.sendMessage(message);
  }

  public Future<?> loadPage(String widthParam, int page, PageDownloadListener listener) {
    QuranPageTask task = new QuranPageTask(appContext, widthParam, listener, page);
    return executorService.submit(task);
  }

  // once complete, see if ImageView is still around and set bitmap.
  private void onImageLoaded(QuranPageTask.QuranTaskData quranTaskData) {
    final Response response = quranTaskData.getResponse();
    BitmapDrawable drawable = null;
    if (response != null) {
      final Bitmap bitmap = response.getBitmap();
      if (bitmap != null) {
        drawable = new BitmapDrawable(resources, bitmap);
      }
    }

    final PageDownloadListener pageDownloadListener = quranTaskData.getAyahTrackerReference().get();
    if (pageDownloadListener != null) {
      pageDownloadListener.onLoadImageResponse(drawable, Response.lightResponse(response));
    }
  }

  private static class WorkerHandler extends Handler {
    private WeakReference<QuranPageWorker> workerReference = new WeakReference<>(null);

    void setQuranPageWorker(QuranPageWorker worker) {
      workerReference = new WeakReference<>(worker);
    }

    @Override
    public void handleMessage(Message msg) {
      QuranPageWorker worker = workerReference.get();
      if (msg.what == MSG_IMAGE_LOADED && worker != null) {
        worker.onImageLoaded((QuranPageTask.QuranTaskData) msg.obj);
      }
    }
  }

}
