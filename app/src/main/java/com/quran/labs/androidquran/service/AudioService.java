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
import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler;
import com.quran.labs.androidquran.service.util.AudioFocusHelper;
import com.quran.labs.androidquran.service.util.AudioFocusable;
import com.quran.labs.androidquran.service.util.AudioIntentReceiver;
import com.quran.labs.androidquran.service.util.AudioRequest;
import com.quran.labs.androidquran.service.util.MediaButtonHelper;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.service.util.RepeatInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.AudioUtils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
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
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.NotificationCompat;
import android.util.SparseIntArray;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import timber.log.Timber;

/**
 * Service that handles media playback. This is the Service through which we
 * perform all the media handling in our application. It waits for Intents
 * (which come from our main activity, {@link PagerActivity}, which signal
 * the service to perform specific operations: Play, Pause, Rewind, Skip, etc.
 */
public class AudioService extends Service implements OnCompletionListener,
    OnPreparedListener, OnErrorListener, AudioFocusable, MediaPlayer.OnSeekCompleteListener {

  // These are the Intent actions that we are prepared to handle. Notice that
  // the fact these constants exist in our class is a mere convenience: what
  // really defines the actions our service can handle are the <action> tags
  // in the <intent-filters> tag for our service in AndroidManifest.xml.
  public static final String ACTION_PLAYBACK = "com.quran.labs.androidquran.action.PLAYBACK";
  public static final String ACTION_PLAY = "com.quran.labs.androidquran.action.PLAY";
  public static final String ACTION_PAUSE = "com.quran.labs.androidquran.action.PAUSE";
  public static final String ACTION_STOP = "com.quran.labs.androidquran.action.STOP";
  public static final String ACTION_SKIP = "com.quran.labs.androidquran.action.SKIP";
  public static final String ACTION_REWIND = "com.quran.labs.androidquran.action.REWIND";
  public static final String ACTION_CONNECT = "com.quran.labs.androidquran.action.CONNECT";
  public static final String ACTION_UPDATE_REPEAT = "com.quran.labs.androidquran.action.UPDATE_REPEAT";

  // pending notification request codes
  private static final int REQUEST_CODE_MAIN = 0;
  private static final int REQUEST_CODE_PREVIOUS = 1;
  private static final int REQUEST_CODE_PAUSE = 2;
  private static final int REQUEST_CODE_SKIP = 3;
  private static final int REQUEST_CODE_STOP = 4;
  private static final int REQUEST_CODE_RESUME = 5;

  public static class AudioUpdateIntent {

    public static final String INTENT_NAME = "com.quran.labs.androidquran.audio.AudioUpdate";
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
  public static final String EXTRA_PLAY_INFO = "com.quran.labs.androidquran.PLAY_INFO";

  // ignore the passed in play info if we're already playing
  public static final String EXTRA_IGNORE_IF_PLAYING = "com.quran.labs.androidquran.IGNORE_IF_PLAYING";

  // used to override what is playing now (stop then play)
  public static final String EXTRA_STOP_IF_PLAYING = "com.quran.labs.androidquran.STOP_IF_PLAYING";

  // repeat info
  public static final String EXTRA_VERSE_REPEAT_COUNT = "com.quran.labs.androidquran.VERSE_REPEAT_COUNT";
  public static final String EXTRA_RANGE_REPEAT_COUNT = "com.quran.labs.androidquran.RANGE_REPEAT_COUNT";
  public static final String EXTRA_RANGE_RESTRICT = "com.quran.labs.androidquran.RANGE_RESTRICT";

  // indicates the state our service:
  private enum State {
    Stopped,    // media player is stopped and not prepared to play
    Preparing,  // media player is preparing...
    Playing,    // playback active (media player ready!). (but the media
    // player may actually be  paused in this state if we don't have audio
    // focus. But we stay in this state so that we know we have to resume
    // playback once we get focus back)
    Paused      // playback paused (media player ready!)
  }

  private State mState = State.Stopped;

  // do we have audio focus?
  private enum AudioFocus {
    NoFocusNoDuck,    // we don't have audio focus, and can't duck
    NoFocusCanDuck,   // we don't have focus, but can play at a low volume
    Focused           // we have full audio focus
  }

  private AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

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

  // TODO: Merge these builders into one
  private NotificationCompat.Builder mNotificationBuilder;
  private NotificationCompat.Builder mPausedNotificationBuilder;

  private LocalBroadcastManager mBroadcastManager = null;
  private MediaSessionCompat mMediaSession;

  private int mGaplessSura = 0;
  private int mNotificationColor;
  private Bitmap mNotificationIcon;
  private SparseIntArray mGaplessSuraData = null;
  private AsyncTask<Integer, Void, SparseIntArray> mTimingTask = null;

  public static final int MSG_START_AUDIO = 1;
  public static final int MSG_UPDATE_AUDIO_POS = 2;

  private static class ServiceHandler extends Handler {

    private WeakReference<AudioService> mServiceRef;

    public ServiceHandler(AudioService service) {
      mServiceRef = new WeakReference<>(service);
    }

    @Override
    public void handleMessage(Message msg) {
      final AudioService service = mServiceRef.get();
      if (service == null || msg == null) {
        return;
      }
      if (msg.what == MSG_START_AUDIO) {
        service.configAndStartMediaPlayer();
      } else if (msg.what == MSG_UPDATE_AUDIO_POS) {
        service.updateAudioPlayPosition();
      }
    }
  }

  private Handler mHandler;

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
      mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

      // we want the media player to notify us when it's ready preparing,
      // and when it's done playing:
      mPlayer.setOnPreparedListener(this);
      mPlayer.setOnCompletionListener(this);
      mPlayer.setOnErrorListener(this);
      mPlayer.setOnSeekCompleteListener(this);
    } else {
      Crashlytics.log("resetting mPlayer...");
      mPlayer.reset();
    }
  }

  @Override
  public void onCreate() {
    Timber.i("debug: Creating service");
    mHandler = new ServiceHandler(this);

    mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
        .createWifiLock(WifiManager.WIFI_MODE_FULL, "QuranAudioLock");
    mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

    // create the Audio Focus Helper, if the Audio Focus feature is available
    final Context appContext = getApplicationContext();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
      mAudioFocusHelper = new AudioFocusHelper(appContext, this);
    } else {
      // no focus feature, so we always "have" audio focus
      mAudioFocus = AudioFocus.Focused;
    }

    mMediaButtonReceiverComponent = new ComponentName(this, AudioIntentReceiver.class);
    mBroadcastManager = LocalBroadcastManager.getInstance(appContext);
    mMediaSession = new MediaSessionCompat(appContext, "QuranMediaSession");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      mNotificationColor = getResources().getColor(R.color.audio_notification_color, null);
    } else {
      mNotificationColor = getResources().getColor(R.color.audio_notification_color);
    }
  }

  /**
   * Called when we receive an Intent. When we receive an intent sent to us
   * via startService(), this is the method that gets called. So here we
   * react appropriately depending on the Intent's action, which specifies
   * what is being requested of us.
   */
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      // handle a crash that occurs where intent comes in as null
      if (State.Stopped == mState) {
        mHandler.removeCallbacksAndMessages(null);
        stopSelf();
      }
      return START_NOT_STICKY;
    }

    final String action = intent.getAction();
    if (ACTION_CONNECT.equals(action)) {
      if (State.Stopped == mState) {
        processStopRequest(true);
      } else {
        int sura = -1;
        int ayah = -1;
        int repeatCount = -200;
        int state = AudioUpdateIntent.PLAYING;
        if (State.Paused == mState) {
          state = AudioUpdateIntent.PAUSED;
        }

        if (mAudioRequest != null) {
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
    } else if (ACTION_PLAYBACK.equals(action)) {
      AudioRequest playInfo = intent.getParcelableExtra(EXTRA_PLAY_INFO);
      if (playInfo != null) {
        if (State.Stopped == mState ||
            !intent.getBooleanExtra(EXTRA_IGNORE_IF_PLAYING, false)) {
          mAudioRequest = playInfo;
          Crashlytics.log("audio request has changed...");
        }
      }

      if (intent.getBooleanExtra(EXTRA_STOP_IF_PLAYING, false)) {
        if (mPlayer != null) {
          mPlayer.stop();
        }
        mState = State.Stopped;
        Crashlytics.log("stop if playing...");
      }

      processTogglePlaybackRequest();
    } else if (ACTION_PLAY.equals(action)) {
      processPlayRequest();
    } else if (ACTION_PAUSE.equals(action)) {
      processPauseRequest();
    } else if (ACTION_SKIP.equals(action)) {
      processSkipRequest();
    } else if (ACTION_STOP.equals(action)) {
      processStopRequest();
    } else if (ACTION_REWIND.equals(action)) {
      processRewindRequest();
    } else if (ACTION_UPDATE_REPEAT.equals(action)) {
      if (mAudioRequest != null) {
        // set the repeat info if applicable
        final int verseRepeatCount = intent
            .getIntExtra(EXTRA_VERSE_REPEAT_COUNT, mAudioRequest.getVerseRepeatCount());
        mAudioRequest.setVerseRepeatCount(verseRepeatCount);

        // set the range repeat count
        final int rangeRepeatCount = intent
            .getIntExtra(EXTRA_RANGE_REPEAT_COUNT, mAudioRequest.getRangeRepeatCount());
        mAudioRequest.setRangeRepeatCount(rangeRepeatCount);

        // set the enforce range flag
        if (intent.hasExtra(EXTRA_RANGE_RESTRICT)) {
          final boolean enforceRange = intent.getBooleanExtra(EXTRA_RANGE_RESTRICT, false);
          mAudioRequest.setEnforceBounds(enforceRange);
        }
      }
    }

    // we don't want the service to restart if killed
    return START_NOT_STICKY;
  }

  private class ReadGaplessDataTask extends AsyncTask<Integer, Void, SparseIntArray> {

    private int mSura = 0;
    private String mDatabasePath = null;

    public ReadGaplessDataTask(String database) {
      mDatabasePath = database;
    }

    @Override
    protected SparseIntArray doInBackground(Integer... params) {
      int sura = params[0];
      mSura = sura;

      SuraTimingDatabaseHandler db = SuraTimingDatabaseHandler.getDatabaseHandler(mDatabasePath);
      SparseIntArray map = null;

      Cursor cursor = null;
      try {
        cursor = db.getAyahTimings(sura);
        Timber.d("got cursor of data");

        if (cursor != null && cursor.moveToFirst()) {
          map = new SparseIntArray();
          do {
            int ayah = cursor.getInt(1);
            int time = cursor.getInt(2);
            map.put(ayah, time);
          }
          while (cursor.moveToNext());
        }
      } finally {
        DatabaseUtils.closeCursor(cursor);
      }
      return map;
    }

    @Override
    protected void onPostExecute(SparseIntArray map) {
      mGaplessSura = mSura;
      mGaplessSuraData = map;
      mTimingTask = null;
    }
  }

  private int getSeekPosition(boolean isRepeating) {
    if (mAudioRequest == null) {
      return -1;
    }

    if (mGaplessSura == mAudioRequest.getCurrentSura()) {
      if (mGaplessSuraData != null) {
        int ayah = mAudioRequest.getCurrentAyah();
        Integer time = mGaplessSuraData.get(ayah);
        if (ayah == 1 && !isRepeating) {
          return mGaplessSuraData.get(0);
        }
        return time;
      }
    }
    return -1;
  }

  private void updateAudioPlayPosition() {
    Timber.d("updateAudioPlayPosition");

    if (mAudioRequest == null) {
      return;
    }
    if (mPlayer != null || mGaplessSuraData == null) {
      int sura = mAudioRequest.getCurrentSura();
      int ayah = mAudioRequest.getCurrentAyah();

      int updatedAyah = ayah;
      int maxAyahs = QuranInfo.getNumAyahs(sura);

      if (sura != mGaplessSura) {
        return;
      }
      int pos = mPlayer.getCurrentPosition();
      Integer ayahTime = mGaplessSuraData.get(ayah);
      Timber.d("updateAudioPlayPosition: %d:%d, currently at %d vs expected at %d",
          sura, ayah, pos, ayahTime);

      if (ayahTime > pos) {
        int iterAyah = ayah;
        while (--iterAyah > 0) {
          ayahTime = mGaplessSuraData.get(iterAyah);
          if (ayahTime <= pos) {
            updatedAyah = iterAyah;
            break;
          } else {
            updatedAyah--;
          }
        }
      } else {
        int iterAyah = ayah;
        while (++iterAyah <= maxAyahs) {
          ayahTime = mGaplessSuraData.get(iterAyah);
          if (ayahTime > pos) {
            updatedAyah = iterAyah - 1;
            break;
          } else {
            updatedAyah++;
          }
        }
      }

      Timber.d("updateAudioPlayPosition: %d:%d, decided ayah should be: %d",
          sura, ayah, updatedAyah);

      if (updatedAyah != ayah) {
        ayahTime = mGaplessSuraData.get(ayah);
        if (Math.abs(pos - ayahTime) < 150) {
          // shouldn't change ayahs if the delta is just 150ms...
          mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150);
          return;
        }

        QuranAyah nextAyah = mAudioRequest.setCurrentAyah(sura, updatedAyah);
        if (nextAyah == null) {
          processStopRequest();
          return;
        } else if (nextAyah.getSura() != sura ||
            nextAyah.getAyah() != updatedAyah) {
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
        updateNotification();
      } else {
        // if we have end of sura info and we bypassed end of sura
        // line, switch the sura.
        ayahTime = mGaplessSuraData.get(999);
        if (ayahTime > 0 && pos >= ayahTime) {
          QuranAyah repeat = mAudioRequest.setCurrentAyah(sura + 1, 1);
          if (repeat != null && repeat.getSura() == sura) {
            // remove any messages currently in the queue
            mHandler.removeCallbacksAndMessages(null);

            // jump back to the ayah we should repeat and play it
            pos = getSeekPosition(false);
            mPlayer.seekTo(pos);
          } else {
            playAudio(true);
          }
          return;
        }
      }

      notifyAyahChanged();

      if (maxAyahs >= (updatedAyah + 1)) {
        Integer t = mGaplessSuraData.get(updatedAyah + 1);
        t = t - mPlayer.getCurrentPosition();
        Timber.d("updateAudioPlayPosition postingDelayed after: %d", t);

        if (t < 100) {
          t = 100;
        } else if (t > 10000) {
          t = 10000;
        }
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, t);
      }
      // if we're on the last ayah, don't do anything - let the file
      // complete on its own to avoid getCurrentPosition() bugs.
    }
  }

  private void processTogglePlaybackRequest() {
    if (State.Paused == mState || State.Stopped == mState) {
      processPlayRequest();
    } else {
      processPauseRequest();
    }
  }

  private void processPlayRequest() {
    if (mAudioRequest == null) {
      return;
    }
    tryToGetAudioFocus();

    // actually play the file

    if (State.Stopped == mState) {
      if (mAudioRequest.isGapless()) {
        if (mTimingTask != null) {
          mTimingTask.cancel(true);
        }
        String dbPath = mAudioRequest.getGaplessDatabaseFilePath();
        mTimingTask = new ReadGaplessDataTask(dbPath);
        mTimingTask.execute(mAudioRequest.getCurrentSura());
      }

      // If we're stopped, just go ahead to the next file and start playing
      playAudio(mAudioRequest.getCurrentSura() == 9 && mAudioRequest.getCurrentAyah() == 1);
    } else if (State.Paused == mState) {
      // If we're paused, just continue playback and restore the
      // 'foreground service' state.
      mState = State.Playing;
      setUpAsForeground();
      configAndStartMediaPlayer(false);
      notifyAudioStatus(AudioUpdateIntent.PLAYING);
    }
  }

  private void processPauseRequest() {
    if (State.Playing == mState) {
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
    } else if (State.Stopped == mState) {
      // if we get a pause while we're already stopped, it means we likely woke up because
      // of AudioIntentReceiver, so just stop in this case.
      stopSelf();
    }
  }

  private void processRewindRequest() {
    if (State.Playing == mState || State.Paused == mState) {
      int seekTo = 0;
      int pos = mPlayer.getCurrentPosition();
      if (mAudioRequest.isGapless()) {
        seekTo = getSeekPosition(true);
        pos = pos - seekTo;
      }

      if (pos > 1500 && !mPlayerOverride) {
        mPlayer.seekTo(seekTo);
        mState = State.Playing; // in case we were paused
      } else {
        tryToGetAudioFocus();
        int sura = mAudioRequest.getCurrentSura();
        mAudioRequest.gotoPreviousAyah();
        if (mAudioRequest.isGapless() && sura == mAudioRequest.getCurrentSura()) {
          int timing = getSeekPosition(true);
          if (timing > -1) {
            mPlayer.seekTo(timing);
          }
          updateNotification();
          mState = State.Playing; // in case we were paused
          return;
        }
        playAudio();
      }
    }
  }

  private void processSkipRequest() {
    if (mAudioRequest == null) {
      return;
    }
    if (State.Playing == mState || State.Paused == mState) {
      if (mPlayerOverride) {
        playAudio(false);
      } else {
        final int sura = mAudioRequest.getCurrentSura();
        tryToGetAudioFocus();
        mAudioRequest.gotoNextAyah(true);
        if (mAudioRequest.isGapless() && sura == mAudioRequest.getCurrentSura()) {
          int timing = getSeekPosition(false);
          if (timing > -1) {
            mPlayer.seekTo(timing);
            mState = State.Playing; // in case we were paused
          }
          updateNotification();
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

    if (State.Preparing == mState) {
      mShouldStop = true;
      relaxResources(false, true);
    }

    if (force || State.Playing == mState || State.Paused == mState) {
      mState = State.Stopped;

      // let go of all resources...
      relaxResources(true, true);
      giveUpAudioFocus();

      // service is no longer necessary. Will be started again if needed.
      mHandler.removeCallbacksAndMessages(null);
      stopSelf();

      // stop async task if it's running
      if (mTimingTask != null) {
        mTimingTask.cancel(true);
      }

      // tell the ui we've stopped
      notifyAudioStatus(AudioUpdateIntent.STOPPED);
    }
  }

  private void notifyAyahChanged() {
    if (mAudioRequest != null) {
      Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
      updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.PLAYING);
      updateIntent.putExtra(AudioUpdateIntent.SURA, mAudioRequest.getCurrentSura());
      updateIntent.putExtra(AudioUpdateIntent.AYAH, mAudioRequest.getCurrentAyah());
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
  private void relaxResources(boolean releaseMediaPlayer, boolean stopForeground) {
    if (stopForeground) {
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
    if (mWifiLock.isHeld()) {
      mWifiLock.release();
    }
  }

  private void giveUpAudioFocus() {
    if (mAudioFocus == AudioFocus.Focused &&
        mAudioFocusHelper != null && mAudioFocusHelper.abandonFocus()) {
      mAudioFocus = AudioFocus.NoFocusNoDuck;
    }
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

  private void configAndStartMediaPlayer(boolean canSeek) {
    Timber.d("configAndStartMediaPlayer()");
    if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
      // If we don't have audio focus and can't duck, we have to pause,
      // even if mState is State.Playing. But we stay in the Playing state
      // so that we know we have to resume playback once we get focus back.
      if (mPlayer.isPlaying()) {
        mPlayer.pause();
      }
      return;
    } else if (mAudioFocus == AudioFocus.NoFocusCanDuck) {
      // we'll be relatively quiet
      mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);
    } else {
      mPlayer.setVolume(1.0f, 1.0f);
    } // we can be loud

    if (mShouldStop) {
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

    Timber.d("checking if playing...");
    if (!mPlayer.isPlaying()) {
      if (canSeek && mAudioRequest.isGapless()) {
        int timing = getSeekPosition(false);
        if (timing != -1) {
          Timber.d("got timing: %d, seeking and updating later...", timing);
          mPlayer.seekTo(timing);
          return;
        } else {
          Timber.d("no timing data yet, will try again...");
          // try to play again after 200 ms
          mHandler.sendEmptyMessageDelayed(MSG_START_AUDIO, 200);
          return;
        }
      } else if (mAudioRequest.isGapless()) {
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200);
      }

      mPlayer.start();
    }
  }

  private void tryToGetAudioFocus() {
    if (mAudioFocus != AudioFocus.Focused &&
        mAudioFocusHelper != null && mAudioFocusHelper.requestFocus()) {
      mAudioFocus = AudioFocus.Focused;
    }
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
      String url = mAudioRequest == null ? null : mAudioRequest.getUrl();
      if (mAudioRequest == null || url == null) {
        Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
        updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.STOPPED);
        mBroadcastManager.sendBroadcast(updateIntent);

        processStopRequest(true); // stop everything!
        return;
      }

      final boolean isStreaming = url.startsWith("http:") || url.startsWith("https:");
      if (!isStreaming) {
        File f = new File(url);
        if (!f.exists()) {
          Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
          updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.STOPPED);
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

      Timber.d("okay, we are preparing to play - streaming is: %b", isStreaming);
      createMediaPlayerIfNeeded();
      mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      try {
        boolean playUrl = true;
        if (overrideResource != 0) {
          AssetFileDescriptor afd = getResources().openRawResourceFd(overrideResource);
          if (afd != null) {
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mPlayerOverride = true;
            playUrl = false;
          }
        }

        if (playUrl) {
          overrideResource = 0;
          mPlayer.setDataSource(url);
        }
      } catch (IllegalStateException ie) {
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

      mState = State.Preparing;
      if (!mIsSetupAsForeground) {
        setUpAsForeground();
      }

      // Use the media button APIs (if available) to register ourselves
      // for media button events
      MediaButtonHelper.registerMediaButtonEventReceiverCompat(mAudioManager,
          mMediaButtonReceiverComponent);

      // starts preparing the media player in the background. When it's
      // done, it will call our OnPreparedListener (that is, the
      // onPrepared() method on this class, since we set the listener
      // to 'this').
      //
      // Until the media player is prepared, we *cannot* call start() on it!
      Timber.d("preparingAsync()...");
      Crashlytics.log("prepareAsync: " + overrideResource + ", " + url);
      mPlayer.prepareAsync();

      // If we are streaming from the internet, we want to hold a Wifi lock,
      // which prevents the Wifi radio from going to sleep while the song is
      // playing. If, on the other hand, we are *not* streaming, we want to
      // release the lock if we were holding it before.
      if (isStreaming) {
        mWifiLock.acquire();
      } else if (mWifiLock.isHeld()) {
        mWifiLock.release();
      }
    } catch (IOException ex) {
      Timber.e("IOException playing file: %s", ex.getMessage());
      ex.printStackTrace();
    }
  }

  @Override
  public void onSeekComplete(MediaPlayer mediaPlayer) {
    Timber.d("seek complete! %d vs %d",
        mediaPlayer.getCurrentPosition(), mPlayer.getCurrentPosition());
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
    Timber.d("okay, prepared!");

    // The media player is done preparing. That means we can start playing!
    mState = State.Playing;
    if (mShouldStop) {
      processStopRequest();
      mShouldStop = false;
      return;
    }

    // if gapless and sura changed, get the new data
    if (mAudioRequest.isGapless()) {
      if (mGaplessSura != mAudioRequest.getCurrentSura()) {
        if (mTimingTask != null) {
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

    updateNotification();
    configAndStartMediaPlayer();
  }

  /** Updates the notification. */
  void updateNotification() {
    mNotificationBuilder.setContentText(mAudioRequest.getTitle(getApplicationContext()));
    mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
  }

  void pauseNotification() {
    mPausedNotificationBuilder.setContentText(mAudioRequest.getTitle(getApplicationContext()));
    mNotificationManager.notify(NOTIFICATION_ID, mPausedNotificationBuilder.build());
  }

  /**
   * Configures service as a foreground service. A foreground service
   * is a service that's doing something the user is actively aware of
   * (such as playing music), and must appear to the user as a notification.
   * That's why we create the notification here.
   */
  private void setUpAsForeground() {
    // clear the "downloading complete" notification (if it exists)
    mNotificationManager.cancel(QuranDownloadNotifier.DOWNLOADING_COMPLETE_NOTIFICATION);

    final Context appContext = getApplicationContext();
    final PendingIntent pi = PendingIntent.getActivity(
        appContext, REQUEST_CODE_MAIN, new Intent(appContext, PagerActivity.class),
        PendingIntent.FLAG_UPDATE_CURRENT);

    final PendingIntent previousIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_PREVIOUS, AudioUtils.getAudioIntent(this, ACTION_REWIND),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent nextIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_SKIP, AudioUtils.getAudioIntent(this, ACTION_SKIP),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent pauseIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_PAUSE, AudioUtils.getAudioIntent(this, ACTION_PAUSE),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent resumeIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_RESUME, AudioUtils.getAudioIntent(this, ACTION_PLAYBACK),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent stopIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_STOP, AudioUtils.getAudioIntent(this, ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT);

    // if the notification icon is null, let's try to build it
    if (mNotificationIcon == null) {
      try {
        Resources resources = appContext.getResources();
        Bitmap logo = BitmapFactory.decodeResource(resources, R.drawable.icon);
        int iconWidth = logo.getWidth();
        int iconHeight = logo.getHeight();
        ColorDrawable cd = new ColorDrawable(
            resources.getColor(R.color.audio_notification_background_color));
        Bitmap bitmap = Bitmap.createBitmap(iconWidth * 2, iconHeight * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        cd.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        cd.draw(canvas);
        canvas.drawBitmap(logo, iconWidth / 2, iconHeight / 2, null);
        mNotificationIcon = bitmap;
      } catch (OutOfMemoryError oomError) {
        // if this happens, we need to handle it gracefully, since it's not crash worthy.
        Crashlytics.logException(oomError);
      }
    }

    // if we couldn't get the notification icon, we'll use the non-MediaStyle notification.
    boolean emptyTitles = mNotificationIcon == null;
    String audioTitle = mAudioRequest.getTitle(getApplicationContext());
    if (mNotificationBuilder == null) {
      mNotificationBuilder = new NotificationCompat.Builder(appContext);
      mNotificationBuilder
          .setSmallIcon(R.drawable.ic_notification)
          .setColor(mNotificationColor)
          .setOngoing(true)
          .setContentTitle(getString(R.string.app_name))
          .setContentIntent(pi)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .addAction(R.drawable.ic_previous,
              emptyTitles ? "" : getString(R.string.previous), previousIntent)
          .addAction(R.drawable.ic_pause, emptyTitles ? "" : getString(R.string.pause), pauseIntent)
          .addAction(R.drawable.ic_next, emptyTitles ? "" : getString(R.string.next), nextIntent)
          .setShowWhen(false);
      if (mNotificationIcon != null) {
        mNotificationBuilder.setStyle(new NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(new int[] { 0, 1, 2 })
            .setMediaSession(mMediaSession.getSessionToken()))
            .setLargeIcon(mNotificationIcon);
      }
    }
    mNotificationBuilder.setTicker(audioTitle);
    mNotificationBuilder.setContentText(audioTitle);

    if (mPausedNotificationBuilder == null) {
      mPausedNotificationBuilder = new NotificationCompat.Builder(appContext);
      mPausedNotificationBuilder
          .setSmallIcon(R.drawable.ic_notification)
          .setColor(mNotificationColor)
          .setOngoing(true)
          .setContentTitle(getString(R.string.app_name))
          .setContentIntent(pi)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .addAction(R.drawable.ic_play, emptyTitles ? "" : getString(R.string.play), resumeIntent)
          .addAction(R.drawable.ic_stop, emptyTitles ? "" : getString(R.string.stop), stopIntent)
          .setShowWhen(false);
      if (mNotificationIcon != null) {
        mPausedNotificationBuilder
            .setLargeIcon(mNotificationIcon)
            .setStyle(new NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(new int[] { 0, 1 })
            .setMediaSession(mMediaSession.getSessionToken()));
      }
    }
    mPausedNotificationBuilder.setContentText(audioTitle);

    startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
    mIsSetupAsForeground = true;
  }

  /**
   * Called when there's an error playing media. When this happens, the media
   * player goes to the Error state. We warn the user about the error and
   * reset the media player.
   */
  public boolean onError(MediaPlayer mp, int what, int extra) {
    Timber.e("Error: what=%s, extra=%s", String.valueOf(what), String.valueOf(extra));

    mState = State.Stopped;
    relaxResources(true, true);
    giveUpAudioFocus();
    return true; // true indicates we handled the error
  }

  public void onGainedAudioFocus() {
    mAudioFocus = AudioFocus.Focused;

    // restart media player with new focus settings
    if (State.Playing == mState) {
      configAndStartMediaPlayer(false);
    }
  }

  public void onLostAudioFocus(boolean canDuck) {
    mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;

    // start/restart/pause media player with new focus settings
    if (mPlayer != null && mPlayer.isPlaying()) {
      configAndStartMediaPlayer(false);
    }
  }

  @Override
  public void onDestroy() {
    // Service is being killed, so make sure we release our resources
    mHandler.removeCallbacksAndMessages(null);
    mState = State.Stopped;
    relaxResources(true, true);
    giveUpAudioFocus();
    MediaButtonHelper.unregisterMediaButtonEventReceiverCompat(mAudioManager,
        mMediaButtonReceiverComponent);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }
}
