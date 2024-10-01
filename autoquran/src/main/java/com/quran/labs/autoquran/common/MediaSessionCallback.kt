package com.quran.labs.autoquran.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MediaSessionCallback(
  private val context: Context,
  private val setMediaPlayer: (mediaPlayer: MediaPlayer) -> Unit,
  private val setMetaData: (mediaUrl: MediaItem) -> Unit,
  private val setMediaPlaybackState: (state: Int, position: Long?) -> Unit,
) : MediaSessionCompat.Callback(), OnPreparedListener, MediaPlayer.OnCompletionListener {

  private var mediaPlayer: MediaPlayer = MediaPlayer().apply {
    setAudioAttributes()
    setOnPreparedListener(this@MediaSessionCallback)
    setOnCompletionListener(this@MediaSessionCallback)
  }
  private var currentMediaItem: MediaItem? = null
  private var job: Job = Job()
  private var mediaItems = mutableListOf<MediaItem>()

  fun setMediaItems(mediaItems: MutableList<MediaItem>) {
    this.mediaItems = mediaItems
  }

  override fun onPlay() {
    if (mediaPlayer.isPlaying.not()) {
      mediaPlayer.start()
      setMediaPlaybackState(STATE_PLAYING, mediaPlayer.currentPosition.toLong())
      mediaPlayer.startProgress()
    }
  }

  override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
    if (mediaPlayer.isPlaying) {
      mediaPlayer.stop()
      setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED, null)
    }
    val mediaItem: MediaItem = mediaItems.find { mediaId == it.description.mediaId }
      ?: throw IllegalStateException("MediaItem not found")
    currentMediaItem = mediaItem
    mediaItem.prepare()
  }

  override fun onSeekTo(position: Long) {
    mediaPlayer.seekTo(position.toInt())
  }

  override fun onPause() {
    if (mediaPlayer.isPlaying) {
      mediaPlayer.pause()
      setMediaPlaybackState(STATE_PAUSED, mediaPlayer.currentPosition.toLong())
      stopProgress()
    }
  }

  override fun onStop() {
    mediaPlayer.stop()
    setMediaPlaybackState(PlaybackStateCompat.STATE_STOPPED, null)
    stopProgress()
  }

  override fun onSkipToNext() {
    skipTo(next = true)
  }

  override fun onSkipToPrevious() {
    skipTo(next = false)
  }

  override fun onPrepared(mp: MediaPlayer) {
    mp.start()
    setMetaData(currentMediaItem!!)
    setMediaPlaybackState(STATE_PLAYING, null)
    mp.startProgress()
  }

  override fun onCompletion(mp: MediaPlayer?) {
    mp?.pause()
    setMediaPlaybackState(STATE_PAUSED, null)
    stopProgress()
  }

  private fun skipTo(next: Boolean) {
    val mediaIndexLimit = if (next) -1 else 1
    val mediaIndexIncrement = if (next) 1 else -1
    val currentMediaIndex = mediaItems.indexOf(currentMediaItem)
    if (currentMediaIndex > mediaIndexLimit) {
      if (mediaPlayer.isPlaying) {
        mediaPlayer.stop()
      }
      val nextMediaItem = mediaItems[currentMediaIndex + mediaIndexIncrement]
      currentMediaItem = nextMediaItem
      nextMediaItem.prepare()
    }
  }

  private fun MediaItem.prepare() {
    setMetaData(this@prepare)
    mediaPlayer.reset()
    mediaPlayer.setDataSource(context, this@prepare.description.mediaUri ?: Uri.EMPTY)
    mediaPlayer.prepareAsync()
    setMediaPlayer(mediaPlayer)
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

  private fun MediaPlayer.startProgress() {
    job = CoroutineScope(Dispatchers.Main).launch {
      while (this@startProgress.isPlaying) {
        val currentPosition = mediaPlayer.currentPosition
        setMediaPlaybackState(STATE_PLAYING, currentPosition.toLong())
        delay(1000) // Update progress every second, adjust as needed
      }
    }
    job.start()
  }

  private fun stopProgress() {
    job.cancel()
  }
}

