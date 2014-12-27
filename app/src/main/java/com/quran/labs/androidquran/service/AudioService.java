/* 
 * This code is based on the RandomMusicPlayer example from
 * the Android Open Source Project samples.  It has been modified
 * for use in Quran Android.
 *   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quran.labs.androidquran.service;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler;
import com.quran.labs.androidquran.service.util.AudioFocusHelper;
import com.quran.labs.androidquran.service.util.AudioFocusable;
import com.quran.labs.androidquran.service.util.AudioIntentReceiver;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.MediaButtonHelper;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.RepeatInfo;
import com.quran.labs.androidquran.ui.PagerActivity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Service that handles media playback. This is the Service through which we
 * perform all the media handling in our application. It waits for Intents
 * (which come from our main activity, {@link PagerActivity}, which signal
 * the service to perform specific operations: Play, Pause, Rewind, Skip, etc.
 */
public class AudioService extends Service implements OnCompletionListener,
        OnPreparedListener, OnErrorListener, AudioFocusable,
        MediaPlayer.OnSeekCompleteListener {

   // The tag we put on debug messages
   final static String TAG = "AudioService";

   // These are the Intent actions that we are prepared to handle. Notice that
   // the fact these constants exist in our class is a mere convenience: what
   // really defines the actions our service can handle are the <action> tags
   // in the <intent-filters> tag for our service in AndroidManifest.xml.
   public static final String ACTION_PLAYBACK =
         "com.quran.labs.androidquran.action.PLAYBACK";
   public static final String ACTION_PLAY =
           "com.quran.labs.androidquran.action.PLAY";
   public static final String ACTION_PAUSE =
           "com.quran.labs.androidquran.action.PAUSE";
   public static final String ACTION_STOP =
           "com.quran.labs.androidquran.action.STOP";
   public static final String ACTION_SKIP =
           "com.quran.labs.androidquran.action.SKIP";
   public static final String ACTION_REWIND =
           "com.quran.labs.androidquran.action.REWIND";
   public static final String ACTION_CONNECT =
           "com.quran.labs.androidquran.action.CONNECT";
   public static final String ACTION_UPDATE_REPEAT =
           "com.quran.labs.androidquran.action.UPDATE_REPEAT";

   // pending notification request codes
   private static final int REQUEST_CODE_MAIN = 0;
   private static final int REQUEST_CODE_PREVIOUS = 1;
   private static final int REQUEST_CODE_PAUSE = 2;
   private static final int REQUEST_CODE_SKIP = 3;
   private static final int REQUEST_CODE_STOP = 4;
   private static final int REQUEST_CODE_RESUME = 5;

  public static class AudioUpdateIntent {
      public static final String INTENT_NAME =
              "com.quran.labs.androidquran.audio.AudioUpdate";
      public static final String STATUS = "status";
      public static final String SURA = "sura";
      public static final String AYAH = "ayah";
      public static final String REPEAT_COUNT = "repeat_count";
      public static final String REQUEST = "request";

      public static final int STOPPED = 0;
      public static final int PLAYING = 1;
      public static final int PAUSED = 2;
   }

   // The volume we set the media player to when we lose audio focus, but are
   // allowed to reduce the volume instead of stopping playback.
   public static final float DUCK_VOLUME = 0.1f;

   // our media player
   private MediaPlayer mPlayer = null;

   // are we playing an override file (basmalah/isti3atha)
   private boolean mPlayerOverride;

   // our AudioFocusHelper object, if it's available (it's available on SDK
   // level >= 8). If not available, this will be null. Always check for null
   // before using!
   private AudioFocusHelper mAudioFocusHelper = null;

   // object representing the current playing request
   private AudioRequest mAudioRequest = null;

   // so user can pass in a serializable AudioRequest to the intent
   public static final String EXTRA_PLAY_INFO =
           "com.quran.labs.androidquran.PLAY_INFO";

   // ignore the passed in play info if we're already playing
   public static final String EXTRA_IGNORE_IF_PLAYING =
           "com.quran.labs.androidquran.IGNORE_IF_PLAYING";

   // used to override what is playing now (stop then play)
   public static final String EXTRA_STOP_IF_PLAYING =
           "com.quran.labs.androidquran.STOP_IF_PLAYING";

   // repeat info
   public static final String EXTRA_VERSE_REPEAT_COUNT =
           "com.quran.labs.androidquran.VERSE_REPEAT_COUNT";
   public static final String EXTRA_RANGE_REPEAT_COUNT =
           "com.quran.labs.androidquran.RANGE_REPEAT_COUNT";
   public static final String EXTRA_RANGE_RESTRICT =
           "com.quran.labs.androidquran.RANGE_RESTRICT";

   // indicates the state our service:
   private enum State {
      Stopped,    // media player is stopped and not prepared to play
      Preparing,  // media player is preparing...
      Playing,    // playback active (media player ready!). (but the media
      // player may actually be  paused in this state if we don't have audio
      // focus. But we stay in this state so that we know we have to resume
      // playback once we get focus back)
      Paused      // playback paused (media player ready!)
   };

   private State mState = State.Stopped;

   private enum PauseReason {
      UserRequest,  // paused by user request
      FocusLoss,    // paused because of audio focus loss
   };

   // why did we pause? (only relevant if mState == State.Paused)
   PauseReason mPauseReason = PauseReason.UserRequest;

   // do we have audio focus?
   private enum AudioFocus {
      NoFocusNoDuck,    // we don't have audio focus, and can't duck
      NoFocusCanDuck,   // we don't have focus, but can play at a low volume
      Focused           // we have full audio focus
   }
   private AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

   private String mNotificationName = "";
   
   // title of the audio we are currently playing
   private String mAudioTitle = "";

   // are we already in the foreground
   private boolean mIsSetupAsForeground = false;

   // should we stop (after preparing is done) or not
   private boolean mShouldStop = false;

   // Wifi lock that we hold when streaming files from the internet,
   // in order to prevent the device from shutting off the Wifi radio
   private WifiLock mWifiLock;

   // The ID we use for the notification (the onscreen alert that appears
   // at the notification area at the top of the screen as an icon -- and
   // as text as well if the user expands the notification area).
   final int NOTIFICATION_ID = 4;

   // The component name of MusicIntentReceiver, for use with media button
   // and remote control APIs
   private ComponentName mMediaButtonReceiverComponent;

   private AudioManager mAudioManager;
   private NotificationManager mNotificationManager;

   private NotificationCompat.Builder mNotificationBuilder;
   private NotificationCompat.Builder mPausedNotificationBuilder;
   private LocalBroadcastManager mBroadcastManager = null;

   private int mGaplessSura = 0;
   private SparseIntArray mGaplessSuraData = null;
   private AsyncTask<Integer, Void, SparseIntArray> mTimingTask = null;

   public static final int MSG_START_AUDIO = 1;
   public static final int MSG_UPDATE_AUDIO_POS = 2;
   private Handler mHandler = new Handler(){
      @Override
      public void handleMessage(Message msg){
         if (msg == null){ return; }
         if (msg.what == MSG_START_AUDIO){
            configAndStartMediaPlayer();
         } else if (msg.what == MSG_UPDATE_AUDIO_POS){
            updateAudioPlayPosition();
         }
      }
   };

   /**
    * Makes sure the media player exists and has been reset. This will create
    * the media player if needed, or reset the existing media player if one
    * already exists.
    */
   private void createMediaPlayerIfNeeded() {
      if (mPlayer == null) {
         mPlayer = new MediaPlayer();
         
         // Make sure the media player will acquire a wake-lock while playing.
         // If we don't do that, the CPU might go to sleep while the song is
         // playing, causing playback to stop.
         //
         // Remember that to use this, we have to declare the
         // android.permission.WAKE_LOCK permission in AndroidManifest.xml.
         mPlayer.setWakeMode(getApplicationContext(),
                 PowerManager.PARTIAL_WAKE_LOCK);

         // we want the media player to notify us when it's ready preparing,
         // and when it's done playing:
         mPlayer.setOnPreparedListener(this);
         mPlayer.setOnCompletionListener(this);
         mPlayer.setOnErrorListener(this);
         mPlayer.setOnSeekCompleteListener(this);
      }
      else
         mPlayer.reset();
   }

   @Override
   public void onCreate() {
      Log.i(TAG, "debug: Creating service");

      mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "audiolock");
      mNotificationManager = (NotificationManager)
              getSystemService(NOTIFICATION_SERVICE);
      mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

      // create the Audio Focus Helper, if the Audio Focus feature is available
      if (android.os.Build.VERSION.SDK_INT >= 8){
         mAudioFocusHelper = new AudioFocusHelper(
                 getApplicationContext(), this);
      }
      // no focus feature, so we always "have" audio focus
      else { mAudioFocus = AudioFocus.Focused; }

      mMediaButtonReceiverComponent = new ComponentName(
              this, AudioIntentReceiver.class);
      mNotificationName = getString(R.string.app_name);

      final Context appContext = getApplicationContext();
      mBroadcastManager = LocalBroadcastManager.getInstance(appContext);
   }

   /**
    * Called when we receive an Intent. When we receive an intent sent to us
    * via startService(), this is the method that gets called. So here we
    * react appropriately depending on the Intent's action, which specifies
    * what is being requested of us.
    */
   @Override
   public int onStartCommand(Intent intent, int flags, int startId) {
      String action = intent.getAction();

      if (ACTION_CONNECT.equals(action)){
         if (mState == State.Stopped){
            processStopRequest(true);
         }
         else {
            int sura = -1;
            int ayah = -1;
            int repeatCount = -200;
            int state = AudioUpdateIntent.PLAYING;
            if (mState == State.Paused){
               state = AudioUpdateIntent.PAUSED;
            }

            if (mAudioRequest != null){
               sura = mAudioRequest.getCurrentSura();
               ayah = mAudioRequest.getCurrentAyah();

               final RepeatInfo repeatInfo = mAudioRequest.getRepeatInfo();
               if (repeatInfo != null) {
                 repeatCount = repeatInfo.getRepeatCount();
               }
            }

            Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
            updateIntent.putExtra(AudioUpdateIntent.STATUS, state);
            updateIntent.putExtra(AudioUpdateIntent.SURA, sura);
            updateIntent.putExtra(AudioUpdateIntent.AYAH, ayah);
            updateIntent.putExtra(AudioUpdateIntent.REPEAT_COUNT, repeatCount);
            updateIntent.putExtra(AudioUpdateIntent.REQUEST, mAudioRequest);

            mBroadcastManager.sendBroadcast(updateIntent);
         }
      }
      else if (ACTION_PLAYBACK.equals(action)){
         Serializable playInfo = intent.getSerializableExtra(EXTRA_PLAY_INFO);
         if (playInfo != null && playInfo instanceof AudioRequest){
            if (mState == State.Stopped ||
                    !intent.getBooleanExtra(EXTRA_IGNORE_IF_PLAYING, false)){
               mAudioRequest = (AudioRequest)playInfo;
            }
         }

         if (intent.getBooleanExtra(EXTRA_STOP_IF_PLAYING, false)){
            if (mPlayer != null){ mPlayer.stop(); }
            mState = State.Stopped;
         }

         processTogglePlaybackRequest();
      }
      else if (ACTION_PLAY.equals(action)){ processPlayRequest(); }
      else if (ACTION_PAUSE.equals(action)){ processPauseRequest(); }
      else if (ACTION_SKIP.equals(action)){ processSkipRequest(); }
      else if (ACTION_STOP.equals(action)){ processStopRequest(); }
      else if (ACTION_REWIND.equals(action)){ processRewindRequest(); }
      else if (ACTION_UPDATE_REPEAT.equals(action)){
        if (mAudioRequest != null) {
          // set the repeat info if applicable
          final int verseRepeatCount = intent.getIntExtra(
              EXTRA_VERSE_REPEAT_COUNT, mAudioRequest.getVerseRepeatCount());
          mAudioRequest.setVerseRepeatCount(verseRepeatCount);

          // set the range repeat count
          final int rangeRepeatCount = intent.getIntExtra(
              EXTRA_RANGE_REPEAT_COUNT, mAudioRequest.getRangeRepeatCount());
          mAudioRequest.setRangeRepeatCount(rangeRepeatCount);

          // set the enforce range flag
          if (intent.hasExtra(EXTRA_RANGE_RESTRICT)) {
            final boolean enforceRange = intent.getBooleanExtra(
                EXTRA_RANGE_RESTRICT, false);
            mAudioRequest.setEnforceBounds(enforceRange);
          }
        }
      }

      return START_NOT_STICKY; // Means we started the service, but don't want
      // it to restart in case it's killed.
   }

   private class ReadGaplessDataTask extends AsyncTask<Integer, Void,
           SparseIntArray> {
      private int mSura = 0;
      private String mDatabasePath = null;

      public ReadGaplessDataTask(String database){
         mDatabasePath = database;
      }

      @Override
      protected SparseIntArray doInBackground(Integer... params){
         int sura = params[0];
         mSura = sura;

         SuraTimingDatabaseHandler db =
                 SuraTimingDatabaseHandler.getDatabaseHandler(mDatabasePath);
         SparseIntArray map = null;
         Cursor cursor = db.getAyahTimings(sura);
         Log.d(TAG, "got cursor of data");

         if (cursor != null && cursor.moveToFirst()){
            map = new SparseIntArray();
            do {
               int ayah = cursor.getInt(1);
               int time = cursor.getInt(2);
               //Log.d(TAG, "adding: " + ayah + " @ " + time);
               map.put(ayah, time);
            }
            while (cursor.moveToNext());
         }
         if (cursor != null){ cursor.close(); }
         return map;
      }

      @Override
      protected void onPostExecute(SparseIntArray map){
         mGaplessSura = mSura;
         mGaplessSuraData = map;
         mTimingTask = null;
      }
   }

   private int getSeekPosition(boolean isRepeating){
      if (mAudioRequest == null){ return -1; }

      if (mGaplessSura == mAudioRequest.getCurrentSura()){
         if (mGaplessSuraData != null){
            int ayah = mAudioRequest.getCurrentAyah();
            Integer time = mGaplessSuraData.get(ayah);
            if (ayah == 1 && !isRepeating){
               return mGaplessSuraData.get(0);
            }
            return time;
         }
      }
      return -1;
   }

   private void updateAudioPlayPosition(){
      Log.d(TAG, "updateAudioPlayPosition");

      if (mAudioRequest == null){ return; }
      if (mPlayer != null || mGaplessSuraData == null){
         int sura = mAudioRequest.getCurrentSura();
         int ayah = mAudioRequest.getCurrentAyah();

         int updatedAyah = ayah;
         int maxAyahs = QuranInfo.getNumAyahs(sura);

         if (sura != mGaplessSura){ return; }
         int pos = mPlayer.getCurrentPosition();
         Integer ayahTime = mGaplessSuraData.get(ayah);
         Log.d(TAG, "updateAudioPlayPosition: " + sura + ":" + ayah +
                 ", currently at " + pos + " vs expected at " + ayahTime);

         if (ayahTime > pos){
            int iterAyah = ayah;
            while (--iterAyah > 0){
               ayahTime = mGaplessSuraData.get(iterAyah);
               if (ayahTime <= pos){
                  updatedAyah = iterAyah;
                  break;
               }
               else { updatedAyah--; }
            }
         }
         else {
            int iterAyah = ayah;
            while (++iterAyah <= maxAyahs){
               ayahTime = mGaplessSuraData.get(iterAyah);
               if (ayahTime > pos){
                  updatedAyah = iterAyah - 1;
                  break;
               }
               else { updatedAyah++; }
            }
         }

         Log.d(TAG, "updateAudioPlayPosition: " + sura + ":" + ayah +
                 ", decided ayah should be: " + updatedAyah);

         if (updatedAyah != ayah){
            ayahTime = mGaplessSuraData.get(ayah);
            if (Math.abs(pos - ayahTime) < 150){
               // shouldn't change ayahs if the delta is just 150ms...
               mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150);
               return;
            }

            QuranAyah nextAyah = mAudioRequest.setCurrentAyah(sura, updatedAyah);
            if (nextAyah == null) {
              processStopRequest();
              return;
            } else if (nextAyah.getSura() != sura ||
                nextAyah.getAyah() != updatedAyah){
               // remove any messages currently in the queue
               mHandler.removeCallbacksAndMessages(null);

               // if the ayah hasn't changed, we're repeating the ayah,
               // otherwise, we're repeating a range. this variable is
               // what determines whether or not we replay the basmallah.
               final boolean ayahRepeat =
                   (ayah == nextAyah.getAyah() && sura == nextAyah.getSura());

               if (ayahRepeat) {
                 // jump back to the ayah we should repeat and play it
                 pos = getSeekPosition(true);
                 mPlayer.seekTo(pos);
               } else {
                 // we're repeating into a different sura
                 final boolean flag = sura != mAudioRequest.getCurrentSura();
                 playAudio(flag);
               }
               return;
            }

            // moved on to next ayah
            updateNotification(mAudioRequest.getTitle(getApplicationContext()));
         }
         else {
            // if we have end of sura info and we bypassed end of sura
            // line, switch the sura.
            ayahTime = mGaplessSuraData.get(999);
            if (ayahTime > 0 && pos >= ayahTime){
               QuranAyah repeat = mAudioRequest.setCurrentAyah(sura+1, 1);
               if (repeat != null && repeat.getSura() == sura){
                  // remove any messages currently in the queue
                  mHandler.removeCallbacksAndMessages(null);

                  // jump back to the ayah we should repeat and play it
                  pos = getSeekPosition(false);
                  mPlayer.seekTo(pos);
               }
               else { playAudio(true); }
               return;
            }
         }

         notifyAyahChanged();

         if (maxAyahs >= (updatedAyah + 1)){
            Integer t = mGaplessSuraData.get(updatedAyah + 1);
            t = t - mPlayer.getCurrentPosition();
            Log.d(TAG, "updateAudioPlayPosition postingDelayed after: " + t);

            if (t < 100){ t = 100; }
            else if (t > 10000){ t = 10000; }
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, t);
         }
         // if we're on the last ayah, don't do anything - let the file
         // complete on its own to avoid getCurrentPosition() bugs.
      }
   }

   private void processTogglePlaybackRequest() {
      if (mState == State.Paused || mState == State.Stopped) {
         processPlayRequest();
      } else {
         processPauseRequest();
      }
   }

   private void processPlayRequest() {
      if (mAudioRequest == null){ return; }
      tryToGetAudioFocus();

      // actually play the file

      if (mState == State.Stopped) {
         if (mAudioRequest.isGapless()){
            if (mTimingTask != null){
               mTimingTask.cancel(true);
            }
            String dbPath = mAudioRequest.getGaplessDatabaseFilePath();
            mTimingTask = new ReadGaplessDataTask(dbPath);
            mTimingTask.execute(mAudioRequest.getCurrentSura());
         }

         // If we're stopped, just go ahead to the next file and start playing
         playAudio(mAudioRequest.getCurrentSura() == 9 &&
                   mAudioRequest.getCurrentAyah() == 1);
      }
      else if (mState == State.Paused) {
         // If we're paused, just continue playback and restore the
         // 'foreground service' state.
         mState = State.Playing;
         setUpAsForeground();
         configAndStartMediaPlayer(false);
         notifyAudioStatus(AudioUpdateIntent.PLAYING);
      }
   }

   private void processPauseRequest() {
      if (mState == State.Playing) {
         // Pause media player and cancel the 'foreground service' state.
         mState = State.Paused;
         mHandler.removeCallbacksAndMessages(null);
         mPlayer.pause();
         if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
           // while paused, we always retain the MediaPlayer
           relaxResources(false, true);
         } else {
           // on jellybean and above, stay in the foreground and
           // update the notification.
           relaxResources(false, false);
           pauseNotification();
           notifyAudioStatus(AudioUpdateIntent.PAUSED);
         }
      }
   }

   private void processRewindRequest() {
      if (mState == State.Playing || mState == State.Paused){
         int seekTo = 0;
         int pos = mPlayer.getCurrentPosition();
         if (mAudioRequest.isGapless()){
            seekTo = getSeekPosition(true);
            pos =  pos - seekTo;
         }

         if (pos > 1500 && !mPlayerOverride){
            mPlayer.seekTo(seekTo);
            mState = State.Playing; // in case we were paused
         }
         else {
            tryToGetAudioFocus();
            int sura = mAudioRequest.getCurrentSura();
            mAudioRequest.gotoPreviousAyah();
            if (mAudioRequest.isGapless() &&
                    sura == mAudioRequest.getCurrentSura()){
               int timing = getSeekPosition(true);
               if (timing > -1){ mPlayer.seekTo(timing); }
               updateNotification(
                       mAudioRequest.getTitle(getApplicationContext()));
               mState = State.Playing; // in case we were paused
               return;
            }
            playAudio();
         }
      }
   }

   private void processSkipRequest() {
      if (mAudioRequest == null){ return; }
      if (mState == State.Playing || mState == State.Paused) {
         if (mPlayerOverride) {
           playAudio(false);
         } else {
           int sura = mAudioRequest.getCurrentSura();
           tryToGetAudioFocus();
           mAudioRequest.gotoNextAyah(true);
           if (mAudioRequest.isGapless() &&
               sura == mAudioRequest.getCurrentSura()) {
             int timing = getSeekPosition(false);
             if (timing > -1) {
               mPlayer.seekTo(timing);
               mState = State.Playing; // in case we were paused
             }
             updateNotification(
                 mAudioRequest.getTitle(getApplicationContext()));
             return;
           }
           playAudio();
         }
      }
   }

   private void processStopRequest() {
      processStopRequest(false);
   }

   private void processStopRequest(boolean force) {
      mHandler.removeCallbacksAndMessages(null);

      if (mState == State.Preparing){
         mShouldStop = true;
         relaxResources(false, true);
      }

      if (mState == State.Playing || mState == State.Paused || force) {
         mState = State.Stopped;

         // let go of all resources...
         relaxResources(true, true);
         giveUpAudioFocus();

         // service is no longer necessary. Will be started again if needed.
         stopSelf();

         // stop async task if it's running
         if (mTimingTask != null){
           mTimingTask.cancel(true);
         }

         // tell the ui we've stopped
         notifyAudioStatus(AudioUpdateIntent.STOPPED);
      }
   }

   private void notifyAyahChanged(){
      if (mAudioRequest != null){
         Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
         updateIntent.putExtra(AudioUpdateIntent.STATUS,
                 AudioUpdateIntent.PLAYING);
         updateIntent.putExtra(AudioUpdateIntent.SURA,
                 mAudioRequest.getCurrentSura());
         updateIntent.putExtra(AudioUpdateIntent.AYAH,
                 mAudioRequest.getCurrentAyah());
         mBroadcastManager.sendBroadcast(updateIntent);
      }
   }

   private void notifyAudioStatus(int status) {
     Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
     updateIntent.putExtra(AudioUpdateIntent.STATUS, status);
     mBroadcastManager.sendBroadcast(updateIntent);
   }

   /**
    * Releases resources used by the service for playback. This includes the
    * "foreground service" status and notification, the wake locks and
    * possibly the MediaPlayer.
    *
    * @param releaseMediaPlayer Indicates whether the Media Player should also
    *                           be released or not
    */
   private void relaxResources(boolean releaseMediaPlayer,
                               boolean stopForeground){
      if (stopForeground){
        // stop being a foreground service
        stopForeground(true);
        mIsSetupAsForeground = false;
      }

      // stop and release the Media Player, if it's available
      if (releaseMediaPlayer && mPlayer != null) {
         mPlayer.reset();
         mPlayer.release();
         mPlayer = null;
      }
      
      // we can also release the Wifi lock, if we're holding it
      if (mWifiLock.isHeld()){ mWifiLock.release(); }
   }

   private void giveUpAudioFocus() {
      if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
            && mAudioFocusHelper.abandonFocus())
         mAudioFocus = AudioFocus.NoFocusNoDuck;
   }

   /**
    * Reconfigures MediaPlayer according to audio focus settings and
    * starts/restarts it. This method starts/restarts the MediaPlayer
    * respecting the current audio focus state. So if we have focus,
    * it will play normally; if we don't have focus, it will either
    * leave the MediaPlayer paused or set it to a low volume, depending
    * on what is allowed by the current focus settings. This method assumes
    * mPlayer != null, so if you are calling it, you have to do so from a
    * context where you are sure this is the case.
    */
   private void configAndStartMediaPlayer() {
      configAndStartMediaPlayer(true);
   }

   private void configAndStartMediaPlayer(boolean canSeek){
      Log.d(TAG, "configAndStartMediaPlayer()");
      if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
         // If we don't have audio focus and can't duck, we have to pause,
         // even if mState is State.Playing. But we stay in the Playing state
         // so that we know we have to resume playback once we get focus back.
         if (mPlayer.isPlaying()) { mPlayer.pause(); }
         return;
      }
      else if (mAudioFocus == AudioFocus.NoFocusCanDuck){
         // we'll be relatively quiet
         mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);
      }
      else { mPlayer.setVolume(1.0f, 1.0f); } // we can be loud

      if (mShouldStop){
         processStopRequest();
         mShouldStop = false;
         return;
      }

      if (mPlayerOverride) {
        if (!mPlayer.isPlaying()) {
          mPlayer.start();
        }
        return;
      }

      Log.d(TAG, "checking if playing...");
      if (!mPlayer.isPlaying()){
         if (canSeek && mAudioRequest.isGapless()){
            int timing = getSeekPosition(false);
            if (timing != -1){
               Log.d(TAG, "got timing: " + timing +
                       ", seeking and updating later...");
               mPlayer.seekTo(timing);
               return;
            }
            else {
               Log.d(TAG, "no timing data yet, will try again...");
               // try to play again after 200 ms
               mHandler.sendEmptyMessageDelayed(MSG_START_AUDIO, 200);
               return;
            }
         }
         else if (mAudioRequest.isGapless()){
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200);
         }

         mPlayer.start();
      }
   }

   private void tryToGetAudioFocus() {
      if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
            && mAudioFocusHelper.requestFocus())
         mAudioFocus = AudioFocus.Focused;
   }

   /**
    * Starts playing the next file.
    */
   private void playAudio() {
     playAudio(false);
   }

   private void playAudio(boolean playRepeatSeparator) {
      mState = State.Stopped;
      relaxResources(false, false); // release everything except MediaPlayer
      mPlayerOverride = false;

      try {
         String url = mAudioRequest == null? null : mAudioRequest.getUrl();
         if (mAudioRequest == null ||  url == null){
            Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
            updateIntent.putExtra(AudioUpdateIntent.STATUS,
                    AudioUpdateIntent.STOPPED);
            mBroadcastManager.sendBroadcast(updateIntent);

            processStopRequest(true); // stop everything!
            return;
         }

         final boolean isStreaming =
             url.startsWith("http:") || url.startsWith("https:");
         if (!isStreaming){
            File f = new File(url);
            if (!f.exists()){
               Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
               updateIntent.putExtra(AudioUpdateIntent.STATUS,
                       AudioUpdateIntent.STOPPED);
               updateIntent.putExtra(EXTRA_PLAY_INFO, mAudioRequest);
               mBroadcastManager.sendBroadcast(updateIntent);

               processStopRequest(true);
               return;
            }
         }

         int overrideResource = 0;
         if (playRepeatSeparator) {
           final int sura = mAudioRequest.getCurrentSura();
           final int ayah = mAudioRequest.getCurrentAyah();
           if (sura != 9 && ayah > 1) {
             overrideResource = R.raw.bismillah;
           } else if (sura == 9 &&
               (ayah > 1 || mAudioRequest.needsIsti3athaAudio())) {
             overrideResource = R.raw.isti3atha;
           }
           // otherwise, ayah of 1 will automatically play the file's basmala
         }

         Log.d(TAG, "okay, we are preparing to play - streaming is: " +
             isStreaming);
         createMediaPlayerIfNeeded();
         mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
         try {
           boolean playUrl = true;
           if (overrideResource != 0) {
              AssetFileDescriptor afd =
                  getResources().openRawResourceFd(overrideResource);
              if (afd != null) {
                mPlayer.setDataSource(afd.getFileDescriptor(),
                    afd.getStartOffset(),
                    afd.getLength());
                afd.close();
                mPlayerOverride = true;
                playUrl = false;
              }
            }

            if (playUrl) {
              overrideResource = 0;
              mPlayer.setDataSource(url);
            }
         } catch (IllegalStateException ie){
           Crashlytics.log("IllegalStateException() while " +
               "setting data source, trying to reset...");
           if (overrideResource != 0) {
             playAudio(false);
             return;
           }
           mPlayer.reset();
           mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
           mPlayer.setDataSource(url);
         }

         mAudioTitle = mAudioRequest.getTitle(getApplicationContext());

         mState = State.Preparing;
         if (!mIsSetupAsForeground){
            setUpAsForeground();
         }

         // Use the media button APIs (if available) to register ourselves
         // for media button events
         MediaButtonHelper.registerMediaButtonEventReceiverCompat(
               mAudioManager, mMediaButtonReceiverComponent);

         // starts preparing the media player in the background. When it's
         // done, it will call our OnPreparedListener (that is, the
         // onPrepared() method on this class, since we set the listener
         // to 'this').
         //
         // Until the media player is prepared, we *cannot* call start() on it!
         Log.d(TAG, "preparingAsync()...");
         mPlayer.prepareAsync();
         
         // If we are streaming from the internet, we want to hold a Wifi lock,
         // which prevents the Wifi radio from going to sleep while the song is
         // playing. If, on the other hand, we are *not* streaming, we want to
         // release the lock if we were holding it before.
         if (isStreaming){ mWifiLock.acquire(); }
         else if (mWifiLock.isHeld()){ mWifiLock.release(); }
      }
      catch (IOException ex) {
         Log.e(TAG, "IOException playing file: " + ex.getMessage());
         ex.printStackTrace();
      }
   }


   @Override
   public void onSeekComplete(MediaPlayer mediaPlayer) {
      Log.d(TAG, "seek complete! " +
              mediaPlayer.getCurrentPosition() +
              " vs " + mPlayer.getCurrentPosition());
      mPlayer.start();
      mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200);
   }

   /** Called when media player is done playing current file. */
   @Override
   public void onCompletion(MediaPlayer player) {
      // The media player finished playing the current file, so
      // we go ahead and start the next.
      if (mPlayerOverride) {
        playAudio(false);
      } else {
        boolean flag = false;
        final int beforeSura = mAudioRequest.getCurrentSura();
        if (mAudioRequest.gotoNextAyah(false)) {
          // we actually switched to a different ayah - so if the
          // sura changed, then play the basmala if the ayah is
          // not the first one (or if we're in sura tawba).
          flag = beforeSura != mAudioRequest.getCurrentSura();
        }
        playAudio(flag);
      }
   }

   /** Called when media player is done preparing. */
   @Override
   public void onPrepared(MediaPlayer player) {
      Log.d(TAG, "okay, prepared!");

      // The media player is done preparing. That means we can start playing!
      mState = State.Playing;
      if (mShouldStop){
         processStopRequest();
         mShouldStop = false;
         return;
      }

      // if gapless and sura changed, get the new data
      if (mAudioRequest.isGapless()){
         if (mGaplessSura != mAudioRequest.getCurrentSura()){
            if (mTimingTask != null){
               mTimingTask.cancel(true);
            }

            String dbPath = mAudioRequest.getGaplessDatabaseFilePath();
            mTimingTask = new ReadGaplessDataTask(dbPath);
            mTimingTask.execute(mAudioRequest.getCurrentSura());
         }
      }

      if (mPlayerOverride || !mAudioRequest.isGapless()) {
        notifyAyahChanged();
      }

      updateNotification(mAudioTitle);
      configAndStartMediaPlayer();
   }

   /** Updates the notification. */
   void updateNotification(String text) {
      mAudioTitle = text;
      mNotificationBuilder.setContentText(text);
      mNotificationManager.notify(NOTIFICATION_ID,
          mNotificationBuilder.build());
   }

   void pauseNotification() {
     mPausedNotificationBuilder.setContentText(mAudioTitle);
     mNotificationManager.notify(NOTIFICATION_ID,
         mPausedNotificationBuilder.build());
   }

   /**
    * Configures service as a foreground service. A foreground service
    * is a service that's doing something the user is actively aware of
    * (such as playing music), and must appear to the user as a notification.
    * That's why we create the notification here.
    */
   private void setUpAsForeground() {
      // clear the "downloading complete" notification (if it exists)
      mNotificationManager.cancel(
          QuranDownloadNotifier.DOWNLOADING_COMPLETE_NOTIFICATION);

      final Context appContext = getApplicationContext();
      final PendingIntent pi = PendingIntent.getActivity(appContext,
         REQUEST_CODE_MAIN, new Intent(appContext, PagerActivity.class),
         PendingIntent.FLAG_UPDATE_CURRENT);

      final PendingIntent previousIntent = PendingIntent.getService(
         appContext, REQUEST_CODE_PREVIOUS, getAudioIntent(this, ACTION_REWIND),
         PendingIntent.FLAG_UPDATE_CURRENT);
     final PendingIntent nextIntent = PendingIntent.getService(
         appContext, REQUEST_CODE_SKIP, getAudioIntent(this, ACTION_SKIP),
         PendingIntent.FLAG_UPDATE_CURRENT);
     final PendingIntent pauseIntent = PendingIntent.getService(
         appContext, REQUEST_CODE_PAUSE, getAudioIntent(this, ACTION_PAUSE),
         PendingIntent.FLAG_UPDATE_CURRENT);
     final PendingIntent resumeIntent = PendingIntent.getService(
         appContext, REQUEST_CODE_RESUME, getAudioIntent(this, ACTION_PLAYBACK),
         PendingIntent.FLAG_UPDATE_CURRENT);
     final PendingIntent stopIntent = PendingIntent.getService(
         appContext, REQUEST_CODE_STOP, getAudioIntent(this, ACTION_STOP),
         PendingIntent.FLAG_UPDATE_CURRENT);

      if (mNotificationBuilder == null) {
        mNotificationBuilder = new NotificationCompat.Builder(appContext);
        mNotificationBuilder
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentTitle(mNotificationName)
            .setContentIntent(pi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_previous, "", previousIntent)
            .addAction(R.drawable.ic_pause, "", pauseIntent)
            .addAction(R.drawable.ic_next, "", nextIntent);
      }
      mNotificationBuilder.setTicker(mAudioTitle).setContentText(mAudioTitle);

     if (mPausedNotificationBuilder == null) {
       mPausedNotificationBuilder = new NotificationCompat.Builder(appContext);
       mPausedNotificationBuilder
           .setSmallIcon(R.drawable.ic_notification)
           .setOngoing(true)
           .setContentTitle(mNotificationName)
           .setContentIntent(pi)
           .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
           .addAction(R.drawable.ic_play,
               getString(R.string.play), resumeIntent)
           .addAction(R.drawable.ic_stop,
               getString(R.string.stop), stopIntent);
     }
     mPausedNotificationBuilder.setContentText(mAudioTitle);

     startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
      mIsSetupAsForeground = true;
   }

   /**
    * Called when there's an error playing media. When this happens, the media
    * player goes to the Error state. We warn the user about the error and
    * reset the media player.
    */
   public boolean onError(MediaPlayer mp, int what, int extra) {
      //Toast.makeText(getApplicationContext(), "Media player error! Resetting.",
      //      Toast.LENGTH_SHORT).show();
      Log.e(TAG, "Error: what=" + String.valueOf(what) +
              ", extra=" + String.valueOf(extra));

      mState = State.Stopped;
      relaxResources(true, true);
      giveUpAudioFocus();
      return true; // true indicates we handled the error
   }

   public void onGainedAudioFocus() {
      //Toast.makeText(getApplicationContext(), "gained audio focus.",
      //        Toast.LENGTH_SHORT).show();
      mAudioFocus = AudioFocus.Focused;

      // restart media player with new focus settings
      if (mState == State.Playing)
         configAndStartMediaPlayer(false);
   }

   public void onLostAudioFocus(boolean canDuck) {
      //Toast.makeText(getApplicationContext(), "lost audio focus." +
      //        (canDuck ? "can duck" : "no duck"), Toast.LENGTH_SHORT).show();
      mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck :
              AudioFocus.NoFocusNoDuck;

      // start/restart/pause media player with new focus settings
      if (mPlayer != null && mPlayer.isPlaying()){
         configAndStartMediaPlayer(false);
      }
   }

   public static Intent getAudioIntent(Context context, String action) {
     final Intent intent = new Intent(context, AudioService.class);
     intent.setAction(action);
     return intent;
   }

   @Override
   public void onDestroy() {
      // Service is being killed, so make sure we release our resources
      mState = State.Stopped;
      relaxResources(true, true);
      giveUpAudioFocus();
   }

   @Override
   public IBinder onBind(Intent arg0) {
      return null;
   }
}
