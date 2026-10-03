package org.quran.app.data
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.quran.app.domain.*
private lateinit var appContext:Context
internal fun platformContext(): Context = appContext
fun initializePlatform(context:Context) { appContext=context.applicationContext }
actual fun platformSettingsStore():SettingsStore = object:SettingsStore {
 private val preferences=appContext.getSharedPreferences("quran.study",Context.MODE_PRIVATE)
 override fun get(key:String):String?=preferences.getString(key,null)
 override fun set(key:String,value:String) { check(preferences.edit().putString(key,value).commit()) { "Could not save study progress" } }
}
actual fun platformAudioPlayer():AudioPlayer = AndroidAudioPlayer(appContext)
private class AndroidAudioPlayer(context: Context) : AudioPlayer {
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
 init {
  future.addListener({
   if (!released) {
    runCatching { future.get() }.onSuccess { ready ->
     controller = ready
     ready.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && shouldPlay) completed?.invoke() }
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
 override fun play(onCompleted: () -> Unit, onError: (String) -> Unit) {
  completed = onCompleted
  failed = onError
  if (connectionFailed) { onError("Playback service could not be connected"); return }
  if (uri == null || released) { onError("Import a recording first"); return }
  shouldPlay = true
  controller?.run { if (playbackState == Player.STATE_ENDED) seekTo(0); play() }
 }
 override fun pause() { shouldPlay = false; controller?.pause() }
 override fun release() {
  pause()
  released = true
  completed = null
  failed = null
  androidx.media3.session.MediaController.releaseFuture(future)
  controller = null
 }
}
