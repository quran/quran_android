package com.quran.mobile.feature.voicesearch

import com.quran.mobile.feature.voicesearch.asr.AsrEngine
import com.quran.mobile.feature.voicesearch.asr.AsrModelManager
import com.quran.mobile.feature.voicesearch.asr.AudioRecorder
import com.quran.mobile.feature.voicesearch.asr.ModelState
import com.quran.mobile.feature.voicesearch.asr.RecordingState
import com.quran.mobile.voicesearch.QuranVerseMatcher
import com.quran.mobile.voicesearch.QuranVerseProvider
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface NavigationResult {
  data class VerseSelected(val sura: Int, val ayah: Int) : NavigationResult
  data class TextSearch(val text: String) : NavigationResult
}

class VoiceSearchPresenter @Inject constructor(
  private val asrEngine: AsrEngine,
  private val modelManager: AsrModelManager,
  private val verseProvider: QuranVerseProvider
) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private var audioRecorder: AudioRecorder? = null

  private val _state = MutableStateFlow(VoiceSearchState())
  val state: StateFlow<VoiceSearchState> = _state.asStateFlow()

  private val _navigationEvents = MutableSharedFlow<NavigationResult>()
  val navigationEvents: SharedFlow<NavigationResult> = _navigationEvents.asSharedFlow()

  private var verseMatcher: QuranVerseMatcher? = null

  fun initialize() {
    modelManager.checkModelAvailability()
    scope.launch {
      modelManager.modelState.collect { modelState ->
        val screenState = when (modelState) {
          is ModelState.Ready -> ScreenState.Ready
          is ModelState.Downloading -> ScreenState.ModelDownloading
          is ModelState.NotDownloaded -> ScreenState.Idle
          is ModelState.Error -> ScreenState.Idle
        }
        _state.update { it.copy(modelState = modelState, screenState = screenState) }
      }
    }

    // Pre-load verse data in background
    scope.launch(Dispatchers.Default) {
      val verses = verseProvider.getAllVerses()
      if (verses.isNotEmpty()) {
        verseMatcher = QuranVerseMatcher(verses)
      }
    }
  }

  fun onEvent(event: VoiceSearchEvent) {
    when (event) {
      is VoiceSearchEvent.DownloadModel -> downloadModel()
      is VoiceSearchEvent.CancelDownload -> cancelDownload()
      is VoiceSearchEvent.StartRecording -> startRecording()
      is VoiceSearchEvent.StopRecording -> stopRecording()
      is VoiceSearchEvent.DismissError -> dismissError()
      is VoiceSearchEvent.Reset -> reset()
      is VoiceSearchEvent.SelectVerse -> selectVerse(event.sura, event.ayah)
      is VoiceSearchEvent.SearchText -> searchText()
    }
  }

  private fun downloadModel() {
    scope.launch {
      modelManager.downloadModel()
    }
  }

  private fun cancelDownload() {
    modelManager.cancelDownload()
  }

  private fun startRecording() {
    if (_state.value.screenState == ScreenState.Recording) return

    val recorder = AudioRecorder()
    audioRecorder = recorder
    _state.update { it.copy(screenState = ScreenState.Recording, amplitude = 0f) }

    scope.launch {
      recorder.startRecording().collect { recordingState ->
        when (recordingState) {
          is RecordingState.Recording -> {
            _state.update { it.copy(amplitude = recordingState.amplitude) }
          }
          is RecordingState.Stopped -> {
            transcribe(recorder)
          }
          is RecordingState.Error -> {
            _state.update {
              it.copy(
                screenState = ScreenState.Ready,
                errorMessage = recordingState.message
              )
            }
          }
        }
      }
    }
  }

  private fun stopRecording() {
    audioRecorder?.stopRecording()
  }

  private suspend fun transcribe(recorder: AudioRecorder) {
    _state.update { it.copy(screenState = ScreenState.Transcribing) }

    try {
      val samples = recorder.getAccumulatedSamples()
      val result = asrEngine.transcribeSamples(samples, AudioRecorder.SAMPLE_RATE)

      if (result.text.isBlank()) {
        _state.update {
          it.copy(
            screenState = ScreenState.Ready,
            errorMessage = "No speech detected. Please try again."
          )
        }
        return
      }

      // Match against Quran verses
      val matches = verseMatcher?.match(result.text) ?: emptyList()

      _state.update {
        it.copy(
          screenState = ScreenState.Results,
          transcribedText = result.text,
          verseMatches = matches
        )
      }
    } catch (e: Exception) {
      Timber.e(e, "Transcription failed")
      _state.update {
        it.copy(
          screenState = ScreenState.Ready,
          errorMessage = "Recognition failed: ${e.message}"
        )
      }
    }
  }

  private fun dismissError() {
    _state.update { it.copy(errorMessage = null) }
  }

  private fun reset() {
    _state.update {
      VoiceSearchState(
        screenState = if (modelManager.isModelReady()) ScreenState.Ready else ScreenState.Idle,
        modelState = modelManager.modelState.value
      )
    }
  }

  private fun selectVerse(sura: Int, ayah: Int) {
    scope.launch { _navigationEvents.emit(NavigationResult.VerseSelected(sura, ayah)) }
  }

  private fun searchText() {
    scope.launch { _navigationEvents.emit(NavigationResult.TextSearch(_state.value.transcribedText)) }
  }

  fun release() {
    scope.cancel()
    audioRecorder?.stopRecording()
  }
}
