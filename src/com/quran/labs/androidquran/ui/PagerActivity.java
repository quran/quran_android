package com.quran.labs.androidquran.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

public class PagerActivity extends SherlockFragmentActivity {
   private static String TAG = "PagerActivity";
   
   private QuranPageWorker mWorker = null;
   private SharedPreferences mPrefs = null;
   private long mLastPopupTime = 0;
   private boolean mIsActionBarHidden = true;

   @Override
   public void onCreate(Bundle savedInstanceState){
      setTheme(R.style.Theme_Sherlock);
      getSherlock().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

      super.onCreate(savedInstanceState);
   
      if (QuranScreenInfo.getInstance() == null){
         Log.d(TAG, "QuranScreenInfo was null, re-initializing...");
         WindowManager w = getWindowManager();
         Display d = w.getDefaultDisplay();
         int width = d.getWidth();
         int height = d.getHeight();
         QuranScreenInfo.initialize(width, height);
      }
      
      getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
      mPrefs = PreferenceManager.getDefaultSharedPreferences(
            getApplicationContext());

      getSupportActionBar().hide();
      mIsActionBarHidden = true;
      
      setContentView(R.layout.quran_page_activity);
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(0xaa000000));

      int page = 1;
      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null)
         page = 604 - extras.getInt("page");

      mWorker = new QuranPageWorker(this);
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
            QuranSettings.save(mPrefs);
            if (QuranSettings.getInstance().isDisplayMarkerPopup()){
               mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(PagerActivity.this, page, mLastPopupTime);
            }
         }
      });

      pager.setCurrentItem(page);
   }
   
   public void toggleActionBar(){
      if (mIsActionBarHidden){
         getWindow().addFlags(
               WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getWindow().clearFlags(
               WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getSupportActionBar().show();
         mIsActionBarHidden = false;
      }
      else {
         getWindow().addFlags(
               WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getWindow().clearFlags(
               WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getSupportActionBar().hide();
         mIsActionBarHidden = true;
      }
   }
   
   public QuranPageWorker getQuranPageWorker(){
      return mWorker;
   }
   
   @Override
   protected void onDestroy() {
      android.util.Log.d(TAG, "onDestroy()");
      super.onDestroy();
   }
}
