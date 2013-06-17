package com.quran.labs.androidquran.ui.helpers;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;
import com.quran.labs.androidquran.ui.fragment.ImageCacheFragment;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import java.lang.ref.WeakReference;

public class QuranPageWorker {
   private static final String TAG = "QuranPageWorker";
   
   private LruCache<String, Bitmap> mMemoryCache = null;
   private Context mContext;

   public QuranPageWorker(FragmentActivity activity){
      mContext = activity;
      ImageCacheFragment fragment = ImageCacheFragment.getImageCacheFragment(
              activity.getSupportFragmentManager());
      mMemoryCache = fragment.mRetainedCache;
      if (mMemoryCache != null){ return; }

      final int memClass = ((ActivityManager)activity.getSystemService(
            Context.ACTIVITY_SERVICE)).getMemoryClass();
      final int cacheSize = 1024 * 1024 * memClass / 8;
      final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
      Log.d(TAG, "memory class: " + memClass + ", cache size: " +
              cacheSize + ", max memory: " + maxMemory);
      mMemoryCache = new LruCache<String, Bitmap>(cacheSize){
         @Override
         protected int sizeOf(String key, Bitmap bitmap){
            Log.d(TAG, "row bytes: " + bitmap.getRowBytes() + ", height: " +
                  bitmap.getHeight() + ", " + (bitmap.getRowBytes() *
                        bitmap.getHeight()));
            return bitmap.getRowBytes() * bitmap.getHeight();
         }
      };
      fragment.mRetainedCache = mMemoryCache;
      
      Log.d(TAG, "initial LruCache size: " + (memClass/8));
   }
   
   private void addBitmapToCache(String key, Bitmap bitmap) {
      if (bitmap != null && getBitmapFromCache(key) == null) {
         mMemoryCache.put(key, bitmap);
         Log.d(TAG, "cache size: " + mMemoryCache.size());
      }
      
      Log.d(TAG, "cache: number of puts: " + mMemoryCache.putCount() + 
            ", number of evicts: " + mMemoryCache.evictionCount());
   }

   private Bitmap getBitmapFromCache(String key) {
      return mMemoryCache.get(key);
   }

   public void loadPage(String widthParam, int page, ImageView imageView){
      final Bitmap bitmap = getBitmapFromCache(page + widthParam);
      if (bitmap != null){
         imageView.setImageBitmap(bitmap);
      }
      else {
         // TODO: restrict so only three of these are running at a time
         QuranPageWorkerTask task = new QuranPageWorkerTask(
                 widthParam, imageView);
         task.execute(page);
      }
   }

   private class QuranPageWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
      private final WeakReference<ImageView> imageViewReference;
      private int data = 0;
      private String mWidthParam;

      public QuranPageWorkerTask(String widthParam, ImageView imageView) {
         mWidthParam = widthParam;
         // use a WeakReference to ensure the ImageView can be garbage collected
         imageViewReference = new WeakReference<ImageView>(imageView);
      }

      @Override
      protected Bitmap doInBackground(Integer... params) {
         data = params[0];
         Bitmap bitmap = QuranDisplayHelper.getQuranPage(
                 mContext, mWidthParam, data);
         if (bitmap == null){
            if (QuranScreenInfo.getInstance().isTablet(mContext)){
               Log.w(TAG, "tablet got bitmap null, trying alternate width...");
               String param = QuranScreenInfo.getInstance().getWidthParam();
               if (param.equals(mWidthParam)){
                  param = QuranScreenInfo.getInstance().getTabletWidthParam();
               }
               bitmap = QuranDisplayHelper.getQuranPage(mContext, param, data);
               if (bitmap == null){
                  Log.w(TAG, "bitmap still null, giving up...");
               }
            }
            Log.w(TAG, "got bitmap back as null...");
         }

         addBitmapToCache(data + mWidthParam, bitmap);
         return bitmap;
      }

      // once complete, see if ImageView is still around and set bitmap.
      @Override
      protected void onPostExecute(Bitmap bitmap) {
         if (imageViewReference != null && bitmap != null) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
               imageView.setImageBitmap(bitmap);
            }
            else { Log.w(TAG, "failed to set bitmap in imageview"); }
         }
      }
   }
}
