package com.quran.labs.androidquran.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.SparseLongArray
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.math.MathUtils.clamp
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
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
import com.quran.labs.androidquran.data.TimingRepository
import com.quran.labs.androidquran.extension.requiresBasmallah
import com.quran.labs.androidquran.presenter.audio.service.AudioQueue
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.NotificationChannelUtil.setupNotificationChannel
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
class AudioService : Service(), Player.Listener {

  // our exo player
  private var player: ExoPlayer? = null

  // are we playing an override file (basmalah/isti3atha)
  private var playerOverride = false

  // object representing the current playing request
  private var audioRequest: AudioRequest? = null

  // the playback queue
  private var audioQueue: AudioQueue? = null

  // indicates the state our service:
  private enum class State {
    Stopped,  // exo player is stopped and not prepared to play
    Preparing,  // exo player is preparing...
    Playing,  // playback active (exo player ready!). (but the media

    // player may actually be  paused in this state if we don't have audio
    // focus. But we stay in this state so that we know we have to resume
    // playback once we get focus back)
    Paused // playback paused (exo player ready!)
  }

  private var state = State.Stopped


  // are we already in the foreground
  private var isSetupAsForeground = false

  // should we stop (after preparing is done) or not
  private var shouldStop = false

  // The ID we use for the notification (the onscreen alert that appears
  // at the notification area at the top of the screen as an icon -- and
  // as text as well if the user expands the notification area).
  private val NOTIFICATION_ID = Constants.NOTIFICATION_ID_AUDIO_PLAYBACK


  private lateinit var notificationManager: NotificationManager
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
  private var gaplessSuraData: SparseLongArray = SparseLongArray()
  private val compositeDisposable = CompositeDisposable()
  private lateinit var scope: CoroutineScope

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var audioUtils: AudioUtils

  @Inject
  lateinit var audioStatusRepository: AudioStatusRepository

  @Inject
  lateinit var timingRepository: TimingRepository

