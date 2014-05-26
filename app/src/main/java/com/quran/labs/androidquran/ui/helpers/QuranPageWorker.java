package com.quran.labs.androidquran.ui.helpers;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.util.AsyncTask;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

public class QuranPageWorker {
   private static final String TAG = "QuranPageWorker";
   
   private Context mContext;
   private Resources mResources;
   private static QuranPageWorker sInstance;
   private LruCache<String, BitmapDrawable> mMemoryCache = null;

   public static synchronized QuranPageWorker getInstance(Context context){
     if (sInstance == null){
       sInstance = new QuranPageWorker(context);
     }
     return sInstance;
   }

   private QuranPageWorker(Context context){
      mContext = context.getApplicationContext();
      mResources = context.getResources();

      final int memClass = ((ActivityManager)context.getSystemService(
            Context.ACTIVITY_SERVICE)).getMemoryClass();
      final int cacheSize = 1024 * 1024 * memClass / 8;
      final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
      Crashlytics.log(Log.DEBUG, TAG, "memory class: " + memClass +
         ", cache size: " + cacheSize + ", max memory: " + maxMemory);
      mMemoryCache = new LruCache<String, BitmapDrawable>(cacheSize){
         @Override
         protected void entryRemoved(boolean evicted, String key,
                                     BitmapDrawable oldValue,
                                     BitmapDrawable newValue){
            if (RecyclingBitmapDrawable.class.isInstance(oldValue)){
               ((RecyclingBitmapDrawable)oldValue).setIsCached(false);
            }
         }

         @Override
         protected int sizeOf(String key, BitmapDrawable bitmapDrawable){
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
          "initial LruCache size: " + (memClass/8));
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
            ((RecyclingBitmapDrawable)drawable).setIsCached(true);
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

   public void loadPage(String widthParam, int page, ImageView imageView){
      final BitmapDrawable drawable = getBitmapFromCache(page + widthParam);
      if (drawable != null){
         imageView.setImageDrawable(drawable);
      }
      else {
         // AsyncTask included in our code now and by default
         // uses a SerialExecutor (or a SingleThreadExecutor on
         // pre honeycomb devices).
         QuranPageWorkerTask task = new QuranPageWorkerTask(
                 widthParam, imageView);
         task.execute(page);
      }
   }

   private class QuranPageWorkerTask extends
           AsyncTask<Integer, Void, BitmapDrawable> {
      private final WeakReference<ImageView> mImageViewReference;
      private int data = 0;
      private String mWidthParam;

      public QuranPageWorkerTask(String widthParam, ImageView imageView) {
         mWidthParam = widthParam;
         // use a WeakReference to ensure the ImageView can be garbage collected
         mImageViewReference = new WeakReference<ImageView>(imageView);
      }

      @Override
      protected BitmapDrawable doInBackground(Integer... params) {
         data = params[0];
         Bitmap bitmap = null;
         OutOfMemoryError oom = null;

         try {
            bitmap = QuranDisplayHelper.getQuranPage(
                 mContext, mWidthParam, data);
         } catch (OutOfMemoryError me){
           Crashlytics.log(Log.WARN, TAG,
               "out of memory exception loading page " +
               data + ", " + mWidthParam);
           oom = me;
         }

         if (bitmap == null){
           Crashlytics.log(Log.WARN, TAG, "cache memory: " +
               mMemoryCache.size() + " vs " + mMemoryCache.maxSize());
            if (QuranScreenInfo.getInstance().isTablet(mContext)){
               Crashlytics.log(Log.WARN, TAG,
                   "tablet got bitmap null, trying alternate width...");
               String param = QuranScreenInfo.getInstance().getWidthParam();
               if (param.equals(mWidthParam)){
                  param = QuranScreenInfo.getInstance().getTabletWidthParam();
               }
               bitmap = QuranDisplayHelper.getQuranPage(mContext, param, data);
               if (bitmap == null){
                  Crashlytics.log(Log.WARN, TAG, "bitmap still null, giving up...");
               }
            }
            Crashlytics.log(Log.WARN, TAG, "got bitmap back as null...");
         }

         BitmapDrawable drawable = null;
         if (bitmap != null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB){
               drawable = new BitmapDrawable(mResources, bitmap);
            }
            else {
               drawable = new RecyclingBitmapDrawable(mResources, bitmap);
            }

            addBitmapToCache(data + mWidthParam, drawable);
         } else if (oom != null){
           // throw the exception since we couldn't handle it
           throw oom;
         }

         return drawable;
      }

      // once complete, see if ImageView is still around and set bitmap.
      @Override
      protected void onPostExecute(BitmapDrawable drawable) {
         if (drawable != null) {
            final ImageView imageView = mImageViewReference.get();
            if (imageView != null) {
               imageView.setImageDrawable(drawable);
            }
            else { Log.w(TAG, "failed to set bitmap in imageview"); }
         }
      }
   }
}
