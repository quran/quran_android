package com.quran.mobile.feature.memorize.presenter

import android.app.Activity
import com.quran.data.di.ActivityScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.recording.RecordingPlayer
import com.quran.mobile.feature.memorize.recording.SessionPlayback
import com.quran.mobile.feature.memorize.session.MemorizeSessionController
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.recitation.common.RecitedAyah
import com.quran.recitation.common.RecitedSection
import com.quran.recitation.events.RecitationPlaybackEventPresenter
import com.quran.recitation.events.RecitationSelection
import com.quran.recitation.presenter.RecitationPlaybackPresenter
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Plays back the session's own recording (when the user chose to record) from the audio bar, once
 * listening is paused: the page follows the ayah being heard.
 */
@ActivityScope
class MemorizePlaybackPresenter @Inject constructor(
  private val controller: MemorizeSessionController,
  private val sessionPlayback: SessionPlayback,
  private val player: RecordingPlayer,
  private val playbackEvents: RecitationPlaybackEventPresenter,
) : RecitationPlaybackPresenter {

  private val selection = MutableStateFlow<RecitationSelection>(RecitationSelection.None)
  override val recitationPlaybackFlow: StateFlow<RecitationSelection> = selection.asStateFlow()

  private var scope: CoroutineScope? = null
  private var sessionRecordingId: String? = null

  override fun bind(what: Activity) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    this.scope = scope
    player.playback
      .onEach { playback ->
        val ofSession = playback != null && playback.recording.id == controller.state.value?.recordingId
        playbackEvents.onPlayingStateChange(ofSession && playback?.isPlaying == true)
        if (playback != null && ofSession) {
          val ayah = playback.recording.ayahAt(playback.positionMs)
          if (ayah != selection.value.ayah()) selection.value = RecitationSelection.Ayah(PlayedAyah(ayah))
        }
      }
      .launchIn(scope)
    controller.state
      .onEach { state ->
        // listening again, or the session is over: the playback of its recording stops
        val playing = player.playback.value?.recording?.id
        val ofSession = state?.recordingId ?: sessionRecordingId
        if (playing != null && playing == ofSession && (state == null || state.status == MemorizeState.Status.LISTENING)) {
          player.stop()
          playbackEvents.onPlayingStateChange(false)
        }
        sessionRecordingId = state?.recordingId
      }
      .launchIn(scope)
  }

  override fun unbind(what: Activity) {
    // the screen is going away: don't keep talking in the background
    player.pause()
    scope?.cancel()
    scope = null
  }

  override fun play() = sessionPlayback.play()

  override fun pauseIfPlaying() = player.pause()

  private class PlayedAyah(private val ayah: SuraAyah) : RecitedAyah {
    override fun ayah(): SuraAyah = ayah
    override fun recitedSections(): List<RecitedSection> = emptyList()
  }
}
