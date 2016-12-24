package com.quran.labs.androidquran.util;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.ui.helpers.PageDownloadListener;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import okhttp3.OkHttpClient;

public class QuranPageTask implements Runnable {
  private static final String TAG = "QuranPageTask";

  private int pageNumber;
  private String widthParam;
  private Context context;
  private final WeakReference<PageDownloadListener> pageDownloadListenerWeakReference;

  @Inject OkHttpClient okHttpClient;

  public QuranPageTask(Context context, String widthParam,
                       PageDownloadListener listener, int page) {
    pageNumber = page;
    this.widthParam = widthParam;
    // use a WeakReference to ensure the listener can be gc
    pageDownloadListenerWeakReference = new WeakReference<>(listener);
    this.context = context.getApplicationContext();
    ((QuranApplication) this.context).getApplicationComponent().inject(this);
  }

  @Override
  public void run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

    Response response = null;
    OutOfMemoryError oom = null;

    try {
      response = QuranDisplayHelper.getQuranPage(okHttpClient, context, widthParam, pageNumber);
    } catch (OutOfMemoryError me){
      Crashlytics.log(Log.WARN, TAG,
          "out of memory exception loading page " +
              pageNumber + ", " + widthParam);
      oom = me;
    }

    if (response == null ||
        (response.getBitmap() == null &&
            response.getErrorCode() != Response.ERROR_SD_CARD_NOT_FOUND)){
      if (QuranScreenInfo.getInstance().isTablet(context)){
        Crashlytics.log(Log.WARN, TAG,
            "tablet got bitmap null, trying alternate width...");
        String param = QuranScreenInfo.getInstance().getWidthParam();
        if (param.equals(widthParam)){
          param = QuranScreenInfo.getInstance().getTabletWidthParam();
        }
        response = QuranDisplayHelper.getQuranPage(okHttpClient, context, param, pageNumber);
        if (response.getBitmap() == null){
          Crashlytics.log(Log.WARN, TAG,
              "bitmap still null, giving up... [" +
                  response.getErrorCode() + "]");
        }
      }
      Crashlytics.log(Log.WARN, TAG, "got response back as null... [" +
          (response == null ? "" : response.getErrorCode()));
    }

    if ((response == null ||
        response.getBitmap() == null) && oom != null) {
      throw oom;
    }

    if (response != null) {
      response.setPageData(pageNumber, widthParam);
    }

    final QuranTaskData data = new QuranTaskData(response, pageDownloadListenerWeakReference);
    QuranPageWorker.submitResult(data);
  }

  public static class QuranTaskData {
    private final Response response;
    private final WeakReference<PageDownloadListener> pageDownloadListenerWeakReference;

    QuranTaskData(Response response, WeakReference<PageDownloadListener> listenerReference) {
      this.response = response;
      pageDownloadListenerWeakReference = listenerReference;
    }

    public Response getResponse() {
      return response;
    }

    public WeakReference<PageDownloadListener> getAyahTrackerReference() {
      return pageDownloadListenerWeakReference;
    }
  }
}
