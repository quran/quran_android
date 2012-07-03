package com.quran.labs.androidquran.ui;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.service.QuranAudioService;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AudioStatusBar;

import java.io.File;
import java.io.Serializable;

public class PagerActivity extends SherlockFragmentActivity implements
        AudioStatusBar.AudioBarListener {
   private static String TAG = "PagerActivity";
   
   private QuranPageWorker mWorker = null;
   private SharedPreferences mPrefs = null;
   private long mLastPopupTime = 0;
   private boolean mIsActionBarHidden = true;
   private AudioStatusBar mAudioStatusBar = null;
   private ViewPager mViewPager = null;
   private QuranPageAdapter mPagerAdapter = null;
   private boolean mShouldReconnect = false;

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

      int background = getResources().getColor(
              R.color.transparent_actionbar_color);
      setContentView(R.layout.quran_page_activity);
      getSupportActionBar().setBackgroundDrawable(
              new ColorDrawable(background));
      mAudioStatusBar = (AudioStatusBar)findViewById(R.id.audio_area);
      mAudioStatusBar.setAudioBarListener(this);

      int page = 1;
      Intent intent = getIntent();
      Bundle extras = intent.getExtras();
      if (extras != null){ page = 604 - extras.getInt("page"); }

      mWorker = new QuranPageWorker(this);
      mLastPopupTime = System.currentTimeMillis();
      mPagerAdapter = new QuranPageAdapter(
              getSupportFragmentManager());
      mViewPager = (ViewPager)findViewById(R.id.quran_pager);
      mViewPager.setAdapter(mPagerAdapter);
      mViewPager.setOnPageChangeListener(new OnPageChangeListener(){

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
               mLastPopupTime = QuranDisplayHelper.displayMarkerPopup(
                       PagerActivity.this, page, mLastPopupTime);
            }
         }
      });

      mViewPager.setCurrentItem(page);

      // just got created, need to reconnect to service
      mShouldReconnect = true;
   }

   @Override
   public void onResume(){
      LocalBroadcastManager.getInstance(this).registerReceiver(
              mAudioReceiver,
              new IntentFilter(AudioService.AudioUpdateIntent.INTENT_NAME));
      super.onResume();
      if (mShouldReconnect){
         startService(new Intent(AudioService.ACTION_CONNECT));
         mShouldReconnect = false;
      }
   }

   @Override
   public void onPause(){
      LocalBroadcastManager.getInstance(this)
              .unregisterReceiver(mAudioReceiver);
      super.onPause();
   }

   BroadcastReceiver mAudioReceiver = new BroadcastReceiver(){
      @Override
      public void onReceive(Context context, Intent intent){
         if (intent != null){
            int state = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.STATUS, -1);
            int sura = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.SURA, -1);
            int ayah = intent.getIntExtra(
                    AudioService.AudioUpdateIntent.AYAH, -1);
            if (state == AudioService.AudioUpdateIntent.PLAYING){
               mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
               highlightAyah(sura, ayah);
            }
            else if (state == AudioService.AudioUpdateIntent.PAUSED){
               mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
               highlightAyah(sura, ayah);
            }
            else if (state == AudioService.AudioUpdateIntent.STOPPED){
               mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
               unhighlightAyah();

               Serializable qi = intent.getSerializableExtra(
                       AudioService.EXTRA_PLAY_INFO);
               if (qi != null){
                  // this means we stopped due to missing audio
               }
            }
         }
      }
   };

   public void toggleActionBar(){
      if (mIsActionBarHidden){
         getWindow().addFlags(
                 WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getWindow().clearFlags(
                 WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getSupportActionBar().show();
         mAudioStatusBar.updateSelectedItem();
         mAudioStatusBar.setVisibility(View.VISIBLE);
         mIsActionBarHidden = false;
      }
      else {
         getWindow().addFlags(
               WindowManager.LayoutParams.FLAG_FULLSCREEN);
         getWindow().clearFlags(
               WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
         getSupportActionBar().hide();
         mAudioStatusBar.setVisibility(View.GONE);
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

   public void highlightAyah(int sura, int ayah){
      int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      int position = 604 - page;

      Fragment f = mPagerAdapter.getFragmentIfExists(position);
      if (f != null && f instanceof QuranPageFragment){
         QuranPageFragment qpf = (QuranPageFragment)f;
         qpf.highlightAyah(sura, ayah);
      }
   }

   public void unhighlightAyah(){
      int position = mViewPager.getCurrentItem();
      Fragment f = mPagerAdapter.getFragmentIfExists(position);
      if (f!= null && f instanceof QuranPageFragment){
         QuranPageFragment qpf = (QuranPageFragment)f;
         qpf.unhighlightAyah();
      }
   }

   @Override
   public void onPlayPressed() {
      int position = mViewPager.getCurrentItem();
      int page = 604 - position;

      int startSura = QuranInfo.PAGE_SURA_START[page - 1];
      int startAyah = QuranInfo.PAGE_AYAH_START[page - 1];
      int currentQari = mAudioStatusBar.getCurrentQari();

      QuranAyah ayah = new QuranAyah(startSura, startAyah);
      boolean streaming = true;
      if (streaming){
         playStreaming(ayah, page, currentQari);
      }
      else { downloadAndPlayAudio(ayah, page, currentQari); }
   }

   private void playStreaming(QuranAyah ayah, int page, int qari){
      String qariUrl = AudioUtils.getQariUrl(this, qari);
      String s = qariUrl + "%03d%03d" + AudioUtils.AUDIO_EXTENSION;
      AudioRequest request = new AudioRequest(s, ayah);
      play(request);

      mAudioStatusBar.switchMode(AudioStatusBar.PLAYING_MODE);
   }

   private void downloadAndPlayAudio(QuranAyah ayah, int page, int qari){
      QuranAyah endAyah = AudioUtils.getLastAyahToPlay(ayah, page,
              AudioUtils.LookAheadAmount.PAGE);
      String url = AudioUtils.getLocalQariUrl(this, qari);
      if (endAyah == null || url == null){ return; }

      url += File.separator + "%d" + File.separator +
              "%d" + AudioUtils.AUDIO_EXTENSION;
      AudioRequest request = new AudioRequest(url, ayah);
      request.setPlayBounds(ayah, endAyah);

      if (AudioUtils.haveAllFiles(this, qari, ayah, endAyah)){
         play(request);
      }
      else {
         mAudioStatusBar.switchMode(AudioStatusBar.DOWNLOADING_MODE);
      }
   }

   private void play(AudioRequest request){
      Intent i = new Intent(AudioService.ACTION_PLAYBACK);
      i.putExtra(AudioService.EXTRA_PLAY_INFO, request);
      i.putExtra(AudioService.EXTRA_IGNORE_IF_PLAYING, true);
      startService(i);
   }

   @Override
   public void onPausePressed() {
      startService(new Intent(AudioService.ACTION_PAUSE));
      mAudioStatusBar.switchMode(AudioStatusBar.PAUSED_MODE);
   }

   @Override
   public void onNextPressed() {
      startService(new Intent(AudioService.ACTION_SKIP));
   }

   @Override
   public void onPreviousPressed() {
      startService(new Intent(AudioService.ACTION_REWIND));
   }

   @Override
   public void onStopPressed() {
      startService(new Intent(AudioService.ACTION_STOP));
      mAudioStatusBar.switchMode(AudioStatusBar.STOPPED_MODE);
      unhighlightAyah();
   }
}