package com.quran.labs.autoquran

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.net.toUri
import androidx.media.MediaBrowserServiceCompat
import androidx.media.utils.MediaConstants
import com.quran.data.core.QuranInfo
import com.quran.labs.autoquran.common.getSuraName
import com.quran.labs.autoquran.common.makeThreeDigit
import com.quran.labs.autoquran.di.DaggerServiceComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class QuranAudioService : MediaBrowserServiceCompat(), MediaPlayer.OnPreparedListener {
  @Inject
  lateinit var quranInfo: QuranInfo
  private lateinit var mSession: MediaSessionCompat
  private var job: Job? = null
  var mediaPlayer: MediaPlayer? = null
  val mediaItems = mutableListOf(
      createBrowsableMediaItem("quran", "Quran")
  )

  override fun onCreate() {
    super.onCreate()
    mSession = MediaSessionCompat(baseContext, "QuranAudioService")
    setSessionToken(mSession.sessionToken)
    mSession.setCallback(MediaSessionCallback())
    val component = DaggerServiceComponent.create()
    component.inject(this)
    quranInfo.suraPageStart.forEachIndexed { index, _ ->
      val name = getSuraName(this, index + 1, true, false)
      mediaItems.add(
          createMediaItem(
              name, name, "Quran", "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/${makeThreeDigit(index + 1)}.mp3".toUri()
          )
      )
    }
  }

  override fun onDestroy() {
    mSession.release()
    mediaPlayer?.release()
  }

  override fun onGetRoot(clientPackageName: String,
                         clientUid: Int,
                         rootHints: Bundle?): BrowserRoot {
    return BrowserRoot("root", null)
  }

  override fun onLoadChildren(parentMediaId: String,
                              result: Result<List<MediaItem>>) {
    result.sendResult(mediaItems)
  }

  private fun createBrowsableMediaItem(
      mediaId: String,
      folderName: String
  ): MediaItem {
    val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
    mediaDescriptionBuilder.setMediaId(mediaId)
    mediaDescriptionBuilder.setTitle(folderName)
    val extras = Bundle()
    extras.putInt(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
    )
    extras.putInt(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
    )
    extras.putInt(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
        MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    )
    extras.putDouble(
        MediaConstants.DESCRIPTION_EXTRAS_KEY_COMPLETION_PERCENTAGE,
        0.0
    )
    mediaDescriptionBuilder.setExtras(extras)
    return MediaItem(
        mediaDescriptionBuilder.build(), MediaItem.FLAG_BROWSABLE)
  }

  private fun createMediaItem(mediaId: String, title: String, artist: String, uri: Uri): MediaItem {
    val playbackStateExtras = Bundle()
    playbackStateExtras.putString(
        MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID, mediaId)
    val description = MediaDescriptionCompat.Builder()
        .setMediaId(mediaId)
        .setTitle(title)
        .setSubtitle(artist)
        .setMediaUri(uri)
        .setExtras(playbackStateExtras)
        .build()

    return MediaItem(description, MediaItem.FLAG_PLAYABLE)
  }

  override fun onPrepared(mp: MediaPlayer) {
    mp.start()
    setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    startUpdatingProgress(mediaPlayer!!)
  }

  private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
    override fun onPlay() {
      if (mediaPlayer?.isPlaying == false) {
        mediaPlayer?.start()
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startUpdatingProgress(mediaPlayer!!)
      }
    }

    override fun onSkipToQueueItem(queueId: Long) {}
    override fun onSeekTo(position: Long) {}
    override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
      val mediaUrl: MediaItem? = mediaItems.find { mediaId == it.description.mediaId }
      prepareMediaItem(mediaUrl)
    }

    override fun onPause() {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.pause()
        setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer?.duration?.div(100)?.toLong())
        stopUpdatingProgress()
      }
    }

    override fun onStop() {
      mediaPlayer?.stop()
    }

    override fun onSkipToNext() {}
    override fun onSkipToPrevious() {}
    override fun onCustomAction(action: String, extras: Bundle) {}
    override fun onPlayFromSearch(query: String, extras: Bundle) {}
  }

  private fun prepareMediaItem(mediaUrl: MediaItem?) {
    if (mediaUrl != null) {
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes()
        setMetaData(mediaUrl)
        setOnPreparedListener(this@QuranAudioService)
        setDataSource(baseContext, mediaUrl.description.mediaUri ?: Uri.EMPTY)
        prepareAsync()
      }
    }
  }

  private fun MediaPlayer.setAudioAttributes() {
    setAudioAttributes(
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
    )
  }

  private fun setMetaData(mediaUrl: MediaItem) {
    mSession.setMetadata(
        MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaUrl.description.title.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, mediaUrl.description.subtitle.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaUrl.description.mediaId.toString())
            .build()
    )
  }

  private fun setMediaPlaybackState(state: Int, currentPosition: Long? = 0) {
    mSession.setPlaybackState(
        PlaybackStateCompat.Builder()
            .setState(state, currentPosition ?: 0, 1.0f, SystemClock.elapsedRealtime())
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO)
            .build()
    )
  }

  private fun startUpdatingProgress(mediaPlayer: MediaPlayer) {
    job = CoroutineScope(Dispatchers.Main).launch {
      while (mediaPlayer.isPlaying) {
        val currentPosition = mediaPlayer.currentPosition
        setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING, currentPosition.toLong())
        delay(1000) // Update progress every second, adjust as needed
      }
    }
    job?.start()
  }

  private fun stopUpdatingProgress() {
    job?.cancel()
  }
}
