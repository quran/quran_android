package com.quran.labs.feature.autoquran

import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.quran.labs.feature.autoquran.common.MediaSessionCallback
import com.quran.labs.feature.autoquran.common.SurahBuilder
import com.quran.labs.feature.autoquran.common.filterWithQari
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.mobile.di.QuranApplicationComponentProvider
import javax.inject.Inject

class QuranAudioService : MediaBrowserServiceCompat() {
  @Inject
  lateinit var surahBuilder: SurahBuilder
  private lateinit var session: MediaSessionCompat
  private var mediaPlayer: MediaPlayer? = null
  private var mediaItems = mutableListOf<MediaItem>()
  private val mediaSessionCallback = MediaSessionCallback(
    context = this,
    setMediaPlayer = { mediaPlayer = it },
    setMetaData = this::setMetaData,
    setMediaPlaybackState = this::setMediaPlaybackState,
  )

  override fun onCreate() {
    super.onCreate()
    val injector = (application as? QuranApplicationComponentProvider)
      ?.provideQuranApplicationComponent() as? QuranAutoInjector
    injector?.inject(this)
    mediaItems = surahBuilder.create(this)
    session = MediaSessionCompat(baseContext, "QuranAudioService")
    setSessionToken(session.sessionToken)
    mediaSessionCallback.setMediaItems(mediaItems)
    session.setCallback(mediaSessionCallback)
  }

  override fun onDestroy() {
    session.release()
    mediaPlayer?.release()
    super.onDestroy()
  }

  override fun onGetRoot(
    clientPackageName: String,
    clientUid: Int,
    rootHints: Bundle?
  ): BrowserRoot {
    return BrowserRoot("root", null)
  }

  override fun onLoadChildren(
    parentMediaId: String,
    result: Result<List<MediaItem>>
  ) {
    when (parentMediaId) {
      "root" -> result.sendResult(mediaItems)
      else -> result.sendResult(mediaItems.filterWithQari(parentMediaId))
    }
  }


  private fun setMediaPlaybackState(state: Int, currentPosition: Long? = 0) {
    session.setPlaybackState(
      PlaybackStateCompat.Builder()
        .setState(state, currentPosition ?: 0, 1.0f)
        .setActions(
          PlaybackStateCompat.ACTION_PLAY or
              PlaybackStateCompat.ACTION_PAUSE or
              PlaybackStateCompat.ACTION_STOP or
              PlaybackStateCompat.ACTION_SEEK_TO or
              PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
              PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        .build()
    )
  }

  private fun setMetaData(mediaUrl: MediaItem) {
    session.setMetadata(
      MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, mediaUrl.description.title.toString())
        .putString(
          MediaMetadataCompat.METADATA_KEY_ARTIST,
          mediaUrl.description.subtitle.toString()
        )
        .putString(
          MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
          mediaUrl.description.mediaId.toString()
        )
        .putLong(
          MediaMetadataCompat.METADATA_KEY_DURATION,
          mediaPlayer?.duration?.toLong() ?: 0
        )
        .build()
    )
  }
}
