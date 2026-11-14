package com.quran.mobile.feature.memorize.recording

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays the user's recordings back (all of a recording's parts in a row). Main thread only. */
@SingleIn(AppScope::class)
class RecordingPlayer @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {
  data class Playback(val recording: Recording, val positionMs: Long, val isPlaying: Boolean)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val _playback = MutableStateFlow<Playback?>(null)
  /** What is loaded, where it is and whether it's playing; null when nothing is. */
  val playback: StateFlow<Playback?> = _playback.asStateFlow()

  private var player: ExoPlayer? = null
  private var ticker: Job? = null

  private val listener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

    override fun onPlaybackStateChanged(playbackState: Int) {
      if (playbackState == Player.STATE_ENDED) player?.run {
        pause()
        seekTo(0, 0)
      }
      publish()
    }
  }

  /** Plays [recording], from where it was paused if it is the one loaded. */
  fun play(recording: Recording) {
    val player = load(recording)
    player.play()
    tick()
  }

  /** Goes to [positionMs] of [recording] (loading it if needed), playing or not as it was. */
  fun seekTo(recording: Recording, positionMs: Long) {
    val player = load(recording)
    val (part, offset) = partAt(recording.parts, positionMs)
    player.seekTo(part, offset)
    _playback.value = Playback(recording, position(recording, player), player.isPlaying)
  }

  /** Moves [deltaMs] forward (or back) in what is loaded. */
  fun skip(deltaMs: Long) {
    val playback = _playback.value ?: return
    seekTo(playback.recording, (playback.positionMs + deltaMs).coerceIn(0, playback.recording.durationMs))
  }

  fun pause() {
    player?.pause()
  }

  private fun load(recording: Recording): ExoPlayer {
    val player = player ?: ExoPlayer.Builder(context).build().also {
      it.setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
        true,
      )
      it.addListener(listener)
      player = it
    }
    val loaded = _playback.value?.recording
    if (loaded?.id != recording.id || loaded.parts != recording.parts) {
      player.setMediaItems(recording.parts.map { MediaItem.fromUri(Uri.fromFile(it.file)) })
      player.prepare()
    }
    _playback.value = Playback(recording, position(recording, player), player.isPlaying)
    return player
  }

  fun stop() {
    ticker?.cancel()
    player?.release()
    player = null
    _playback.value = null
  }

  private fun tick() {
    if (ticker?.isActive == true) return
    ticker = scope.launch {
      while (isActive) {
        publish()
        delay(TICK_MS)
      }
    }
  }

  private fun publish() {
    val player = player ?: return
    val recording = _playback.value?.recording ?: return
    _playback.value = Playback(recording, position(recording, player), player.isPlaying)
    if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) ticker?.cancel()
  }

  private fun position(recording: Recording, player: ExoPlayer): Long =
    recording.parts.take(player.currentMediaItemIndex).sumOf { it.durationMs } + player.currentPosition

  internal companion object {
    // often enough for the word being heard to be lit as it is said
    private const val TICK_MS = 100L

    /** Which of [parts] (played one after the other) holds [positionMs], and where in it. */
    fun partAt(parts: List<Recording.Part>, positionMs: Long): Pair<Int, Long> {
      var start = 0L
      parts.forEachIndexed { index, part ->
        if (positionMs < start + part.durationMs || index == parts.lastIndex) {
          return index to (positionMs - start).coerceIn(0, part.durationMs)
        }
        start += part.durationMs
      }
      return 0 to 0L
    }
  }
}
