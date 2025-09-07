package com.quran.mobile.feature.audiobar.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.quran.data.di.QuranScope
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import com.quran.labs.androidquran.common.audio.model.playback.PlaybackStatus
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.common.ui.core.R
import com.quran.mobile.feature.audiobar.state.AudioBarEvent
import com.quran.mobile.feature.audiobar.state.AudioBarState
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvent
import com.quran.mobile.feature.audiobar.state.AudioBarUiEvents
import com.quran.recitation.common.RecitationSession
import com.quran.recitation.events.RecitationEventPresenter
import com.quran.recitation.events.RecitationPlaybackEventPresenter
import com.quran.recitation.presenter.RecitationPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@QuranScope
class AudioBarPresenter @Inject constructor(
  downloadInfoStreams: DownloadInfoStreams,
  recitationEventPresenter: RecitationEventPresenter,
  recitationPlaybackEventPresenter: RecitationPlaybackEventPresenter,
  private val audioStatusRepository: AudioStatusRepository,
  private val currentQariManager: CurrentQariManager,
  private val recitationPresenter: RecitationPresenter,
  private val audioBarEventRepository: AudioBarEventRepository
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val internalAudioBarFlow =
    MutableStateFlow<AudioBarState>(AudioBarState.Loading(-1, R.string.loading))

  init {
    downloadInfoStreams.downloadInfoStream()
      .map { downloadInfo -> downloadInfo.toAudioBarState() }
      .onEach { internalAudioBarFlow.value = it }
      .launchIn(scope)

    combine(
      audioStatusRepository.audioPlaybackFlow,
      currentQariManager.flow(),
      recitationPresenter.isRecitationEnabledFlow(),
    ) { audioStatus, qari, isRecitationEnabled ->
      transformAudioBarState(audioStatus, isRecitationEnabled, qari)
    }
      .onEach { internalAudioBarFlow.value = it }
      .launchIn(scope)

    combine(
      recitationPresenter.isRecitationEnabledFlow(),
      recitationEventPresenter.recitationSessionFlow,
      recitationEventPresenter.listeningStateFlow,
      recitationPlaybackEventPresenter.playingStateFlow
    ) { isRecitationEnabled, recitationSession, isListening, isPlaying ->
      if (isRecitationEnabled) {
        transformAudioBarRecitationState(recitationSession, isListening, isPlaying)
      } else {
        null
      }
    }
      .filterNotNull()
      .onEach { internalAudioBarFlow.value = it }
      .launchIn(scope)
  }

  @Composable
  internal fun audioBarPresenter(): AudioBarState {
    val state = internalAudioBarFlow.collectAsState()
    return state.value
  }

  internal fun eventListeners(): AudioBarUiEvents {
    return AudioBarUiEvents(
      commonPlaybackEventSink,
      playingPlaybackEventSink,
      pausedPlaybackEvent,
      stoppedEventSink,
      downloadingEventSink,
      cancelableEventSink,
      promptEventSink,
      commonRecordingEventSink,
      recitationListeningEventSink,
      recitationPlayingEventSink,
      recitationStoppedEventSink
    )
  }

  private fun DownloadInfo.toAudioBarState(): AudioBarState {
    return when (this) {
      is DownloadInfo.DownloadBatchError -> AudioBarState.Error(errorResource)

      is DownloadInfo.FileDownloadProgress -> AudioBarState.Downloading(
        progress, com.quran.mobile.common.download.R.string.downloading
      )

      is DownloadInfo.DownloadBatchSuccess -> AudioBarState.Stopped(
        currentQariManager.currentQari().nameResource,
        recitationPresenter.isRecitationEnabled()
      )

      DownloadInfo.RequestDownloadNetworkPermission -> AudioBarState.Prompt(
        com.quran.mobile.common.download.R.string.download_non_wifi_prompt
      )

      is DownloadInfo.DownloadRequested, is DownloadInfo.DownloadEvent -> AudioBarState.Downloading(
        -1, com.quran.mobile.common.download.R.string.downloading
      )
    }
  }

  private fun transformAudioBarState(
    audioStatus: AudioStatus, enableRecitation: Boolean, qari: Qari
  ): AudioBarState {
    return when (audioStatus) {
      is AudioStatus.Playback -> {
        when (audioStatus.playbackStatus) {
          PlaybackStatus.PREPARING -> AudioBarState.Loading(-1, R.string.loading)

          PlaybackStatus.PLAYING -> AudioBarState.Playing(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed
          )

          PlaybackStatus.PAUSED -> AudioBarState.Paused(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed
          )
        }
      }

      AudioStatus.Stopped -> AudioBarState.Stopped(qari.nameResource, enableRecitation)
    }
  }

  private fun transformAudioBarRecitationState(
    recitationSession: RecitationSession?, isListening: Boolean, isPlaying: Boolean
  ): AudioBarState? {
    return if (recitationSession == null) {
      null
    } else if (isListening) {
      AudioBarState.RecitationListening
    } else if (isPlaying) {
      AudioBarState.RecitationPlaying
    } else {
      AudioBarState.RecitationStopped
    }
  }

  private fun emit(event: AudioBarEvent) {
    audioBarEventRepository.onAudioBarEvent(event)
  }

  // event sinks - these only emit the underlying mapped event type to the stream today
  // we could easily specialize the handling of events per sink type in the future if necessary
  private val downloadingEventSink =
    { event: AudioBarUiEvent.DownloadingPlaybackEvent ->
      if (event is AudioBarUiEvent.DownloadingPlaybackEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarState.Downloading(-1, R.string.canceling)
      }
      emit(event.audioBarEvent)
    }

  private val cancelableEventSink =
    { event: AudioBarUiEvent.CancelablePlaybackEvent ->
      if (event is AudioBarUiEvent.CancelablePlaybackEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled()
        )
      }
      emit(event.audioBarEvent)
    }

  private val promptEventSink =
    { event: AudioBarUiEvent.PromptEvent ->
      if (event is AudioBarUiEvent.PromptEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled()
        )
      }
      emit(event.audioBarEvent)
    }

  private val commonPlaybackEventSink =
    { event: AudioBarUiEvent.CommonPlaybackEvent ->
      if (event is AudioBarUiEvent.CommonPlaybackEvent.Stop) {
        internalAudioBarFlow.value = AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled()
        )
      } else if (event is AudioBarUiEvent.CommonPlaybackEvent.SetRepeat) {
        // pre-emptively update the ui with the repeat even before the service gets it
        val currentAudioBarValue = internalAudioBarFlow.value
        if (currentAudioBarValue is AudioBarState.Playing) {
          internalAudioBarFlow.value = currentAudioBarValue.copy(repeat = event.repeat)
        }
      } else if (event is AudioBarUiEvent.CommonPlaybackEvent.SetSpeed) {
        // pre-emptively update the ui with the speed even before the service gets it
        val currentAudioBarValue = internalAudioBarFlow.value
        if (currentAudioBarValue is AudioBarState.Playing) {
          internalAudioBarFlow.value = currentAudioBarValue.copy(speed = event.speed)
        }
      }
      emit(event.audioBarEvent)
    }

  private val playingPlaybackEventSink =
    { event: AudioBarUiEvent.PlayingPlaybackEvent ->
      if (event is AudioBarUiEvent.PlayingPlaybackEvent.Pause) {
        val audioStatus = audioStatusRepository.audioPlaybackFlow.value
        if (audioStatus is AudioStatus.Playback) {
          internalAudioBarFlow.value = AudioBarState.Paused(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed
          )
        }
      }
      emit(event.audioBarEvent)
    }

  private val pausedPlaybackEvent =
    { event: AudioBarUiEvent.PausedPlaybackEvent -> emit(event.audioBarEvent) }
  private val stoppedEventSink =
    { event: AudioBarUiEvent.StoppedPlaybackEvent -> emit(event.audioBarEvent) }
  private val commonRecordingEventSink =
    { event: AudioBarUiEvent.CommonRecordingEvent -> emit(event.audioBarEvent) }
  private val recitationListeningEventSink =
    { event: AudioBarUiEvent.RecitationListeningEvent -> emit(event.audioBarEvent) }
  private val recitationPlayingEventSink =
    { event: AudioBarUiEvent.RecitationPlayingEvent -> emit(event.audioBarEvent) }
  private val recitationStoppedEventSink =
    { event: AudioBarUiEvent.RecitationStoppedEvent -> emit(event.audioBarEvent) }
}
