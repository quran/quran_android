package com.quran.labs.androidquran.ui;

import java.lang.ref.WeakReference;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LruCache;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.util.QuranSettings;

public class PagerActivity extends FragmentActivity {

   private static String TAG = "PagerActivity";
   private SharedPreferences prefs = null;
   private long mLastPopupTime = 0;
   private LruCache<Integer, Bitmap> mMemoryCache = null;

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      
      final int memClass = ((ActivityManager)getSystemService(
            Context.ACTIVITY_SERVICE)).getMemoryClass();
      final int cacheSize = 1024 * 1024 * memClass / 8;
      mMemoryCache = new LruCache<Integer, Bitmap>(cacheSize){
         @Override
         protected int sizeOf(Integer key, Bitmap bitmap){
            return bitmap.getRowBytes() * bitmap.getHeight();
         }
      };
      
      Log.d(TAG, "initial LruCache size: " + cacheSize);
      
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
      prefs = PreferenceManager.getDefaultSharedPreferences(
            getApplicationContext());

      setContentView(R.layout.quran_page_activity);
      int page = 1;
      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null)
         page = 604 - extras.getInt("page");

      mLastPopupTime = System.currentTimeMillis();
      QuranPageAdapter adapter = new QuranPageAdapter(getSupportFragmentManager());
      ViewPager pager = (ViewPager)findViewById(R.id.quran_pager);
      pager.setAdapter(adapter);
      pager.setOnPageChangeListener(new OnPageChangeListener(){

         @Override
         public void onPageScrollStateChanged(int state) {}

         @Override
         public void onPageScrolled(int position, float positionOffset,
               int positionOffsetPixels) {
         }

         @Override
         public void onPageSelected(int position) {
            Log.d(TAG, "onPageSelected(): " + position);
            int page = 604 - position;
            QuranSettings.getInstance().setLastPage(page);
            QuranSettings.save(prefs);
            if (QuranSettings.getInstance().isDisplayMarkerPopup()){
               mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(PagerActivity.this, page, mLastPopupTime);
            }
         }
      });

      pager.setCurrentItem(page);
   }


   public void addBitmapToCache(Integer key, Bitmap bitmap) {
      if (bitmap != null && getBitmapFromCache(key) == null) {
         mMemoryCache.put(key, bitmap);
      }
   }

   public Bitmap getBitmapFromCache(Integer key) {
      return mMemoryCache.get(key);
   }

   public void loadPage(int pageNumber, ImageView imageView) {
      final Bitmap bitmap = getBitmapFromCache(pageNumber);
      if (bitmap != null){
         imageView.setImageBitmap(bitmap);
      }
      else {
         // TODO: set a placeholder image while loading
         QuranPageWorkerTask task = new QuranPageWorkerTask(imageView);
         task.execute(pageNumber);
      }
   }

   class QuranPageWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
      private final WeakReference<ImageView> imageViewReference;
      private int data = 0;

      public QuranPageWorkerTask(ImageView imageView) {
         // use a WeakReference to ensure the ImageView can be garbage collected
         imageViewReference = new WeakReference<ImageView>(imageView);
      }

      @Override
      protected Bitmap doInBackground(Integer... params) {
         data = params[0];
         final Bitmap bitmap = QuranDisplayHelper.getQuranPage(data);
         if (bitmap == null){ Log.w(TAG, "got bitmap back as null..."); }

         addBitmapToCache(data, bitmap);
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