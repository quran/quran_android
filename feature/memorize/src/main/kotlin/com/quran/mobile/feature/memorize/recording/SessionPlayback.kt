package com.quran.mobile.feature.memorize.recording

import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.mobile.feature.memorize.session.MemorizeState
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays back what has been recorded of the current session (from the audio bar or the mic button). */
@SingleIn(AppScope::class)
class SessionPlayback @Inject constructor(
  private val controller: MemorizeSessionController,
  private val recordings: RecordingStore,
  private val player: RecordingPlayer,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /** Whether the current session is being recorded. */
  val canPlay: Boolean get() = controller.state.value?.recordingId != null

  /** Whether the session's recording is playing. */
  val isPlaying: Boolean
    get() = player.playback.value?.let { it.isPlaying && it.recording.id == controller.state.value?.recordingId } == true

  fun play() {
    val state = controller.state.value ?: return
    val session = state.recordingId ?: return
    // the microphone mustn't hear the playback
    if (state.status == MemorizeState.Status.LISTENING) controller.pauseOrResume()
    scope.launch {
      val recording = withContext(Dispatchers.IO) { recordings.session(session) } ?: return@launch
      player.play(recording)
    }
  }

  fun pause() = player.pause()
}
