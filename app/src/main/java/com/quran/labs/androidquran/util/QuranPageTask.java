package com.quran.labs.androidquran.util;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.lang.ref.WeakReference;

public class QuranPageTask implements Runnable {
  private static final String TAG = "QuranPageTask";

  private int mPageNumber;
  private String mWidthParam;
  private Context mContext;
  private final WeakReference<AyahTracker> mAyahTrackerWeakReference;

  public QuranPageTask(Context context, String widthParam,
      AyahTracker tracker, int page) {
    mPageNumber = page;
    mWidthParam = widthParam;
    // use a WeakReference to ensure the AyahTracker can be gc
    mAyahTrackerWeakReference = new WeakReference<>(tracker);
    mContext = context.getApplicationContext();
  }

  @Override
  public void run() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

    Response response = null;
    OutOfMemoryError oom = null;

    try {
      response = QuranDisplayHelper.getQuranPage(
          mContext, mWidthParam, mPageNumber);
    } catch (OutOfMemoryError me){
      Crashlytics.log(Log.WARN, TAG,
          "out of memory exception loading page " +
              mPageNumber + ", " + mWidthParam);
      oom = me;
    }

    if (response == null ||
        (response.getBitmap() == null &&
            response.getErrorCode() != Response.ERROR_SD_CARD_NOT_FOUND)){
      if (QuranScreenInfo.getInstance().isTablet(mContext)){
        Crashlytics.log(Log.WARN, TAG,
            "tablet got bitmap null, trying alternate width...");
        String param = QuranScreenInfo.getInstance().getWidthParam();
        if (param.equals(mWidthParam)){
          param = QuranScreenInfo.getInstance().getTabletWidthParam();
        }
        response = QuranDisplayHelper.getQuranPage(
            mContext, param, mPageNumber);
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
      response.setPageData(mPageNumber, mWidthParam);
    }

    final QuranTaskData data = new QuranTaskData(
        response, mAyahTrackerWeakReference);
    QuranPageWorker.submitResult(data);
  }

  public static class QuranTaskData {
    private final Response mResponse;
    private final WeakReference<AyahTracker> mAyahTrackerWeakReference;

    public QuranTaskData(Response response, WeakReference<AyahTracker> trackerRef) {
      mResponse = response;
      mAyahTrackerWeakReference = trackerRef;
    }

    public Response getResponse() {
      return mResponse;
    }

    public WeakReference<AyahTracker> getAyahTrackerReference() {
      return mAyahTrackerWeakReference;
    }
  }
}
