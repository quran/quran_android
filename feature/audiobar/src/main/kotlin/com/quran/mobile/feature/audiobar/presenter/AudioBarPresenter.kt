package com.quran.mobile.feature.audiobar.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.quran.data.di.QuranReadingScope
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
import com.quran.mobile.feature.audiobar.state.AudioBarScreen
import com.quran.recitation.common.RecitationSession
import com.quran.recitation.events.RecitationEventPresenter
import com.quran.recitation.events.RecitationPlaybackEventPresenter
import com.quran.recitation.presenter.RecitationPresenter
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.presenter.Presenter
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

@CircuitInject(screen = AudioBarScreen::class, scope = QuranReadingScope::class)
@QuranScope
class AudioBarPresenter @Inject constructor(
  downloadInfoStreams: DownloadInfoStreams,
  recitationEventPresenter: RecitationEventPresenter,
  recitationPlaybackEventPresenter: RecitationPlaybackEventPresenter,
  private val audioStatusRepository: AudioStatusRepository,
  private val currentQariManager: CurrentQariManager,
  private val recitationPresenter: RecitationPresenter,
  private val audioBarEventRepository: AudioBarEventRepository
) : Presenter<AudioBarScreen.AudioBarState> {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val internalAudioBarFlow =
    MutableStateFlow<AudioBarScreen.AudioBarState>(AudioBarScreen.AudioBarState.Loading(
      -1,
      R.string.loading
    ) { /* noop */ })

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
  override fun present(): AudioBarScreen.AudioBarState {
    val state = internalAudioBarFlow.collectAsState()
    return state.value
  }

  private fun DownloadInfo.toAudioBarState(): AudioBarScreen.AudioBarState {
    return when (this) {
      is DownloadInfo.DownloadBatchError -> AudioBarScreen.AudioBarState.Error(
        errorId, errorEventSink
      )

      is DownloadInfo.FileDownloadProgress -> AudioBarScreen.AudioBarState.Loading(
        progress, com.quran.mobile.common.download.R.string.downloading, loadingEventSink
      )

      is DownloadInfo.FileDownloaded, is DownloadInfo.DownloadBatchSuccess -> AudioBarScreen.AudioBarState.Stopped(
        currentQariManager.currentQari().nameResource,
        recitationPresenter.isRecitationEnabled(),
        stoppedEventSink
      )

      DownloadInfo.RequestDownloadNetworkPermission -> AudioBarScreen.AudioBarState.Prompt(
        com.quran.mobile.common.download.R.string.download_non_wifi_prompt, promptEventSink
      )

      is DownloadInfo.DownloadRequested, is DownloadInfo.DownloadEvent -> AudioBarScreen.AudioBarState.Loading(
        -1, com.quran.mobile.common.download.R.string.downloading, loadingEventSink
      )
    }
  }

  private fun transformAudioBarState(
    audioStatus: AudioStatus, enableRecitation: Boolean, qari: Qari
  ): AudioBarScreen.AudioBarState {
    return when (audioStatus) {
      is AudioStatus.Playback -> {
        when (audioStatus.playbackStatus) {
          PlaybackStatus.PREPARING -> AudioBarScreen.AudioBarState.Loading(
            -1, R.string.loading, loadingEventSink
          )

          PlaybackStatus.PLAYING -> AudioBarScreen.AudioBarState.Playing(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed,
            commonPlaybackEventSink,
            playingPlaybackEventSink
          )

          PlaybackStatus.PAUSED -> AudioBarScreen.AudioBarState.Paused(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed,
            commonPlaybackEventSink,
            pausedPlaybackEvent
          )
        }
      }

      AudioStatus.Stopped -> AudioBarScreen.AudioBarState.Stopped(
        qari.nameResource, enableRecitation, stoppedEventSink
      )
    }
  }

  private fun transformAudioBarRecitationState(
    recitationSession: RecitationSession?, isListening: Boolean, isPlaying: Boolean
  ): AudioBarScreen.AudioBarState? {
    return if (recitationSession == null) {
      null
    } else if (isListening) {
      AudioBarScreen.AudioBarState.RecitationListening(
        commonRecordingEventSink, recitationListeningEventSink
      )
    } else if (isPlaying) {
      AudioBarScreen.AudioBarState.RecitationPlaying(
        commonRecordingEventSink, recitationPlayingEventSink
      )
    } else {
      AudioBarScreen.AudioBarState.RecitationStopped(
        commonRecordingEventSink, recitationStoppedEventSink
      )
    }
  }

  private fun emit(event: AudioBarEvent) {
    audioBarEventRepository.onAudioBarEvent(event)
  }

  // event sinks - these only emit the underlying mapped event type to the stream today
  // we could easily specialize the handling of events per sink type in the future if necessary
  private val loadingEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.LoadingPlaybackEvent ->
      if (event is AudioBarScreen.AudioBarUiEvent.LoadingPlaybackEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarScreen.AudioBarState.Loading(
          -1, R.string.canceling
        ) { /* can't cancel a cancel */ }
      }
      emit(event.audioBarEvent)
    }

  private val errorEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.ErrorPlaybackEvent ->
      if (event is AudioBarScreen.AudioBarUiEvent.ErrorPlaybackEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarScreen.AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled(),
          stoppedEventSink
        )
      }
      emit(event.audioBarEvent)
    }

  private val promptEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.PromptEvent ->
      if (event is AudioBarScreen.AudioBarUiEvent.PromptEvent.Cancel) {
        internalAudioBarFlow.value = AudioBarScreen.AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled(),
          stoppedEventSink
        )
      }
      emit(event.audioBarEvent)
    }

  private val commonPlaybackEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent ->
      if (event is AudioBarScreen.AudioBarUiEvent.CommonPlaybackEvent.Stop) {
        internalAudioBarFlow.value = AudioBarScreen.AudioBarState.Stopped(
          currentQariManager.currentQari().nameResource,
          recitationPresenter.isRecitationEnabled(),
          stoppedEventSink
        )
      }
      emit(event.audioBarEvent)
    }

  private val playingPlaybackEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.PlayingPlaybackEvent ->
      if (event is AudioBarScreen.AudioBarUiEvent.PlayingPlaybackEvent.Pause) {
        val audioStatus = audioStatusRepository.audioPlaybackFlow.value
        if (audioStatus is AudioStatus.Playback) {
          internalAudioBarFlow.value = AudioBarScreen.AudioBarState.Paused(
            audioStatus.audioRequest.repeatInfo,
            audioStatus.audioRequest.playbackSpeed,
            commonPlaybackEventSink,
            pausedPlaybackEvent
          )
        }
      }
      emit(event.audioBarEvent)
    }

  private val pausedPlaybackEvent =
    { event: AudioBarScreen.AudioBarUiEvent.PausedPlaybackEvent -> emit(event.audioBarEvent) }
  private val stoppedEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.StoppedPlaybackEvent -> emit(event.audioBarEvent) }
  private val commonRecordingEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.CommonRecordingEvent -> emit(event.audioBarEvent) }
  private val recitationListeningEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.RecitationListeningEvent -> emit(event.audioBarEvent) }
  private val recitationPlayingEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.RecitationPlayingEvent -> emit(event.audioBarEvent) }
  private val recitationStoppedEventSink =
    { event: AudioBarScreen.AudioBarUiEvent.RecitationStoppedEvent -> emit(event.audioBarEvent) }
}