  private inner class ServiceHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      if (msg.what == MSG_INCOMING && msg.obj != null) {
        val intent = msg.obj as Intent
        handleIntent(intent)
      } else if (msg.what == MSG_UPDATE_AUDIO_POS) {
        updateAudioPlayPosition()
      }
    }
  }

  /**
   * Makes sure the ExoPlayer exists and has been reset. This will make
   * the ExoPlayer if needed, or reset the existing player if one
   * already exists.
   */
  @OptIn(UnstableApi::class)
  private fun makeOrResetExoPlayer(): ExoPlayer {
    val currentPlayer = player
    return if (currentPlayer == null) {
      // we only need audio, so allow r8 to remove video renderers
      // via https://developer.android.com/media/media3/exoplayer/shrinking
      val audioOnlyRenderersFactory =
        RenderersFactory {
            handler: Handler,
            videoListener: VideoRendererEventListener,
            audioListener: AudioRendererEventListener,
            textOutput: TextOutput,
            metadataOutput: MetadataOutput,
          ->
          arrayOf<Renderer>(
            MediaCodecAudioRenderer(applicationContext, MediaCodecSelector.DEFAULT, handler, audioListener)
          )
        }

      val localPlayer = ExoPlayer.Builder(applicationContext, audioOnlyRenderersFactory)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
      player = localPlayer

      // Set up listener for playback events
      localPlayer.addListener(this)

      // Configure audio attributes for music playback
      val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()
      localPlayer.setAudioAttributes(audioAttributes, true)

      mediaSession.isActive = true
      localPlayer
    } else {
      Timber.d("resetting player...")
      // ExoPlayer doesn't have reset() - we stop and clear media items instead
      currentPlayer.stop()
      currentPlayer.clearMediaItems()
      currentPlayer
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
    scope = CoroutineScope(serviceHandler.asCoroutineDispatcher() + SupervisorJob())

    val appContext = applicationContext
    (appContext as QuranApplication).applicationComponent.inject(this)
    notificationManager =
      appContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager


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
      val placeholder = createBitmap(1, 1)
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
        updateAudioPlaybackStatus()
      }
    } else {
      MediaButtonReceiver.handleIntent(mediaSession, intent)
    }
  }

  private suspend fun updateGaplessData(databasePath: String, sura: Int): SparseLongArray {
    // note - we rely on the cache from timing repository instead of checking the cache here
    // because we do not have a variable for the database path here.
    val timings = timingRepository.timings(databasePath, sura)
    gaplessSura = sura
    gaplessSuraData = timings
    return timings
  }

  private fun getSeekPosition(isRepeating: Boolean): Long {
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
    val localAudioQueue = audioQueue
    val localPlayer = player

    val sura = localAudioQueue?.getCurrentSura()
    if (localPlayer != null && localAudioQueue != null && sura == gaplessSura) {
      val ayah = localAudioQueue.getCurrentAyah()
      val maxAyahs = quranInfo.getNumberOfAyahs(sura)

      setState(PlaybackStateCompat.STATE_PLAYING)
      val pos = localPlayer.currentPosition
      val currentAyahTime = gaplessSuraData[ayah]
      Timber.d(
        "updateAudioPlayPosition: %d:%d, currently at %d vs expected at %d",
        sura, ayah, pos, currentAyahTime
      )
      val updatedAyah = if (currentAyahTime > pos) {
        // the ayah is ahead of the current playback time, so search backwards
        (1 until ayah).findLast { gaplessSuraData[it] <= pos } ?: 1
      } else {
        // the audio position is after this ayah, find out which ayah we're at
        ((ayah + 1)..maxAyahs).find { gaplessSuraData[it] > pos }?.minus(1) ?: maxAyahs
      }
      Timber.d(
        "updateAudioPlayPosition: %d:%d, decided ayah should be: %d",
        sura, ayah, updatedAyah
      )
      if (updatedAyah != ayah) {
        val ayahTime = gaplessSuraData[ayah]
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
            val seekPos = getSeekPosition(true)
            localPlayer.seekTo(seekPos)
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
        val ayahTime = gaplessSuraData[999]
        if (ayahTime in 1..pos) {
          val success = localAudioQueue.playAt(sura + 1, 1, false)
          if (success && localAudioQueue.getCurrentSura() == sura) {
            // remove any messages currently in the queue
            serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)

            // jump back to the ayah we should repeat and play it
            val seekPos = getSeekPosition(false)
            localPlayer.seekTo(seekPos)
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
      relaxResources(releaseExoPlayer = true, stopForeground = true)
      return
    }
    // actually play the file
    if (State.Stopped == state) {
      // If we're stopped, just go ahead to the next file and start playing
      playAudio(localAudioQueue.getCurrentSura() == 9 && localAudioQueue.getCurrentAyah() == 1)
    } else if (State.Paused == state) {
      // If we're paused, just continue playback and restore the
      // 'foreground service' state.
      state = State.Playing
      if (!isSetupAsForeground) {
        setUpAsForeground()
      }
      configAndStartExoPlayer()
      updateAudioPlaybackStatus()
    }
  }

  private fun processPauseRequest() {
    if (State.Playing == state) {
      // Pause exo player and cancel the 'foreground service' state.
      state = State.Paused
      serviceHandler.removeMessages(MSG_UPDATE_AUDIO_POS)
      player?.pause()
      setState(PlaybackStateCompat.STATE_PAUSED)
      // on jellybean and above, stay in the foreground and
      // update the notification.
      relaxResources(releaseExoPlayer = false, stopForeground = false)
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

      var seekTo = 0L
      var pos = localPlayer.currentPosition
      if (localAudioRequest.isGapless()) {
        seekTo = getSeekPosition(true)
        pos -= seekTo
      }

      if (pos > 1500 && !playerOverride) {
        localPlayer.seekTo(seekTo)
        state = State.Playing // in case we were paused
      } else {
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
    val shouldUpdatePlaybackSpeed = State.Playing === state || State.Preparing === state
    if (shouldUpdatePlaybackSpeed) {
      player?.setPlaybackSpeed(speed)
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
      relaxResources(releaseExoPlayer = false, stopForeground = true)
    }
    if (force || State.Stopped != state) {
      state = State.Stopped
      updateAudioPlaybackStatus()

      // let go of all resources...
      relaxResources(releaseExoPlayer = true, stopForeground = true)

      // service is no longer necessary. Will be started again if needed.
      serviceHandler.removeCallbacksAndMessages(null)
      stopSelf()
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
        localPlayer.duration
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
   * possibly the ExoPlayer.
   *
   * @param releaseExoPlayer Indicates whether the ExoPlayer should also
   * be released or not
   */
  private fun relaxResources(releaseExoPlayer: Boolean, stopForeground: Boolean) {
    if (stopForeground) {
      // stop being a foreground service
      stopForeground(true)
      isSetupAsForeground = false
    }

    // stop and release the ExoPlayer, if it's available
    val localPlayer = player
    if (releaseExoPlayer && localPlayer != null) {
      try {
        localPlayer.stop()
        localPlayer.release()
      } catch (_: IllegalStateException) {
        // nothing to do here  ¯\_(ツ)_/¯
      }
      player = null
      mediaSession.isActive = false
    }

  }

  /**
   * Configures and starts playback after the player is ready.
   */
  private fun configAndStartExoPlayer() {
    Timber.d("configAndStartExoPlayer()")

    val player = player ?: return

    if (shouldStop) {
      processStopRequest()
      shouldStop = false
      return
    }

    if (!player.isPlaying) {
      player.play()
    }
    state = State.Playing
    updateAudioPlaybackStatus()

    if (audioRequest?.isGapless() == true) {
      serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200)
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
    relaxResources(releaseExoPlayer = false, stopForeground = false)
    playerOverride = false

    val localAudioQueue = audioQueue
    val localAudioRequest = audioRequest
    if (localAudioQueue != null && localAudioRequest != null) {
      val dbPath = localAudioRequest.audioPathInfo.gaplessDatabase
      if (localAudioRequest.isGapless() && dbPath != null) {
        scope.launch {
          val timings = updateGaplessData(dbPath, localAudioQueue.getCurrentSura())
          playAudio(playRepeatSeparator, timings)
        }
      } else {
        playAudio(playRepeatSeparator, null)
      }
    }
  }

  private fun playAudio(playRepeatSeparator: Boolean, timings: SparseLongArray?) {
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

      val localPlayer = makeOrResetExoPlayer()
      setState(PlaybackStateCompat.STATE_CONNECTING)
      try {
        val mediaItem = if (overrideResource != 0) {
          // For raw resources, we need to create a URI pointing to the resource
          val resourceUri = "android.resource://$packageName/$overrideResource"
          playerOverride = true
          MediaItem.fromUri(resourceUri)
        } else {
          // For regular URLs (both local files and streaming URLs)
          overrideResource = 0
          MediaItem.fromUri(url)
        }

        val potentialTiming = timings?.get(localAudioQueue.getCurrentAyah(), -1) ?: -1
        if (potentialTiming > -1 && overrideResource == 0) {
          localPlayer.setMediaItem(mediaItem, potentialTiming)
        } else {
          localPlayer.setMediaItem(mediaItem)
        }
        localPlayer.prepare()
        Timber.d("prepared media item: $overrideResource, $url")
      } catch (e: IllegalStateException) {
        Timber.e("IllegalStateException while setting media item: %s", e.message)
        // ExoPlayer error handling will call onPlayerError, so just stop here
        processStopRequest(true)
        return
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
      position = localPlayer.currentPosition
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

  override fun onPlaybackStateChanged(playbackState: Int) {
    when (playbackState) {
      Player.STATE_READY -> {
        if (state == State.Preparing) {
          onPlayerReady()
        }
      }
      Player.STATE_ENDED -> {
        onPlayerCompleted()
      }
      Player.STATE_BUFFERING -> {
        onPlayerBuffering()
      }
      Player.STATE_IDLE -> {
        Timber.d("Player idle")
        if (state != State.Stopped) {
          state = State.Stopped
          updateAudioPlaybackStatus()
        }
      }
    }
  }

  // Called when seeking is complete
  override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int
  ) {
    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
      onSeekCompleted()
    }
  }

  // Called when the player starts or stops playing
  override fun onIsPlayingChanged(isPlaying: Boolean) {
    // Ensure UI stays in sync with actual ExoPlayer state changes
    // Only update state if it's a legitimate transition
    if (isPlaying && state == State.Paused) {
      state = State.Playing
      updateAudioPlaybackStatus()
    } else if (!isPlaying && state == State.Playing) {
      // Handle automatic pause (audio focus loss, etc.)
      state = State.Paused
      updateAudioPlaybackStatus()
    }
  }

  private fun onSeekCompleted() {
    val player = player ?: return
    Timber.d("seek complete! position: %d", player.currentPosition)

    audioRequest?.playbackSpeed?.let { speed ->
      processUpdatePlaybackSpeed(speed)
    }

    state = State.Playing
    if (!player.isPlaying) {
      player.play()
    }
    updateAudioPlaybackStatus()
    serviceHandler.sendEmptyMessageDelayed(MSG_UPDATE_AUDIO_POS, 200)
  }

  private fun onPlayerBuffering() {
    Timber.d("Player buffering...")
    val previousState = state
    state = State.Preparing

    val audioRequest = audioRequest
    // only actually update the status if we are streaming or if we are not stopped
    if (previousState != State.Stopped || (audioRequest == null || audioRequest.audioPathInfo.urlFormat.startsWith("http"))) {
      updateAudioPlaybackStatus()
    }
  }

  private fun onPlayerCompleted() {
    // The exo player finished playing the current file, so
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
          player?.seekTo(gaplessSuraData[localAudioQueue.getCurrentAyah()])
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

  private fun onPlayerReady() {
    Timber.d("okay, prepared!")

    // The exo player is done preparing. That means we can start playing!
    if (shouldStop) {
      processStopRequest()
      shouldStop = false
      return
    }

    // if gapless and sura changed, get the new data
    val localAudioRequest = audioRequest ?: return
    if (playerOverride || !localAudioRequest.isGapless()) {
      notifyAyahChanged()
    }
    updateNotification()
    configAndStartExoPlayer()
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
      val cd = ContextCompat.getColor(
        appContext,
        R.color.audio_notification_background_color
      ).toDrawable()
      val bitmap = createBitmap(iconWidth * 2, iconHeight * 2)
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
   * Called when there's an error playing media. When this happens, ExoPlayer
   * goes to the Error state. We warn the user about the error and
   * reset the exo player.
   */
  override fun onPlayerError(error: PlaybackException) {
    Timber.e("ExoPlayer Error: %s", error.toString())
    state = State.Stopped
    updateAudioPlaybackStatus()
    relaxResources(releaseExoPlayer = true, stopForeground = true)
  }

  override fun onDestroy() {
    compositeDisposable.clear()
    // Service is being killed, so make sure we release our resources
    serviceHandler.removeCallbacksAndMessages(null)
    serviceLooper.quitSafely()
    state = State.Stopped
    relaxResources(true, true)
    mediaSession.release()
    timingRepository.clear()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(arg0: Intent): IBinder? {
    return null
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


    // so user can pass in a serializable LegacyAudioRequest to the intent
    const val EXTRA_PLAY_INFO = "com.quran.labs.androidquran.PLAY_INFO"
    private const val NOTIFICATION_CHANNEL_ID = Constants.AUDIO_CHANNEL
    private const val MSG_INCOMING = 1
    private const val MSG_UPDATE_AUDIO_POS = 2
  }
}
