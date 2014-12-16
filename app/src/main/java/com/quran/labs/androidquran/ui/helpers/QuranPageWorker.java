package com.quran.labs.androidquran.ui.helpers;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.util.QuranExecutorService;
import com.quran.labs.androidquran.util.QuranPageTask;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class QuranPageWorker {
  private static final String TAG = "QuranPageWorker";
  private static final int MSG_IMAGE_LOADED = 1;

  private volatile static QuranPageWorker sInstance;

  private Context mContext;
  private Resources mResources;
  private LruCache<String, BitmapDrawable> mMemoryCache;

  private static final ExecutorService sExecutorService =
      new QuranExecutorService();
  private static final Handler sHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_IMAGE_LOADED && sInstance != null) {
        sInstance.onImageLoaded((QuranPageTask.QuranTaskData) msg.obj);
      }
    }
  };

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

    final int memClass = ((ActivityManager) context.getSystemService(
        Context.ACTIVITY_SERVICE)).getMemoryClass();
    final int cacheSize = 1024 * 1024 * memClass / 8;
    final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    Crashlytics.log(Log.DEBUG, TAG, "memory class: " + memClass +
        ", cache size: " + cacheSize + ", max memory: " + maxMemory);
    mMemoryCache = new LruCache<String, BitmapDrawable>(cacheSize) {
      @Override
      protected void entryRemoved(boolean evicted, String key,
          BitmapDrawable oldValue,
          BitmapDrawable newValue) {
        if (RecyclingBitmapDrawable.class.isInstance(oldValue)) {
          ((RecyclingBitmapDrawable) oldValue).setIsCached(false);
        }
      }

      @Override
      protected int sizeOf(String key, BitmapDrawable bitmapDrawable) {
        Bitmap bitmap = bitmapDrawable.getBitmap();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          return getBitmapSizeKitkat(bitmap);
        } else if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.HONEYCOMB_MR1) {
          return getBitmapSizeHoneycombMr1(bitmap);
        }

        Crashlytics.log(Log.DEBUG, TAG, "row bytes: " +
            bitmap.getRowBytes() + ", height: " +
            bitmap.getHeight() + ", " + (bitmap.getRowBytes() *
            bitmap.getHeight()));
        return bitmap.getRowBytes() * bitmap.getHeight();
      }
    };

    Crashlytics.log(Log.DEBUG, TAG,
        "initial LruCache size: " + (memClass / 8));
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  private int getBitmapSizeHoneycombMr1(Bitmap bitmap) {
    return bitmap.getByteCount();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private int getBitmapSizeKitkat(Bitmap bitmap) {
    return bitmap.getAllocationByteCount();
  }

  private void addBitmapToCache(String key, BitmapDrawable drawable) {
    if (drawable != null && getBitmapFromCache(key) == null) {
      if (RecyclingBitmapDrawable.class.isInstance(drawable)) {
        ((RecyclingBitmapDrawable) drawable).setIsCached(true);
      }
      mMemoryCache.put(key, drawable);
      Crashlytics.log(Log.DEBUG, TAG, "cache size: " + mMemoryCache.size());
    }

    Crashlytics.log(Log.DEBUG, TAG, "cache: number of puts: " +
        mMemoryCache.putCount() +
        ", number of evicts: " + mMemoryCache.evictionCount());
  }

  private BitmapDrawable getBitmapFromCache(String key) {
    return mMemoryCache.get(key);
  }

  public Future<?> loadPage(String widthParam, int page, AyahTracker tracker) {
    final BitmapDrawable drawable = getBitmapFromCache(page + widthParam);
    if (drawable != null) {
      tracker.onLoadImageResponse(drawable, Response.fromPage(page));
      return null;
    } else {
      QuranPageTask task = new QuranPageTask(mContext,
          widthParam, tracker, page);
      return sExecutorService.submit(task);
    }
  }

  // once complete, see if ImageView is still around and set bitmap.
  protected void onImageLoaded(QuranPageTask.QuranTaskData quranTaskData) {
    final Response response = quranTaskData.getResponse();
    BitmapDrawable drawable = null;
    if (response != null) {
      final Bitmap bitmap = response.getBitmap();
      if (bitmap != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
          drawable = new BitmapDrawable(mResources, bitmap);
        } else {
          drawable = new RecyclingBitmapDrawable(mResources, bitmap);
        }

        addBitmapToCache(response.getPageNumber() +
            response.getWidthParam(), drawable);
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
