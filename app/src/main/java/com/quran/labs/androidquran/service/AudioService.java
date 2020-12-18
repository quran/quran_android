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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.SparseIntArray;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.app.NotificationCompat.MediaStyle;
import androidx.media.session.MediaButtonReceiver;
import com.quran.data.core.QuranInfo;
import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.audio.AudioPlaybackInfo;
import com.quran.labs.androidquran.dao.audio.AudioRequest;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDisplayData;
import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler;
import com.quran.labs.androidquran.extension.SuraAyahExtensionKt;
import com.quran.labs.androidquran.presenter.audio.service.AudioQueue;
import com.quran.labs.androidquran.service.util.AudioFocusHelper;
import com.quran.labs.androidquran.service.util.AudioFocusable;
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.AudioUtils;
import com.quran.labs.androidquran.util.NotificationChannelUtil;
import io.reactivex.Maybe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.IOException;
import javax.inject.Inject;
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
  private MediaPlayer player = null;

  // are we playing an override file (basmalah/isti3atha)
  private boolean playerOverride;

  // our AudioFocusHelper object, if it's available (it's available on SDK
  // level >= 8). If not available, this will be null. Always check for null
  // before using!
  private AudioFocusHelper audioFocusHelper = null;

  // object representing the current playing request
  private AudioRequest audioRequest = null;

  // the playback queue
  private AudioQueue audioQueue = null;

  // so user can pass in a serializable LegacyAudioRequest to the intent
  public static final String EXTRA_PLAY_INFO = "com.quran.labs.androidquran.PLAY_INFO";

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

  private State state = State.Stopped;

  // do we have audio focus?
  private enum AudioFocus {
    NoFocusNoDuck,    // we don't have audio focus, and can't duck
    NoFocusCanDuck,   // we don't have focus, but can play at a low volume
    Focused           // we have full audio focus
  }

  private AudioFocus audioFocus = AudioFocus.NoFocusNoDuck;

  // are we already in the foreground
  private boolean isSetupAsForeground = false;

  // should we stop (after preparing is done) or not
  private boolean shouldStop = false;

  // Wifi lock that we hold when streaming files from the internet,
  // in order to prevent the device from shutting off the Wifi radio
  private WifiLock wifiLock;

  // The ID we use for the notification (the onscreen alert that appears
  // at the notification area at the top of the screen as an icon -- and
  // as text as well if the user expands the notification area).
  private final int NOTIFICATION_ID = Constants.NOTIFICATION_ID_AUDIO_PLAYBACK;
  private static final String NOTIFICATION_CHANNEL_ID = Constants.AUDIO_CHANNEL;

  private NotificationManager notificationManager;
  private NotificationCompat.Builder notificationBuilder;
  private NotificationCompat.Builder pausedNotificationBuilder;
  private boolean didSetNotificationIconOnNotificationBuilder;

  private LocalBroadcastManager broadcastManager = null;
  private BroadcastReceiver noisyAudioStreamReceiver;
  private MediaSessionCompat mediaSession;

  private int gaplessSura = 0;
  private int notificationColor;
  // read by service thread, written on the I/O thread once
  private volatile Bitmap notificationIcon;
  private Bitmap displayIcon;
  private SparseIntArray gaplessSuraData = null;
  private AsyncTask<Integer, Void, SparseIntArray> timingTask = null;
  private final CompositeDisposable compositeDisposable = new CompositeDisposable();

  @Inject QuranInfo quranInfo;
  @Inject QuranDisplayData quranDisplayData;
  @Inject AudioUtils audioUtils;

  private static final int MSG_INCOMING = 1;
  private static final int MSG_START_AUDIO = 2;
  private static final int MSG_UPDATE_AUDIO_POS = 3;

  private class ServiceHandler extends Handler {

    ServiceHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_INCOMING && msg.obj != null) {
        Intent intent = (Intent) msg.obj;
        handleIntent(intent);
      } else if (msg.what == MSG_START_AUDIO) {
        configAndStartMediaPlayer();
      } else if (msg.what == MSG_UPDATE_AUDIO_POS) {
        updateAudioPlayPosition();
      }
    }
  }

  private Looper serviceLooper;
  private ServiceHandler serviceHandler;

  /**
   * Makes sure the media player exists and has been reset. This will create
   * the media player if needed, or reset the existing media player if one
   * already exists.
   */
  private void createMediaPlayerIfNeeded() {
    if (player == null) {
      player = new MediaPlayer();

      // Make sure the media player will acquire a wake-lock while playing.
      // If we don't do that, the CPU might go to sleep while the song is
      // playing, causing playback to stop.
      //
      // Remember that to use this, we have to declare the
      // android.permission.WAKE_LOCK permission in AndroidManifest.xml.
      player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

      // we want the media player to notify us when it's ready preparing,
      // and when it's done playing:
      player.setOnPreparedListener(this);
      player.setOnCompletionListener(this);
      player.setOnErrorListener(this);
      player.setOnSeekCompleteListener(this);

      mediaSession.setActive(true);
    } else {
      Timber.d("resetting player...");
      player.reset();
    }
  }

  @Override
  public void onCreate() {
    Timber.i("debug: Creating service");
    final HandlerThread thread = new HandlerThread("AyahAudioService",
        android.os.Process.THREAD_PRIORITY_BACKGROUND);
    thread.start();

    // Get the HandlerThread's Looper and use it for our Handler
    serviceLooper = thread.getLooper();
    serviceHandler = new ServiceHandler(serviceLooper);

    final Context appContext = getApplicationContext();
    ((QuranApplication) appContext).getApplicationComponent().inject(this);
    wifiLock = ((WifiManager) appContext.getSystemService(Context.WIFI_SERVICE))
        .createWifiLock(WifiManager.WIFI_MODE_FULL, "QuranAudioLock");
    notificationManager = (NotificationManager) appContext.getSystemService(NOTIFICATION_SERVICE);

    // create the Audio Focus Helper, if the Audio Focus feature is available
    audioFocusHelper = new AudioFocusHelper(appContext, this);

    broadcastManager = LocalBroadcastManager.getInstance(appContext);
    noisyAudioStreamReceiver = new NoisyAudioStreamReceiver();
    registerReceiver(
        noisyAudioStreamReceiver,
        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

    ComponentName receiver = new ComponentName(this, MediaButtonReceiver.class);
    mediaSession = new MediaSessionCompat(appContext, "QuranMediaSession", receiver, null);
    mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    mediaSession.setCallback(new MediaSessionCallback(), serviceHandler);

    final String channelName = getString(R.string.notification_channel_audio);
    NotificationChannelUtil.INSTANCE.setupNotificationChannel(
        notificationManager, NOTIFICATION_CHANNEL_ID, channelName);

    notificationColor = ContextCompat.getColor(this, R.color.audio_notification_color);
    try {
      // for Android Wear, use a 1x1 Bitmap with the notification color
      displayIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(displayIcon);
      canvas.drawColor(notificationColor);
    } catch (OutOfMemoryError oom) {
      Timber.e(oom);
    }

    compositeDisposable.add(
        Maybe.fromCallable(this::generateNotificationIcon)
          .subscribeOn(Schedulers.io())
          .subscribe(bitmap -> notificationIcon = bitmap));
  }

  private class MediaSessionCallback extends MediaSessionCompat.Callback {

    @Override
    public void onPlay() {
      processPlayRequest();
    }

    @Override
    public void onSkipToNext() {
      processSkipRequest();
    }

    @Override
    public void onSkipToPrevious() {
      processRewindRequest();
    }

    @Override
    public void onPause() {
      processPauseRequest();
    }

    @Override
    public void onStop() {
      processStopRequest();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      // handle a crash that occurs where intent comes in as null
      if (State.Stopped == state) {
        serviceHandler.removeCallbacksAndMessages(null);
        stopSelf();
      }
    } else {
      final String action = intent.getAction();
      if (ACTION_PLAYBACK.equals(action) || Intent.ACTION_MEDIA_BUTTON.equals(action)) {
        // go to the foreground as quickly as possible.
        setUpAsForeground();
      }

      final Message message = serviceHandler.obtainMessage(MSG_INCOMING, intent);
      serviceHandler.sendMessage(message);
    }
    return START_NOT_STICKY;
  }

  private void handleIntent(Intent intent) {
    final String action = intent.getAction();
    if (ACTION_CONNECT.equals(action)) {
      if (State.Stopped == state) {
        processStopRequest(true);
      } else {
        int sura = -1;
        int ayah = -1;
        int repeatCount = -200;
        int state = AudioUpdateIntent.PLAYING;
        if (State.Paused == this.state) {
          state = AudioUpdateIntent.PAUSED;
        }

        if (audioQueue != null && audioRequest != null) {
          sura = audioQueue.getCurrentSura();
          ayah = audioQueue.getCurrentAyah();

          repeatCount = audioRequest.getRepeatInfo();
        }

        Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
        updateIntent.putExtra(AudioUpdateIntent.STATUS, state);
        updateIntent.putExtra(AudioUpdateIntent.SURA, sura);
        updateIntent.putExtra(AudioUpdateIntent.AYAH, ayah);
        updateIntent.putExtra(AudioUpdateIntent.REPEAT_COUNT, repeatCount);
        updateIntent.putExtra(AudioUpdateIntent.REQUEST, audioRequest);

        broadcastManager.sendBroadcast(updateIntent);
      }
    } else if (ACTION_PLAYBACK.equals(action)) {
      AudioRequest playInfo = intent.getParcelableExtra(EXTRA_PLAY_INFO);
      if (playInfo != null) {
        audioRequest = playInfo;

        final SuraAyah start = audioRequest.getStart();
        final boolean basmallah = !playInfo.isGapless() &&
            SuraAyahExtensionKt.requiresBasmallah(start);
        audioQueue = new AudioQueue(quranInfo, audioRequest,
            new AudioPlaybackInfo(start, 1, 1, basmallah));
        Timber.d("audio request has changed...");

        if (player != null) {
          player.stop();
        }
        state = State.Stopped;
        Timber.d("stop if playing...");
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
      final AudioRequest playInfo = intent.getParcelableExtra(EXTRA_PLAY_INFO);
      if (playInfo != null && audioQueue != null) {
        audioQueue = audioQueue.withUpdatedAudioRequest(playInfo);
        audioRequest = playInfo;
      }
    } else {
      MediaButtonReceiver.handleIntent(mediaSession, intent);
    }
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
      } catch (SQLException se) {
        // don't crash the app if the database is corrupt
        Timber.e(se);
      } finally {
        DatabaseUtils.closeCursor(cursor);
      }
      return map;
    }

    @Override
    protected void onPostExecute(SparseIntArray map) {
      gaplessSura = mSura;
      gaplessSuraData = map;
      timingTask = null;
    }
  }

  private int getSeekPosition(boolean isRepeating) {
    if (audioRequest == null) {
      return -1;
    }

    if (gaplessSura == audioQueue.getCurrentSura()) {
      if (gaplessSuraData != null) {
        int ayah = audioQueue.getCurrentAyah();
        Integer time = gaplessSuraData.get(ayah);
        if (ayah == 1 && !isRepeating) {
          return gaplessSuraData.get(0);
        }
        return time;
      }
    }
    return -1;
  }

  private void updateAudioPlayPosition() {
    Timber.d("updateAudioPlayPosition");

    if (audioRequest == null) {
      return;
    }
    if (player != null || gaplessSuraData == null) {
      int sura = audioQueue.getCurrentSura();
      int ayah = audioQueue.getCurrentAyah();

      int updatedAyah = ayah;
      int maxAyahs = quranInfo.getNumberOfAyahs(sura);

      if (sura != gaplessSura) {
        return;
      }
      setState(PlaybackStateCompat.STATE_PLAYING);
      int pos = player.getCurrentPosition();
      Integer ayahTime = gaplessSuraData.get(ayah);
      Timber.d("updateAudioPlayPosition: %d:%d, currently at %d vs expected at %d",
          sura, ayah, pos, ayahTime);

      if (ayahTime > pos) {
        int iterAyah = ayah;
        while (--iterAyah > 0) {
          ayahTime = gaplessSuraData.get(iterAyah);
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
          ayahTime = gaplessSuraData.get(iterAyah);
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
        ayahTime = gaplessSuraData.get(ayah);
        if (Math.abs(pos - ayahTime) < 150) {
          // shouldn't change ayahs if the delta is just 150ms...
          serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150);
          return;
        }

        boolean success = audioQueue.playAt(sura, updatedAyah, false);
        final int nextSura = audioQueue.getCurrentSura();
        final int nextAyah = audioQueue.getCurrentAyah();
        if (!success) {
          processStopRequest();
          return;
        } else if (nextSura != sura || nextAyah != updatedAyah) {
          // remove any messages currently in the queue
          serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS);

          // if the ayah hasn't changed, we're repeating the ayah,
          // otherwise, we're repeating a range. this variable is
          // what determines whether or not we replay the basmallah.
          final boolean ayahRepeat = (ayah == nextAyah && sura == nextSura);

          if (ayahRepeat) {
            // jump back to the ayah we should repeat and play it
            pos = getSeekPosition(true);
            player.seekTo(pos);
          } else {
            // we're repeating into a different sura
            final boolean flag = sura != audioQueue.getCurrentSura();
            playAudio(flag);
          }
          return;
        }

        // moved on to next ayah
        updateNotification();
      } else {
        // if we have end of sura info and we bypassed end of sura
        // line, switch the sura.
        ayahTime = gaplessSuraData.get(999);
        if (ayahTime > 0 && pos >= ayahTime) {
          boolean success = audioQueue.playAt(sura + 1, 1, false);
          if (success && audioQueue.getCurrentSura() == sura) {
            // remove any messages currently in the queue
            serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS);

            // jump back to the ayah we should repeat and play it
            pos = getSeekPosition(false);
            player.seekTo(pos);
          } else if (!success) {
            processStopRequest();
          } else {
            playAudio(true);
          }
          return;
        }
      }

      notifyAyahChanged();

      if (maxAyahs >= (updatedAyah + 1)) {
        int t = gaplessSuraData.get(updatedAyah + 1) - player.getCurrentPosition();
        Timber.d("updateAudioPlayPosition postingDelayed after: %d", t);

        if (t < 100) {
          t = 100;
        } else if (t > 10000) {
          t = 10000;
        }
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, t);
      } else if (maxAyahs == updatedAyah) {
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150);
      }
      // if we're on the last ayah, don't do anything - let the file
      // complete on its own to avoid getCurrentPosition() bugs.
    }
  }

  private void processTogglePlaybackRequest() {
    if (State.Paused == state || State.Stopped == state) {
      processPlayRequest();
    } else {
      processPauseRequest();
    }
  }

  private void processPlayRequest() {
    if (audioRequest == null) {
      // no audio request, what can we do?
      relaxResources(true, true);
      return;
    }
    tryToGetAudioFocus();

    // actually play the file

    if (State.Stopped == state) {
      if (audioRequest.isGapless()) {
        if (timingTask != null) {
          timingTask.cancel(true);
        }
        String dbPath = audioRequest.getAudioPathInfo().getGaplessDatabase();
        timingTask = new ReadGaplessDataTask(dbPath);
        timingTask.execute(audioQueue.getCurrentSura());
      }

      // If we're stopped, just go ahead to the next file and start playing
      playAudio(audioQueue.getCurrentSura() == 9 && audioQueue.getCurrentAyah() == 1);
    } else if (State.Paused == state) {
      // If we're paused, just continue playback and restore the
      // 'foreground service' state.
      state = State.Playing;
      if (!isSetupAsForeground) {
        setUpAsForeground();
      }
      configAndStartMediaPlayer(false);
      notifyAudioStatus(AudioUpdateIntent.PLAYING);
    }
  }

  private void processPauseRequest() {
    if (State.Playing == state) {
      // Pause media player and cancel the 'foreground service' state.
      state = State.Paused;
      serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS);
      player.pause();
      setState(PlaybackStateCompat.STATE_PAUSED);
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
    } else if (State.Stopped == state) {
      // if we get a pause while we're already stopped, it means we likely woke up because
      // of AudioIntentReceiver, so just stop in this case.
      setState(PlaybackStateCompat.STATE_STOPPED);
      stopSelf();
    }
  }

  private void processRewindRequest() {
    if (State.Playing == state || State.Paused == state) {
      setState(PlaybackStateCompat.STATE_REWINDING);

      int seekTo = 0;
      int pos = player.getCurrentPosition();
      if (audioRequest.isGapless()) {
        seekTo = getSeekPosition(true);
        pos = pos - seekTo;
      }

      if (pos > 1500 && !playerOverride) {
        player.seekTo(seekTo);
        state = State.Playing; // in case we were paused
      } else {
        tryToGetAudioFocus();
        int sura = audioQueue.getCurrentSura();
        audioQueue.playPreviousAyah(true);
        if (audioRequest.isGapless() && sura == audioQueue.getCurrentSura()) {
          int timing = getSeekPosition(true);
          if (timing > -1) {
            player.seekTo(timing);
          }
          updateNotification();
          state = State.Playing; // in case we were paused
          return;
        }
        playAudio();
      }
    }
  }

  private void processSkipRequest() {
    if (audioRequest == null) {
      return;
    }
    if (State.Playing == state || State.Paused == state) {
      setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT);

      if (playerOverride) {
        playAudio(false);
      } else {
        final int sura = audioQueue.getCurrentSura();
        tryToGetAudioFocus();
        audioQueue.playNextAyah(true);
        if (audioRequest.isGapless() && sura == audioQueue.getCurrentSura()) {
          int timing = getSeekPosition(false);
          if (timing > -1) {
            player.seekTo(timing);
            state = State.Playing; // in case we were paused
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
    setState(PlaybackStateCompat.STATE_STOPPED);
    serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS);

    if (State.Preparing == state) {
      shouldStop = true;
      relaxResources(false, true);
    }

    if (force || State.Stopped != state) {
      state = State.Stopped;

      // let go of all resources...
      relaxResources(true, true);
      giveUpAudioFocus();

      // service is no longer necessary. Will be started again if needed.
      serviceHandler.removeCallbacksAndMessages(null);
      stopSelf();

      // stop async task if it's running
      if (timingTask != null) {
        timingTask.cancel(true);
      }

      // tell the ui we've stopped
      notifyAudioStatus(AudioUpdateIntent.STOPPED);
    }
  }

  private void notifyAyahChanged() {
    if (audioRequest != null) {
      Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
      updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.PLAYING);
      updateIntent.putExtra(AudioUpdateIntent.SURA, audioQueue.getCurrentSura());
      updateIntent.putExtra(AudioUpdateIntent.AYAH, audioQueue.getCurrentAyah());
      broadcastManager.sendBroadcast(updateIntent);

      MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
          .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getTitle());
      if (player.isPlaying()) {
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration());
      }

      if (displayIcon != null) {
        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, displayIcon);
      }
      mediaSession.setMetadata(metadataBuilder.build());
    }
  }

  private void notifyAudioStatus(int status) {
    Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
    updateIntent.putExtra(AudioUpdateIntent.STATUS, status);
    broadcastManager.sendBroadcast(updateIntent);
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
      isSetupAsForeground = false;
    }

    // stop and release the Media Player, if it's available
    if (releaseMediaPlayer && player != null) {
      player.reset();
      player.release();
      player = null;
      mediaSession.setActive(false);
    }

    // we can also release the Wifi lock, if we're holding it
    if (wifiLock.isHeld()) {
      wifiLock.release();
    }
  }

  private void giveUpAudioFocus() {
    if (audioFocus == AudioFocus.Focused &&
        audioFocusHelper != null && audioFocusHelper.abandonFocus()) {
      audioFocus = AudioFocus.NoFocusNoDuck;
    }
  }

  /**
   * Reconfigures MediaPlayer according to audio focus settings and
   * starts/restarts it. This method starts/restarts the MediaPlayer
   * respecting the current audio focus state. So if we have focus,
   * it will play normally; if we don't have focus, it will either
   * leave the MediaPlayer paused or set it to a low volume, depending
   * on what is allowed by the current focus settings. This method assumes
   * player != null, so if you are calling it, you have to do so from a
   * context where you are sure this is the case.
   */
  private void configAndStartMediaPlayer() {
    configAndStartMediaPlayer(true);
  }

  private void configAndStartMediaPlayer(boolean canSeek) {
    Timber.d("configAndStartMediaPlayer()");
    if (audioFocus == AudioFocus.NoFocusNoDuck) {
      // If we don't have audio focus and can't duck, we have to pause,
      // even if state is State.Playing. But we stay in the Playing state
      // so that we know we have to resume playback once we get focus back.
      if (player.isPlaying()) {
        player.pause();
      }
      return;
    } else if (audioFocus == AudioFocus.NoFocusCanDuck) {
      // we'll be relatively quiet
      player.setVolume(DUCK_VOLUME, DUCK_VOLUME);
    } else {
      player.setVolume(1.0f, 1.0f);
    } // we can be loud

    if (shouldStop) {
      processStopRequest();
      shouldStop = false;
      return;
    }

    if (playerOverride) {
      if (!player.isPlaying()) {
        player.start();
        state = State.Playing;
      }
      return;
    }

    Timber.d("checking if playing...");
    if (!player.isPlaying()) {
      if (canSeek && audioRequest.isGapless()) {
        int timing = getSeekPosition(false);
        if (timing != -1) {
          Timber.d("got timing: %d, seeking and updating later...", timing);
          player.seekTo(timing);
          return;
        } else {
          Timber.d("no timing data yet, will try again...");
          // try to play again after 200 ms
          serviceHandler.sendEmptyMessageDelayed(MSG_START_AUDIO, 200);
          return;
        }
      } else if (audioRequest.isGapless()) {
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200);
      }

      player.start();
      state = State.Playing;
    }
  }

  private void tryToGetAudioFocus() {
    if (audioFocus != AudioFocus.Focused &&
        audioFocusHelper != null && audioFocusHelper.requestFocus()) {
      audioFocus = AudioFocus.Focused;
    }
  }

  private String getTitle() {
    if (audioQueue == null) {
      return "";
    }
    return quranDisplayData.getSuraAyahString(this, audioQueue.getCurrentSura(), audioQueue.getCurrentAyah());
  }

  /**
   * Starts playing the next file.
   */
  private void playAudio() {
    playAudio(false);
  }

  private void playAudio(boolean playRepeatSeparator) {
    if (!isSetupAsForeground) {
      setUpAsForeground();
    }

    state = State.Stopped;
    relaxResources(false, false); // release everything except MediaPlayer
    playerOverride = false;

    try {
      String url = audioQueue == null ? null : audioQueue.getUrl();
      if (audioRequest == null || url == null) {
        Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
        updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.STOPPED);
        broadcastManager.sendBroadcast(updateIntent);

        processStopRequest(true); // stop everything!
        return;
      }

      final boolean isStreaming = url.startsWith("http:") || url.startsWith("https:");
      if (!isStreaming) {
        File f = new File(url);
        if (!f.exists()) {
          Intent updateIntent = new Intent(AudioUpdateIntent.INTENT_NAME);
          updateIntent.putExtra(AudioUpdateIntent.STATUS, AudioUpdateIntent.STOPPED);
          updateIntent.putExtra(EXTRA_PLAY_INFO, audioRequest);
          broadcastManager.sendBroadcast(updateIntent);

          processStopRequest(true);
          return;
        }
      }

      int overrideResource = 0;
      if (playRepeatSeparator) {
        final int sura = audioQueue.getCurrentSura();
        final int ayah = audioQueue.getCurrentAyah();
        if (sura != 9 && ayah > 1) {
          overrideResource = R.raw.bismillah;
        } else if (sura == 9 &&
            (ayah > 1 || audioRequest.needsIsti3athaAudio())) {
          overrideResource = R.raw.isti3atha;
        }
        // otherwise, ayah of 1 will automatically play the file's basmala
      }

      Timber.d("okay, we are preparing to play - streaming is: %b", isStreaming);
      createMediaPlayerIfNeeded();
      player.setAudioStreamType(AudioManager.STREAM_MUSIC);
      setState(PlaybackStateCompat.STATE_CONNECTING);

      try {
        boolean playUrl = true;
        if (overrideResource != 0) {
          AssetFileDescriptor afd = getResources().openRawResourceFd(overrideResource);
          if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            playerOverride = true;
            playUrl = false;
          }
        }

        if (playUrl) {
          overrideResource = 0;
          player.setDataSource(url);
        }
      } catch (IllegalStateException ie) {
        Timber.d("IllegalStateException() while setting data source, trying to reset...");
        if (overrideResource != 0) {
          playAudio(false);
          return;
        }
        player.reset();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setDataSource(url);
      }

      state = State.Preparing;

      // starts preparing the media player in the background. When it's
      // done, it will call our OnPreparedListener (that is, the
      // onPrepared() method on this class, since we set the listener
      // to 'this').
      //
      // Until the media player is prepared, we *cannot* call start() on it!
      Timber.d("preparingAsync()...");
      Timber.d("prepareAsync: " + overrideResource + ", " + url);
      player.prepareAsync();

      // If we are streaming from the internet, we want to hold a Wifi lock,
      // which prevents the Wifi radio from going to sleep while the song is
      // playing. If, on the other hand, we are *not* streaming, we want to
      // release the lock if we were holding it before.
      if (isStreaming) {
        wifiLock.acquire();
      } else if (wifiLock.isHeld()) {
        wifiLock.release();
      }
    } catch (IOException ex) {
      Timber.e("IOException playing file: %s", ex.getMessage());
      ex.printStackTrace();
    }
  }

  private void setState(int state) {
    long position = 0;
    if (player != null && player.isPlaying()) {
      position = player.getCurrentPosition();
    }

    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
    builder.setState(state, position, 1.0f);
    builder.setActions(
        PlaybackStateCompat.ACTION_PLAY |
        PlaybackStateCompat.ACTION_STOP |
        PlaybackStateCompat.ACTION_REWIND |
        PlaybackStateCompat.ACTION_FAST_FORWARD |
        PlaybackStateCompat.ACTION_PAUSE |
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
    mediaSession.setPlaybackState(builder.build());
  }

  @Override
  public void onSeekComplete(MediaPlayer mediaPlayer) {
    Timber.d("seek complete! %d vs %d",
        mediaPlayer.getCurrentPosition(), player.getCurrentPosition());
    player.start();
    state = State.Playing;
    serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200);
  }

  /** Called when media player is done playing current file. */
  @Override
  public void onCompletion(MediaPlayer player) {
    // The media player finished playing the current file, so
    // we go ahead and start the next.
    if (playerOverride) {
      playAudio(false);
    } else {
      final int beforeSura = audioQueue.getCurrentSura();
      if (audioQueue.playNextAyah(false)) {
        if (audioRequest != null &&
            audioRequest.isGapless() &&
            beforeSura == audioQueue.getCurrentSura()) {
          // we're actually repeating, but we reached the end of the file before we could
          // seek to the proper place. so let's seek anyway.
          player.seekTo(gaplessSuraData.get(audioQueue.getCurrentAyah()));
        } else {
          // we actually switched to a different ayah - so if the
          // sura changed, then play the basmala if the ayah is
          // not the first one (or if we're in sura tawba).
          boolean flag = beforeSura != audioQueue.getCurrentSura();
          playAudio(flag);
        }
      } else {
        processStopRequest(true);
      }
    }
  }

  /** Called when media player is done preparing. */
  @Override
  public void onPrepared(MediaPlayer player) {
    Timber.d("okay, prepared!");

    // The media player is done preparing. That means we can start playing!
    if (shouldStop) {
      processStopRequest();
      shouldStop = false;
      return;
    }

    // if gapless and sura changed, get the new data
    if (audioRequest.isGapless()) {
      if (gaplessSura != audioQueue.getCurrentSura()) {
        if (timingTask != null) {
          timingTask.cancel(true);
        }

        String dbPath = audioRequest.getAudioPathInfo().getGaplessDatabase();
        timingTask = new ReadGaplessDataTask(dbPath);
        timingTask.execute(audioQueue.getCurrentSura());
      }
    }

    if (playerOverride || !audioRequest.isGapless()) {
      notifyAyahChanged();
    }

    updateNotification();
    configAndStartMediaPlayer();
  }

  /** Updates the notification. */
  void updateNotification() {
    notificationBuilder.setContentText(getTitle());
    if (!didSetNotificationIconOnNotificationBuilder && notificationIcon != null) {
      notificationBuilder.setLargeIcon(notificationIcon);
      didSetNotificationIconOnNotificationBuilder = true;
    }
    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
  }

  void pauseNotification() {
    final NotificationCompat.Builder builder = getPausedNotificationBuilder();
    notificationManager.notify(NOTIFICATION_ID, builder.build());
  }

  /**
   * Generate the notification icon
   * This might return null if the icon fails to initialize. This method should
   * be called from a separate background thread (other than the one the service
   * is running on).
   *
   * @return a bitmap of the notification icon
   */
  private Bitmap generateNotificationIcon() {
    final Context appContext = getApplicationContext();
    try {
      Resources resources = appContext.getResources();
      Bitmap logo = BitmapFactory.decodeResource(resources, R.drawable.icon);
      int iconWidth = logo.getWidth();
      int iconHeight = logo.getHeight();
      ColorDrawable cd = new ColorDrawable(ContextCompat.getColor(appContext,
          R.color.audio_notification_background_color));
      Bitmap bitmap = Bitmap.createBitmap(iconWidth * 2, iconHeight * 2, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      cd.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
      cd.draw(canvas);
      canvas.drawBitmap(logo, (int) (iconWidth / 2.0), (int) (iconHeight / 2.0), null);
      return bitmap;
    } catch (OutOfMemoryError oomError) {
      // if this happens, we need to handle it gracefully, since it's not crash worthy.
      Timber.e(oomError);
      return null;
    }
  }

  /**
   * Configures service as a foreground service. A foreground service
   * is a service that's doing something the user is actively aware of
   * (such as playing music), and must appear to the user as a notification.
   * That's why we create the notification here.
   */
  private void setUpAsForeground() {
    // clear the "downloading complete" notification (if it exists)
    notificationManager.cancel(QuranDownloadNotifier.DOWNLOADING_COMPLETE_NOTIFICATION);

    final Context appContext = getApplicationContext();
    final PendingIntent pi = getNotificationPendingIntent();

    final PendingIntent previousIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_PREVIOUS, audioUtils.getAudioIntent(this, ACTION_REWIND),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent nextIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_SKIP, audioUtils.getAudioIntent(this, ACTION_SKIP),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent pauseIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_PAUSE, audioUtils.getAudioIntent(this, ACTION_PAUSE),
        PendingIntent.FLAG_UPDATE_CURRENT);

    String audioTitle = getTitle();
    if (notificationBuilder == null) {
      final Bitmap icon = notificationIcon;
      notificationBuilder = new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID);
      notificationBuilder
          .setSmallIcon(R.drawable.ic_notification)
          .setColor(notificationColor)
          .setOngoing(true)
          .setContentTitle(getString(R.string.app_name))
          .setContentIntent(pi)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .addAction(R.drawable.ic_previous, getString(R.string.previous), previousIntent)
          .addAction(R.drawable.ic_pause, getString(R.string.pause), pauseIntent)
          .addAction(R.drawable.ic_next, getString(R.string.next), nextIntent)
          .setShowWhen(false)
          .setWhen(0) // older platforms seem to ignore setShowWhen(false)
          .setLargeIcon(icon)
          .setStyle(
              new MediaStyle()
                  .setShowActionsInCompactView(0, 1, 2)
                  .setMediaSession(mediaSession.getSessionToken()));
      didSetNotificationIconOnNotificationBuilder = icon != null;
    }

    notificationBuilder.setTicker(audioTitle);
    notificationBuilder.setContentText(audioTitle);

    startForeground(NOTIFICATION_ID, notificationBuilder.build());
    isSetupAsForeground = true;
  }

  private NotificationCompat.Builder getPausedNotificationBuilder() {
    final Context appContext = getApplicationContext();
    final PendingIntent resumeIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_RESUME, audioUtils.getAudioIntent(this, ACTION_PLAYBACK),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent stopIntent = PendingIntent.getService(
        appContext, REQUEST_CODE_STOP, audioUtils.getAudioIntent(this, ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT);
    final PendingIntent pi = getNotificationPendingIntent();

    if (pausedNotificationBuilder == null) {
      pausedNotificationBuilder =
          new NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID);
      pausedNotificationBuilder
          .setSmallIcon(R.drawable.ic_notification)
          .setColor(notificationColor)
          .setOngoing(true)
          .setContentTitle(getString(R.string.app_name))
          .setContentIntent(pi)
          .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
          .addAction(R.drawable.ic_play, getString(R.string.play), resumeIntent)
          .addAction(R.drawable.ic_stop, getString(R.string.stop), stopIntent)
          .setShowWhen(false)
          .setWhen(0)
          .setLargeIcon(notificationIcon)
          .setStyle(
              new MediaStyle()
                  .setShowActionsInCompactView(0, 1)
                  .setMediaSession(mediaSession.getSessionToken()));
    }

    pausedNotificationBuilder.setContentText(getTitle());
    return pausedNotificationBuilder;
  }

  private PendingIntent getNotificationPendingIntent() {
    final Context appContext = getApplicationContext();
    return PendingIntent.getActivity(
        appContext, REQUEST_CODE_MAIN, new Intent(appContext, PagerActivity.class),
        PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /**
   * Called when there's an error playing media. When this happens, the media
   * player goes to the Error state. We warn the user about the error and
   * reset the media player.
   */
  @Override
  public boolean onError(MediaPlayer mp, int what, int extra) {
    Timber.e("Error: what=%s, extra=%s", String.valueOf(what), String.valueOf(extra));

    state = State.Stopped;
    relaxResources(true, true);
    giveUpAudioFocus();
    return true; // true indicates we handled the error
  }

  @Override
  public void onGainedAudioFocus() {
    audioFocus = AudioFocus.Focused;

    // restart media player with new focus settings
    if (State.Playing == state) {
      configAndStartMediaPlayer(false);
    }
  }

  @Override
  public void onLostAudioFocus(boolean canDuck) {
    audioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;

    // start/restart/pause media player with new focus settings
    if (player != null && player.isPlaying()) {
      configAndStartMediaPlayer(false);
    }
  }

  @Override
  public void onDestroy() {
    compositeDisposable.clear();
    // Service is being killed, so make sure we release our resources
    serviceHandler.removeCallbacksAndMessages(null);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      serviceLooper.quitSafely();
    } else {
      serviceLooper.quit();
    }
    unregisterReceiver(noisyAudioStreamReceiver);
    state = State.Stopped;
    relaxResources(true, true);
    giveUpAudioFocus();
    mediaSession.release();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

  private class NoisyAudioStreamReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
        // pause audio when headphones are unplugged
        processPauseRequest();
      }
    }
  }
}
