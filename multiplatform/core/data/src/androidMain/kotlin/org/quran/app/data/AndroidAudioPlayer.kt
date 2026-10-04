package org.quran.app.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.quran.app.domain.AudioPlayer

internal class AndroidAudioPlayer(context: Context) : AudioPlayer {
 private val executor = java.util.concurrent.Executor { command -> android.os.Handler(android.os.Looper.getMainLooper()).post(command) }
 private val future = androidx.media3.session.MediaController.Builder(context,
     androidx.media3.session.SessionToken(context, android.content.ComponentName(context, QuranPlaybackService::class.java))).buildAsync()
 private var controller: androidx.media3.session.MediaController? = null
 private var uri: String? = null
 private var shouldPlay = false
 private var released = false
 private var connectionFailed = false
 private var completed: (() -> Unit)? = null
 private var failed: ((String) -> Unit)? = null
 private var playbackChanged: ((Boolean) -> Unit)? = null
 init {
  future.addListener({
   if (!released) {
    runCatching { future.get() }.onSuccess { ready ->
     controller = ready
     ready.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && shouldPlay) completed?.invoke() }
      override fun onIsPlayingChanged(isPlaying: Boolean) {
       if (shouldPlay && controller?.playbackState != Player.STATE_ENDED) playbackChanged?.invoke(isPlaying)
      }
      override fun onPlayerError(error: androidx.media3.common.PlaybackException) { shouldPlay = false; failed?.invoke("Recording could not be played") }
     })
     uri?.let { ready.setMediaItem(MediaItem.fromUri(it)); ready.prepare() }
     if (shouldPlay) ready.play()
    }.onFailure { connectionFailed = true; shouldPlay = false; failed?.invoke("Playback service could not be connected") }
   }
  }, executor)
 }
 override fun loadLocal(uri: String) {
  require(uri.startsWith("content://") || uri.startsWith("file://")) { "Only a local recording can be imported" }
  shouldPlay = false
  this.uri = uri
  controller?.run { stop(); setMediaItem(MediaItem.fromUri(uri)); prepare() }
 }
 override fun play(onCompleted: () -> Unit, onError: (String) -> Unit, onPlaybackChanged: (Boolean) -> Unit) {
  completed = onCompleted
  failed = onError
  playbackChanged = onPlaybackChanged
  if (connectionFailed) { onError("Playback service could not be connected"); return }
  if (uri == null || released) { onError("Import a recording first"); return }
  shouldPlay = true
  controller?.run { if (playbackState == Player.STATE_ENDED) seekTo(0); play() }
 }
 override fun pause() { shouldPlay = false; controller?.pause() }
 override fun clearLocal() {
  shouldPlay = false
  uri = null
  completed = null
  failed = null
  playbackChanged = null
  controller?.run { stop(); clearMediaItems() }
 }
 override fun release() {
  clearLocal()
  released = true
  completed = null
  failed = null
  playbackChanged = null
  androidx.media3.session.MediaController.releaseFuture(future)
  controller = null
 }
}
