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
package com.quran.labs.androidquran.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.SQLException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.media.MediaPlayer.OnSeekCompleteListener
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.Process
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.SparseIntArray
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils.clamp
import androidx.media.session.MediaButtonReceiver
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.playback.AudioRequest
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.dao.audio.AudioPlaybackInfo
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.database.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.database.SuraTimingDatabaseHandler.Companion.getDatabaseHandler
import com.quran.labs.androidquran.extension.requiresBasmallah
import com.quran.labs.androidquran.presenter.audio.service.AudioQueue
import com.quran.labs.androidquran.service.util.AudioFocusHelper
import com.quran.labs.androidquran.service.util.AudioFocusable
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.NotificationChannelUtil.setupNotificationChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs

/**
 * Service that handles media playback. This is the Service through which we
 * perform all the media handling in our application. It waits for Intents
 * (which come from our main activity, [PagerActivity], which signal
 * the service to perform specific operations: Play, Pause, Rewind, Skip, etc.
 */
class AudioService : Service(), OnCompletionListener, OnPreparedListener,
  MediaPlayer.OnErrorListener, AudioFocusable, OnSeekCompleteListener {

  // our media player
  private var player: MediaPlayer? = null

  // are we playing an override file (basmalah/isti3atha)
  private var playerOverride = false

  // object representing the current playing request
  private var audioRequest: AudioRequest? = null

  // the playback queue
  private var audioQueue: AudioQueue? = null

  // indicates the state our service:
  private enum class State {
    Stopped,  // media player is stopped and not prepared to play
    Preparing,  // media player is preparing...
    Playing,  // playback active (media player ready!). (but the media

    // player may actually be  paused in this state if we don't have audio
    // focus. But we stay in this state so that we know we have to resume
    // playback once we get focus back)
    Paused // playback paused (media player ready!)
  }

  private var state = State.Stopped

  // do we have audio focus?
  private enum class AudioFocus {
    NoFocusNoDuck,  // we don't have audio focus, and can't duck
    NoFocusCanDuck,  // we don't have focus, but can play at a low volume
    Focused // we have full audio focus
  }

  private var audioFocus = AudioFocus.NoFocusNoDuck

  // are we already in the foreground
  private var isSetupAsForeground = false

  // should we stop (after preparing is done) or not
  private var shouldStop = false

  // The ID we use for the notification (the onscreen alert that appears
  // at the notification area at the top of the screen as an icon -- and
  // as text as well if the user expands the notification area).
  private val NOTIFICATION_ID = Constants.NOTIFICATION_ID_AUDIO_PLAYBACK

  // Wifi lock that we hold when streaming files from the internet,
  // in order to prevent the device from shutting off the Wifi radio
  private lateinit var wifiLock: WifiLock

  private lateinit var audioFocusHelper: AudioFocusHelper
  private lateinit var notificationManager: NotificationManager
  private lateinit var noisyAudioStreamReceiver: BroadcastReceiver
  private lateinit var mediaSession: MediaSessionCompat

  private lateinit var serviceLooper: Looper
  private lateinit var serviceHandler: ServiceHandler

  private var notificationBuilder: NotificationCompat.Builder? = null
  private var pausedNotificationBuilder: NotificationCompat.Builder? = null
  private var didSetNotificationIconOnNotificationBuilder = false
  private var gaplessSura = 0
  private var notificationColor = 0

  // read by service thread, written on the I/O thread once
  @Volatile
  private var notificationIcon: Bitmap? = null
  private var displayIcon: Bitmap? = null
  private var gaplessSuraData: SparseIntArray = SparseIntArray()
  private var timingDisposable: Disposable? = null
  private val compositeDisposable = CompositeDisposable()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var audioUtils: AudioUtils

  @Inject
  lateinit var audioStatusRepository: AudioStatusRepository

  private inner class ServiceHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      if (msg.what == MSG_INCOMING && msg.obj != null) {
        val intent = msg.obj as Intent
        handleIntent(intent)
      } else if (msg.what == MSG_START_AUDIO) {
        configAndStartMediaPlayer()
      } else if (msg.what == MSG_UPDATE_AUDIO_POS) {
        updateAudioPlayPosition()
      }
    }
  }

  /**
   * Makes sure the media player exists and has been reset. This will create
   * the media player if needed, or reset the existing media player if one
   * already exists.
   */
  private fun createMediaPlayerIfNeeded(): MediaPlayer {
    val currentMediaPlayer = player
    return if (currentMediaPlayer == null) {
      val localPlayer = MediaPlayer()
      player = localPlayer

      // Make sure the media player will acquire a wake-lock while playing.
      // If we don't do that, the CPU might go to sleep while the song is
      // playing, causing playback to stop.
      //
      // Remember that to use this, we have to declare the
      // android.permission.WAKE_LOCK permission in AndroidManifest.xml.
      localPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

      // we want the media player to notify us when it's ready preparing,
      // and when it's done playing:
      localPlayer.setOnPreparedListener(this)
      localPlayer.setOnCompletionListener(this)
      localPlayer.setOnErrorListener(this)
      localPlayer.setOnSeekCompleteListener(this)

      val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()
      localPlayer.setAudioAttributes(audioAttributes)

      mediaSession.isActive = true
      localPlayer
    } else {
      Timber.d("resetting player...")
      currentMediaPlayer.reset()
      currentMediaPlayer
    }
  }

  override fun onCreate() {
    Timber.i("debug: Creating service")
    val thread = HandlerThread(
      "AyahAudioService",
      Process.THREAD_PRIORITY_BACKGROUND
    )
    thread.start()

    // Get the HandlerThread's Looper and use it for our Handler
    serviceLooper = thread.looper
    serviceHandler = ServiceHandler(serviceLooper)
    val appContext = applicationContext
    (appContext as QuranApplication).applicationComponent.inject(this)
    wifiLock = (appContext.getSystemService(WIFI_SERVICE) as WifiManager)
      .createWifiLock(WifiManager.WIFI_MODE_FULL, "QuranAudioLock")
    notificationManager =
      appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    // create the Audio Focus Helper
    audioFocusHelper = AudioFocusHelper(appContext, this)
    noisyAudioStreamReceiver = NoisyAudioStreamReceiver()

    ContextCompat.registerReceiver(
      this,
      noisyAudioStreamReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
      ContextCompat.RECEIVER_EXPORTED
    )

    val receiver = ComponentName(this, MediaButtonReceiver::class.java)
    mediaSession = MediaSessionCompat(appContext, "QuranMediaSession", receiver, null)
    mediaSession.setCallback(MediaSessionCallback(), serviceHandler)
    val channelName = getString(R.string.notification_channel_audio)
    setupNotificationChannel(
      notificationManager, NOTIFICATION_CHANNEL_ID, channelName
    )
    notificationColor = ContextCompat.getColor(this, R.color.audio_notification_color)
    try {
      // for Android Wear, use a 1x1 Bitmap with the notification color
      val placeholder = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
      displayIcon = placeholder
      val canvas = Canvas(placeholder)
      canvas.drawColor(notificationColor)
    } catch (oom: OutOfMemoryError) {
      Timber.e(oom)
    }

    val icon = displayIcon
    // if we couldn't load the 1x1 bitmap, we can't load the image either
    if (icon != null) {
      compositeDisposable.add(
        Maybe.fromCallable { generateNotificationIcon() ?: icon }
          .subscribeOn(Schedulers.io())
          .subscribe { bitmap: Bitmap? -> notificationIcon = bitmap })
    }
  }

  private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
    override fun onPlay() {
      processPlayRequest()
    }

    override fun onSkipToNext() {
      processSkipRequest()
    }

    override fun onSkipToPrevious() {
      processRewindRequest()
    }

    override fun onPause() {
      processPauseRequest()
    }

    override fun onStop() {
      processStopRequest()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent == null) {
      // handle a crash that occurs where intent comes in as null
      if (State.Stopped == state) {
        serviceHandler.removeCallbacksAndMessages(null)
        stopSelf()
      }
    } else {
      val action = intent.action
      if (ACTION_PLAYBACK == action || Intent.ACTION_MEDIA_BUTTON == action) {
        // go to the foreground as quickly as possible.
        setUpAsForeground()
      }
      val message = serviceHandler.obtainMessage(MSG_INCOMING, intent)
      serviceHandler.sendMessage(message)
    }
    return START_NOT_STICKY
  }

  private fun handleIntent(intent: Intent) {
    val action = intent.action
    if (ACTION_CONNECT == action) {
      if (State.Stopped == state) {
        processStopRequest(true)
      } else {
        updateAudioPlaybackStatus()
      }
    } else if (ACTION_PLAYBACK == action) {
      val updatedAudioRequest = intent.getParcelableExtra<AudioRequest>(EXTRA_PLAY_INFO)
      if (updatedAudioRequest != null) {
        audioRequest = updatedAudioRequest
        val start = updatedAudioRequest.start
        val basmallah = !updatedAudioRequest.isGapless() && start.requiresBasmallah()
        audioQueue = AudioQueue(
          quranInfo, updatedAudioRequest,
          AudioPlaybackInfo(start, 1, 1, basmallah)
        )
        Timber.d("audio request has changed...")
        player?.stop()
        state = State.Stopped
        Timber.d("stop if playing...")
      }
      processTogglePlaybackRequest()
    } else if (ACTION_PLAY == action) {
      processPlayRequest()
    } else if (ACTION_PAUSE == action) {
      processPauseRequest()
    } else if (ACTION_SKIP == action) {
      processSkipRequest()
    } else if (ACTION_STOP == action) {
      processStopRequest()
    } else if (ACTION_REWIND == action) {
      processRewindRequest()
    } else if (ACTION_UPDATE_SETTINGS == action) {
      val playInfo = intent.getParcelableExtra<AudioRequest>(EXTRA_PLAY_INFO)
      val localAudioQueue = audioQueue
      if (playInfo != null && localAudioQueue != null) {
        audioQueue = localAudioQueue.withUpdatedAudioRequest(playInfo)
        if (playInfo.playbackSpeed != audioRequest?.playbackSpeed) {
          processUpdatePlaybackSpeed(playInfo.playbackSpeed)
          serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200)
        }
        audioRequest = playInfo
      }
    } else {
      MediaButtonReceiver.handleIntent(mediaSession, intent)
    }
  }

  private fun updateGaplessData(databasePath: String, sura: Int) {
    timingDisposable?.dispose()
    timingDisposable = Single.fromCallable {
      val db = getDatabaseHandler(databasePath)

      val map = SparseIntArray()
      var cursor: Cursor? = null
      try {
        cursor = db.getAyahTimings(sura)
        Timber.d("got cursor of data")
        if (cursor != null && cursor.moveToFirst()) {
          do {
            val ayah = cursor.getInt(1)
            val time = cursor.getInt(2)
            map.put(ayah, time)
          } while (cursor.moveToNext())
        }
      } catch (se: SQLException) {
        // don't crash the app if the database is corrupt
        Timber.e(se)
      } finally {
        closeCursor(cursor)
      }

      map
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      // have to annotate _ as Throwable? otherwise crashes
      // https://github.com/ReactiveX/RxJava/issues/7444
      .subscribe { map, _: Throwable? ->
        gaplessSura = sura
        gaplessSuraData = map ?: SparseIntArray()
      }
  }

  private fun getSeekPosition(isRepeating: Boolean): Int {
    if (audioRequest == null) {
      return -1
    }

    val localAudioQueue = audioQueue ?: return -1
    if (gaplessSura == localAudioQueue.getCurrentSura()) {
      val ayah = localAudioQueue.getCurrentAyah()
      val time = gaplessSuraData[ayah]
      return if (ayah == 1 && !isRepeating) {
        gaplessSuraData[0]
      } else time
    }
    return -1
  }

  private fun updateAudioPlayPosition() {
    Timber.d("updateAudioPlayPosition")
    val localAudioQueue = audioQueue ?: return
    val localPlayer = player

    if (localPlayer != null) {
      val sura = localAudioQueue.getCurrentSura()
      val ayah = localAudioQueue.getCurrentAyah()
      var updatedAyah = ayah
      val maxAyahs = quranInfo.getNumberOfAyahs(sura)
      if (sura != gaplessSura) {
        return
      }
      setState(PlaybackStateCompat.STATE_PLAYING)
      var pos = localPlayer.currentPosition
      var ayahTime = gaplessSuraData[ayah]
      Timber.d(
        "updateAudioPlayPosition: %d:%d, currently at %d vs expected at %d",
        sura, ayah, pos, ayahTime
      )
      var iterAyah = ayah
      if (ayahTime > pos) {
        while (--iterAyah > 0) {
          ayahTime = gaplessSuraData[iterAyah]
          if (ayahTime <= pos) {
            updatedAyah = iterAyah
            break
          } else {
            updatedAyah--
          }
        }
      } else {
        while (++iterAyah <= maxAyahs) {
          ayahTime = gaplessSuraData[iterAyah]
          if (ayahTime > pos) {
            updatedAyah = iterAyah - 1
            break
          } else {
            updatedAyah++
          }
        }
      }
      Timber.d(
        "updateAudioPlayPosition: %d:%d, decided ayah should be: %d",
        sura, ayah, updatedAyah
      )
      if (updatedAyah != ayah) {
        ayahTime = gaplessSuraData[ayah]
        if (abs(pos - ayahTime) < 150) {
          // shouldn't change ayahs if the delta is just 150ms...
          serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150)
          return
        }
        val success = localAudioQueue.playAt(sura, updatedAyah, false)
        val nextSura = localAudioQueue.getCurrentSura()
        val nextAyah = localAudioQueue.getCurrentAyah()

        if (!success) {
          processStopRequest()
          return
        } else if (nextSura != sura || nextAyah != updatedAyah) {
          // remove any messages currently in the queue
          serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)

          // if the ayah hasn't changed, we're repeating the ayah,
          // otherwise, we're repeating a range. this variable is
          // what determines whether or not we replay the basmallah.
          val ayahRepeat = ayah == nextAyah && sura == nextSura
          if (ayahRepeat) {
            // jump back to the ayah we should repeat and play it
            pos = getSeekPosition(true)
            localPlayer.seekTo(pos)
          } else {
            // we're repeating into a different sura
            val flag = sura != localAudioQueue.getCurrentSura()
            playAudio(flag)
          }
          return
        }

        // moved on to next ayah
        updateNotification()
      } else {
        // if we have end of sura info and we bypassed end of sura
        // line, switch the sura.
        ayahTime = gaplessSuraData[999]
        if (ayahTime in 1..pos) {
          val success = localAudioQueue.playAt(sura + 1, 1, false)
          if (success && localAudioQueue.getCurrentSura() == sura) {
            // remove any messages currently in the queue
            serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)

            // jump back to the ayah we should repeat and play it
            pos = getSeekPosition(false)
            localPlayer.seekTo(pos)
          } else if (!success) {
            processStopRequest()
          } else {
            playAudio(true)
          }
          return
        }
      }
      notifyAyahChanged()
      if (maxAyahs >= updatedAyah + 1) {
        val timeDelta = gaplessSuraData[updatedAyah + 1] - localPlayer.currentPosition
        val t = clamp(timeDelta, 100, 10000)
        val tAccountingForSpeed = t / (audioRequest?.playbackSpeed ?: 1f)
        Timber.d(
          "updateAudioPlayPosition before: %d, after %f, speed: %f",
          t,
          tAccountingForSpeed,
          audioRequest?.playbackSpeed
        )
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, tAccountingForSpeed.toLong())
      } else if (maxAyahs == updatedAyah) {
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 150)
      }
      // if we're on the last ayah, don't do anything - let the file
      // complete on its own to avoid getCurrentPosition() bugs.
    }
  }

  private fun processTogglePlaybackRequest() {
    if (State.Paused == state || State.Stopped == state) {
      processPlayRequest()
    } else {
      processPauseRequest()
    }
  }

  private fun processPlayRequest() {
    val localAudioRequest = audioRequest
    val localAudioQueue = audioQueue
    if (localAudioRequest == null || localAudioQueue == null) {
      // no audio request, what can we do?
      relaxResources(releaseMediaPlayer = true, stopForeground = true)
      return
    }
    tryToGetAudioFocus()

    // actually play the file
    if (State.Stopped == state) {
      if (localAudioRequest.isGapless()) {
        val dbPath = localAudioRequest.audioPathInfo.gaplessDatabase
        if (dbPath != null) {
          updateGaplessData(dbPath, localAudioQueue.getCurrentSura())
        }
      }

      // If we're stopped, just go ahead to the next file and start playing
      playAudio(localAudioQueue.getCurrentSura() == 9 && localAudioQueue.getCurrentAyah() == 1)
    } else if (State.Paused == state) {
      // If we're paused, just continue playback and restore the
      // 'foreground service' state.
      state = State.Playing
      if (!isSetupAsForeground) {
        setUpAsForeground()
      }
      configAndStartMediaPlayer(false)
      updateAudioPlaybackStatus()
    }
  }

  private fun processPauseRequest() {
    if (State.Playing == state) {
      // Pause media player and cancel the 'foreground service' state.
      state = State.Paused
      serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)
      player?.pause()
      setState(PlaybackStateCompat.STATE_PAUSED)
      // on jellybean and above, stay in the foreground and
      // update the notification.
      relaxResources(releaseMediaPlayer = false, stopForeground = false)
      pauseNotification()
      updateAudioPlaybackStatus()
    } else if (State.Stopped == state) {
      // if we get a pause while we're already stopped, it means we likely woke up because
      // of AudioIntentReceiver, so just stop in this case.
      setState(PlaybackStateCompat.STATE_STOPPED)
      updateAudioPlaybackStatus()
      stopSelf()
    }
  }

  private fun processRewindRequest() {
    if (State.Playing == state || State.Paused == state) {
      setState(PlaybackStateCompat.STATE_REWINDING)
      val localPlayer = player ?: return
      val localAudioQueue = audioQueue ?: return
      val localAudioRequest = audioRequest ?: return

      var seekTo = 0
      var pos = localPlayer.currentPosition
      if (localAudioRequest.isGapless()) {
        seekTo = getSeekPosition(true)
        pos -= seekTo
      }

      if (pos > 1500 && !playerOverride) {
        localPlayer.seekTo(seekTo)
        state = State.Playing // in case we were paused
      } else {
        tryToGetAudioFocus()
        val sura = localAudioQueue.getCurrentSura()
        localAudioQueue.playPreviousAyah(true)
        if (localAudioRequest.isGapless() && sura == localAudioQueue.getCurrentSura()) {
          val timing = getSeekPosition(true)
          if (timing > -1) {
            localPlayer.seekTo(timing)
          }
          updateNotification()
          state = State.Playing // in case we were paused
          return
        }
        playAudio()
      }
    }
  }

  private fun processUpdatePlaybackSpeed(speed: Float) {
    if (State.Playing === state && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        player?.playbackParams?.let { params ->
          params.setSpeed(speed)
          player?.playbackParams = params
        }
      } catch (e: Exception) {
        // catch an Android 6 crash [IllegalStateException], and report the speed since some
        // non-Android 6 devices also crash here, but with [IllegalArgumentException]
        Timber.e(e, "Failed to set speed to $speed")
      }
    }
  }

  private fun processSkipRequest() {
    if (audioRequest == null) {
      return
    }
    if (State.Playing == state || State.Paused == state) {
      setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
      if (playerOverride) {
        playAudio(false)
      } else {
        val localPlayer = player ?: return
        val localAudioQueue = audioQueue ?: return
        val localAudioRequest = audioRequest ?: return

        val sura = localAudioQueue.getCurrentSura()
        tryToGetAudioFocus()
        localAudioQueue.playNextAyah(true)
        if (localAudioRequest.isGapless() && sura == localAudioQueue.getCurrentSura()) {
          val timing = getSeekPosition(false)
          if (timing > -1) {
            localPlayer.seekTo(timing)
            state = State.Playing // in case we were paused
          }
          updateNotification()
          return
        }
        playAudio()
      }
    }
  }

  private fun processStopRequest(force: Boolean = false) {
    setState(PlaybackStateCompat.STATE_STOPPED)
    serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)
    if (State.Preparing == state) {
      shouldStop = true
      relaxResources(releaseMediaPlayer = false, stopForeground = true)
    }
    if (force || State.Stopped != state) {
      state = State.Stopped
      updateAudioPlaybackStatus()

      // let go of all resources...
      relaxResources(releaseMediaPlayer = true, stopForeground = true)
      giveUpAudioFocus()

      // service is no longer necessary. Will be started again if needed.
      serviceHandler.removeCallbacksAndMessages(null)
      stopSelf()

      // stop async task if it's running
      timingDisposable?.dispose()
    }
  }

  private fun notifyAyahChanged() {
    val localAudioRequest = audioRequest ?: return
    updateAudioPlaybackStatus()

    val metadataBuilder = MediaMetadataCompat.Builder()
      .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getTitle())
      .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, localAudioRequest.qari.name)
    val localPlayer = player
    if (localPlayer?.isPlaying == true) {
      metadataBuilder.putLong(
        MediaMetadataCompat.METADATA_KEY_DURATION,
        localPlayer.duration.toLong()
      )
    }

    if (displayIcon != null) {
      metadataBuilder.putBitmap(
        MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON,
        displayIcon
      )
    }
    mediaSession.setMetadata(metadataBuilder.build())
  }

  private fun updateAudioPlaybackStatus() {
    val audioStatus = when (state) {
      State.Stopped -> AudioStatus.Stopped
      State.Playing, State.Preparing, State.Paused -> {
        val localAudioQueue = audioQueue ?: return
        val localAudioRequest = audioRequest ?: return

        AudioStatus.Playback(
          localAudioQueue.getCurrentPlaybackAyah(),
          localAudioRequest,
          state.asPlayingPlaybackStatus()
        )
      }
    }
    audioStatusRepository.updateAyahPlayback(audioStatus)
  }

  private fun State.asPlayingPlaybackStatus(): PlaybackStatus {
    return when (this) {
      State.Playing -> PlaybackStatus.PLAYING
      State.Preparing -> PlaybackStatus.PREPARING
      State.Paused -> PlaybackStatus.PAUSED
      else -> throw IllegalStateException("State $this is not a playing state")
    }
  }

  /**
   * Releases resources used by the service for playback. This includes the
   * "foreground service" status and notification, the wake locks and
   * possibly the MediaPlayer.
   *
   * @param releaseMediaPlayer Indicates whether the Media Player should also
   * be released or not
   */
  private fun relaxResources(releaseMediaPlayer: Boolean, stopForeground: Boolean) {
    if (stopForeground) {
      // stop being a foreground service
      stopForeground(true)
      isSetupAsForeground = false
    }

    // stop and release the Media Player, if it's available
    val localPlayer = player
    if (releaseMediaPlayer && localPlayer != null) {
      try {
        localPlayer.reset()
        localPlayer.release()
      } catch (ilse: IllegalStateException) {
        // nothing to do here  ¯\_(ツ)_/¯
      }
      player = null
      mediaSession.isActive = false
    }

    // we can also release the Wifi lock, if we're holding it
    if (wifiLock.isHeld) {
      wifiLock.release()
    }
  }

  private fun giveUpAudioFocus() {
    if (audioFocus == AudioFocus.Focused && audioFocusHelper.abandonFocus()) {
      audioFocus = AudioFocus.NoFocusNoDuck
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
  private fun configAndStartMediaPlayer(canSeek: Boolean = true) {
    Timber.d("configAndStartMediaPlayer()")

    val player = player ?: return
    when (audioFocus) {
        AudioFocus.NoFocusNoDuck -> {
          // If we don't have audio focus and can't duck, we have to pause,
          // even if state is State.Playing. But we stay in the Playing state
          // so that we know we have to resume playback once we get focus back.
          if (player.isPlaying) {
            player.pause()
          }
          return
        }
        AudioFocus.NoFocusCanDuck -> {
          // we'll be relatively quiet
          player.setVolume(DUCK_VOLUME, DUCK_VOLUME)
        }
        else -> {
          // we can be loud
          player.setVolume(1.0f, 1.0f)
        }
    }

    if (shouldStop) {
      processStopRequest()
      shouldStop = false
      return
    }

    if (playerOverride) {
      if (!player.isPlaying) {
        player.start()
        state = State.Playing
        updateAudioPlaybackStatus()
      }
      return
    }

    Timber.d("checking if playing...")
    val audioRequest = audioRequest ?: return
    if (!player.isPlaying) {
      if (canSeek && audioRequest.isGapless()) {
        val timing = getSeekPosition(false)
        if (timing != -1) {
          Timber.d("got timing: %d, seeking and updating later...", timing)
          player.seekTo(timing)
        } else {
          Timber.d("no timing data yet, will try again...")
          // try to play again after 200 ms
          serviceHandler.sendEmptyMessageDelayed(MSG_START_AUDIO, 200)
        }
        return
      } else if (audioRequest.isGapless()) {
        serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200)
      }
      player.start()
      state = State.Playing
      updateAudioPlaybackStatus()
    }
  }

  private fun tryToGetAudioFocus() {
    if (audioFocus != AudioFocus.Focused && audioFocusHelper.requestFocus()) {
      audioFocus = AudioFocus.Focused
    }
  }

  private fun getTitle(): String {
    val audioQueue = audioQueue
    return if (audioQueue == null) {
      ""
    } else {
      quranDisplayData.getSuraAyahString(
        this,
        audioQueue.getCurrentSura(),
        audioQueue.getCurrentAyah()
      )
    }
  }

  /**
   * Starts playing the next file.
   */
  private fun playAudio(playRepeatSeparator: Boolean = false) {
    if (!isSetupAsForeground) {
      setUpAsForeground()
    }
    state = State.Stopped
    relaxResources(releaseMediaPlayer = false, stopForeground = false) // release everything except MediaPlayer
    playerOverride = false
    try {
      val localAudioQueue = audioQueue
      val localAudioRequest = audioRequest

      val url = audioQueue?.getUrl()
      if (localAudioRequest == null || localAudioQueue == null || url == null) {
        processStopRequest(true) // stop everything!
        return
      }

      val isStreaming = url.startsWith("http:") || url.startsWith("https:")
      if (!isStreaming) {
        val f = File(url)
        if (!f.exists()) {
          processStopRequest(true)
          return
        }
      }
      var overrideResource = 0
      if (playRepeatSeparator) {
        val sura = localAudioQueue.getCurrentSura()
        val ayah = localAudioQueue.getCurrentAyah()
        if (sura != 9 && ayah > 1) {
          overrideResource = R.raw.bismillah
        } else if (sura == 9 &&
          (ayah > 1 || localAudioRequest.needsIsti3athaAudio())
        ) {
          overrideResource = R.raw.isti3atha
        }
        // otherwise, ayah of 1 will automatically play the file's basmala
      }
      Timber.d("okay, we are preparing to play - streaming is: %b", isStreaming)

      val localPlayer = createMediaPlayerIfNeeded()
      setState(PlaybackStateCompat.STATE_CONNECTING)
      try {
        var playUrl = true
        if (overrideResource != 0) {
          val afd = resources.openRawResourceFd(overrideResource)
          if (afd != null) {
            localPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            playerOverride = true
            playUrl = false
          }
        }
        if (playUrl) {
          overrideResource = 0
          localPlayer.setDataSource(url)
        }
      } catch (ie: IllegalStateException) {
        Timber.d("IllegalStateException() while setting data source, trying to reset...")
        if (overrideResource != 0) {
          playAudio(false)
          return
        }
        try {
          localPlayer.reset()
        } catch (ilse: IllegalStateException) {
          // we hit an error while trying to load, thus calling release, thus causing reset to
          // throw - unlikely to work if we retry so just stop playback.
          processStopRequest(true);
          return;
        }
        localPlayer.setDataSource(url)
      }
      state = State.Preparing
      updateAudioPlaybackStatus()

      // starts preparing the media player in the background. When it's
      // done, it will call our OnPreparedListener (that is, the
      // onPrepared() method on this class, since we set the listener
      // to 'this').
      //
      // Until the media player is prepared, we *cannot* call start() on it!
      Timber.d("preparingAsync()...")
      Timber.d("prepareAsync: $overrideResource, $url")
      localPlayer.prepareAsync()

      // If we are streaming from the internet, we want to hold a Wifi lock,
      // which prevents the Wifi radio from going to sleep while the song is
      // playing. If, on the other hand, we are *not* streaming, we want to
      // release the lock if we were holding it before.
      if (isStreaming) {
        wifiLock.acquire()
      } else if (wifiLock.isHeld) {
        wifiLock.release()
      }
    } catch (ex: IOException) {
      Timber.e("IOException playing file: %s", ex.message)
      ex.printStackTrace()
    }
  }

  private fun setState(state: Int) {
    var position: Long = 0
    val localPlayer = player
    if (localPlayer != null && localPlayer.isPlaying) {
      position = localPlayer.currentPosition.toLong()
    }
    val builder = PlaybackStateCompat.Builder()
    builder.setState(state, position, 1.0f)

    val actions = when (state) {
      PlaybackStateCompat.STATE_PLAYING -> {
        PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
      }
      PlaybackStateCompat.STATE_PAUSED -> {
        PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
      }
      PlaybackStateCompat.STATE_STOPPED -> {
        PlaybackStateCompat.ACTION_PLAY
      }
      PlaybackStateCompat.STATE_CONNECTING -> {
        PlaybackStateCompat.ACTION_STOP
      }
      PlaybackStateCompat.STATE_REWINDING -> {
        PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
      }
      PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> {
        PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND
      }
      else -> { PlaybackStateCompat.ACTION_STOP }
    }
    builder.setActions(actions)
    mediaSession.setPlaybackState(builder.build())
  }

  override fun onSeekComplete(mediaPlayer: MediaPlayer) {
    val player = player ?: return
    Timber.d(
      "seek complete! %d vs %d",
      mediaPlayer.currentPosition, player.currentPosition
    )
    player.start()
    state = State.Playing
    updateAudioPlaybackStatus()
    audioRequest?.playbackSpeed?.let { speed ->
      processUpdatePlaybackSpeed(speed)
    }
    serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200)
  }

  /** Called when media player is done playing current file.  */
  override fun onCompletion(player: MediaPlayer) {
    // The media player finished playing the current file, so
    // we go ahead and start the next.
    if (playerOverride) {
      playAudio(false)
    } else {
      val localAudioQueue = audioQueue ?: return
      val localAudioRequest = audioRequest ?: return
      val beforeSura = localAudioQueue.getCurrentSura()
      if (localAudioQueue.playNextAyah(false)) {
        if (localAudioRequest.isGapless() && beforeSura == localAudioQueue.getCurrentSura()
        ) {
          // we're actually repeating, but we reached the end of the file before we could
          // seek to the proper place. so let's seek anyway.
          player.seekTo(gaplessSuraData[localAudioQueue.getCurrentAyah()])
        } else {
          // we actually switched to a different ayah - so if the
          // sura changed, then play the basmala if the ayah is
          // not the first one (or if we're in sura tawba).
          val flag = beforeSura != localAudioQueue.getCurrentSura()
          playAudio(flag)
        }
      } else {
        processStopRequest(true)
      }
    }
  }

  /** Called when media player is done preparing.  */
  override fun onPrepared(player: MediaPlayer) {
    Timber.d("okay, prepared!")

    // The media player is done preparing. That means we can start playing!
    if (shouldStop) {
      processStopRequest()
      shouldStop = false
      return
    }

    // if gapless and sura changed, get the new data
    val localAudioQueue = audioQueue ?: return
    val localAudioRequest = audioRequest ?: return
    if (localAudioRequest.isGapless()) {
      if (gaplessSura != localAudioQueue.getCurrentSura()) {
        val dbPath = localAudioRequest.audioPathInfo.gaplessDatabase
        if (dbPath != null) {
          updateGaplessData(dbPath, localAudioQueue.getCurrentSura())
        }
      }
    }

    if (playerOverride || !localAudioRequest.isGapless()) {
      notifyAyahChanged()
    }
    updateNotification()
    configAndStartMediaPlayer()
  }

  /** Updates the notification.  */
  private fun updateNotification() {
    notificationBuilder?.setContentText(getTitle())
    if (!didSetNotificationIconOnNotificationBuilder && notificationIcon != null) {
      notificationBuilder?.setLargeIcon(notificationIcon)
      didSetNotificationIconOnNotificationBuilder = true
    }
    notificationManager.notify(NOTIFICATION_ID, notificationBuilder?.build())
  }

  private fun pauseNotification() {
    val builder = getPausedNotificationBuilder()
    notificationManager.notify(NOTIFICATION_ID, builder.build())
  }

  /**
   * Generate the notification icon
   * This might return null if the icon fails to initialize. This method should
   * be called from a separate background thread (other than the one the service
   * is running on).
   *
   * @return a bitmap of the notification icon
   */
  private fun generateNotificationIcon(): Bitmap? {
    val appContext = applicationContext
    return try {
      val resources = appContext.resources
      val logo = BitmapFactory.decodeResource(resources, QuranFileConstants.ICON_RESOURCE_ID)
      val iconWidth = logo.width
      val iconHeight = logo.height
      val cd = ColorDrawable(
        ContextCompat.getColor(
          appContext,
          R.color.audio_notification_background_color
        )
      )
      val bitmap = Bitmap.createBitmap(iconWidth * 2, iconHeight * 2, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bitmap)
      cd.setBounds(0, 0, canvas.width, canvas.height)
      cd.draw(canvas)
      canvas.drawBitmap(
        logo,
        (iconWidth / 2.0).toInt().toFloat(),
        (iconHeight / 2.0).toInt().toFloat(),
        null
      )
      bitmap
    } catch (oomError: OutOfMemoryError) {
      // if this happens, we need to handle it gracefully, since it's not crash worthy.
      Timber.e(oomError)
      null
    }
  }

  /**
   * Configures service as a foreground service. A foreground service
   * is a service that's doing something the user is actively aware of
   * (such as playing music), and must appear to the user as a notification.
   * That's why we create the notification here.
   */
  private fun setUpAsForeground() {
    // clear the "downloading complete" notification (if it exists)
    notificationManager.cancel(QuranDownloadNotifier.DOWNLOADING_COMPLETE_NOTIFICATION)
    val appContext = applicationContext
    val pi = notificationPendingIntent

    val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE
    } else {
      0
    }
    val previousIntent = PendingIntent.getService(
      appContext, REQUEST_CODE_PREVIOUS, audioUtils.getAudioIntent(this, ACTION_REWIND),
      PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    )
    val nextIntent = PendingIntent.getService(
      appContext, REQUEST_CODE_SKIP, audioUtils.getAudioIntent(this, ACTION_SKIP),
      PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    )
    val pauseIntent = PendingIntent.getService(
      appContext, REQUEST_CODE_PAUSE, audioUtils.getAudioIntent(this, ACTION_PAUSE),
      PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    )
    val audioTitle = getTitle()
    val currentNotificationBuilder = notificationBuilder
    val updatedBuilder = if (currentNotificationBuilder == null) {
      val icon = notificationIcon
      val builder = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
      builder
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
          androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(mediaSession.sessionToken)
        )
      didSetNotificationIconOnNotificationBuilder = icon != null
      notificationBuilder = builder
      builder
    } else {
      currentNotificationBuilder
    }

    updatedBuilder.setTicker(audioTitle)
    updatedBuilder.setContentText(audioTitle)
    startForeground(NOTIFICATION_ID, updatedBuilder.build())
    isSetupAsForeground = true
  }

  private fun getPausedNotificationBuilder(): NotificationCompat.Builder {
    val appContext = applicationContext

    val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_IMMUTABLE
    } else {
      0
    }

    val resumeIntent = PendingIntent.getService(
      appContext, REQUEST_CODE_RESUME, audioUtils.getAudioIntent(this, ACTION_PLAYBACK),
      PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    )
    val stopIntent = PendingIntent.getService(
      appContext, REQUEST_CODE_STOP, audioUtils.getAudioIntent(this, ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    )
    val pi = notificationPendingIntent
    val localPausedNotificationBuilder = pausedNotificationBuilder
    val pauseBuilder = if (localPausedNotificationBuilder == null) {
      val builder =
        NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
      builder
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(notificationColor)
        .setOngoing(true)
        .setContentTitle(getString(R.string.app_name))
        .setContentIntent(pi)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .addAction(com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, getString(R.string.play), resumeIntent)
        .addAction(R.drawable.ic_stop, getString(R.string.stop), stopIntent)
        .setShowWhen(false)
        .setWhen(0)
        .setLargeIcon(notificationIcon)
        .setStyle(
          androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)
            .setMediaSession(mediaSession.sessionToken)
        )
      pausedNotificationBuilder = builder
      builder
    } else {
      localPausedNotificationBuilder
    }

    pauseBuilder.setContentText(getTitle())
    return pauseBuilder
  }

  private val notificationPendingIntent: PendingIntent
    get() {
      val appContext = applicationContext
      val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE
      } else {
        0
      }

      return PendingIntent.getActivity(
        appContext, REQUEST_CODE_MAIN, Intent(appContext, PagerActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
      )
    }

  /**
   * Called when there's an error playing media. When this happens, the media
   * player goes to the Error state. We warn the user about the error and
   * reset the media player.
   */
  override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
    Timber.e("Error: what=%s, extra=%s", what.toString(), extra.toString())
    state = State.Stopped
    updateAudioPlaybackStatus()
    relaxResources(releaseMediaPlayer = true, stopForeground = true)
    giveUpAudioFocus()
    return true // true indicates we handled the error
  }

  override fun onGainedAudioFocus() {
    audioFocus = AudioFocus.Focused

    // restart media player with new focus settings
    if (State.Playing == state) {
      configAndStartMediaPlayer(false)
    }
  }

  override fun onLostAudioFocus(canDuck: Boolean) {
    audioFocus = if (canDuck) AudioFocus.NoFocusCanDuck else AudioFocus.NoFocusNoDuck

    // start/restart/pause media player with new focus settings
    if (player?.isPlaying == true) {
      configAndStartMediaPlayer(false)
    }
  }

  override fun onDestroy() {
    compositeDisposable.clear()
    // Service is being killed, so make sure we release our resources
    serviceHandler.removeCallbacksAndMessages(null)
    serviceLooper.quitSafely()
    unregisterReceiver(noisyAudioStreamReceiver)
    state = State.Stopped
    relaxResources(true, true)
    giveUpAudioFocus()
    mediaSession.release()
    super.onDestroy()
  }

  override fun onBind(arg0: Intent): IBinder? {
    return null
  }

  private inner class NoisyAudioStreamReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
        // pause audio when headphones are unplugged
        processPauseRequest()
      }
    }
  }

  companion object {
    // These are the Intent actions that we are prepared to handle.
    const val ACTION_PLAYBACK = "com.quran.labs.androidquran.action.PLAYBACK"
    const val ACTION_PLAY = "com.quran.labs.androidquran.action.PLAY"
    const val ACTION_PAUSE = "com.quran.labs.androidquran.action.PAUSE"
    const val ACTION_STOP = "com.quran.labs.androidquran.action.STOP"
    const val ACTION_SKIP = "com.quran.labs.androidquran.action.SKIP"
    const val ACTION_REWIND = "com.quran.labs.androidquran.action.REWIND"
    const val ACTION_CONNECT = "com.quran.labs.androidquran.action.CONNECT"
    const val ACTION_UPDATE_SETTINGS = "com.quran.labs.androidquran.action.UPDATE_SETTINGS"

    // pending notification request codes
    private const val REQUEST_CODE_MAIN = 0
    private const val REQUEST_CODE_PREVIOUS = 1
    private const val REQUEST_CODE_PAUSE = 2
    private const val REQUEST_CODE_SKIP = 3
    private const val REQUEST_CODE_STOP = 4
    private const val REQUEST_CODE_RESUME = 5

    // The volume we set the media player to when we lose audio focus, but are
    // allowed to reduce the volume instead of stopping playback.
    const val DUCK_VOLUME = 0.1f

    // so user can pass in a serializable LegacyAudioRequest to the intent
    const val EXTRA_PLAY_INFO = "com.quran.labs.androidquran.PLAY_INFO"
    private const val NOTIFICATION_CHANNEL_ID = Constants.AUDIO_CHANNEL
    private const val MSG_INCOMING = 1
    private const val MSG_START_AUDIO = 2
    private const val MSG_UPDATE_AUDIO_POS = 3
  }
}
