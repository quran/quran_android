package com.quran.mobile.feature.voicesearch.di

import com.quran.data.di.AppScope
import com.quran.mobile.di.InlineVoiceSearchController
import com.quran.mobile.di.InlineVoiceSearchState
import com.quran.mobile.feature.voicesearch.asr.AsrEngine
import com.quran.mobile.feature.voicesearch.asr.AsrModelManager
import com.quran.mobile.feature.voicesearch.asr.AudioRecorder
import com.quran.mobile.feature.voicesearch.asr.RecordingState
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class)
class InlineVoiceSearchControllerImpl @Inject constructor(
  private val asrEngine: AsrEngine,
  private val asrModelManager: AsrModelManager,
  private val preferencesProvider: VoiceSearchPreferencesProvider
) : InlineVoiceSearchController {

  private val _state = MutableStateFlow<InlineVoiceSearchState>(InlineVoiceSearchState.Idle)
  override val state: StateFlow<InlineVoiceSearchState> = _state.asStateFlow()

  override val isEnabled: Boolean
    get() = preferencesProvider.isVoiceSearchEnabled()

  private var scope: CoroutineScope? = null
  private var recorder: AudioRecorder? = null
  private var recordingJob: Job? = null
  private var periodicTranscriptionJob: Job? = null
  private var transcriptionInProgress = false
  private var adaptiveIntervalMs = INITIAL_DELAY_MS

  override fun startRecording() {
    if (_state.value is InlineVoiceSearchState.Recording) return

    asrModelManager.checkModelAvailability()
    if (!asrModelManager.isModelReady()) {
      _state.value = InlineVoiceSearchState.ModelNotReady
      return
    }

    val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    scope = newScope
    val newRecorder = AudioRecorder()
    recorder = newRecorder
    adaptiveIntervalMs = INITIAL_DELAY_MS

    _state.value = InlineVoiceSearchState.Recording(amplitude = 0f, partialText = "")

    // Collect recording state (amplitude updates)
    recordingJob = newScope.launch {
      newRecorder.startRecording().collect { recordingState ->
        when (recordingState) {
          is RecordingState.Recording -> {
            val current = _state.value
            val partialText = if (current is InlineVoiceSearchState.Recording) {
              current.partialText
            } else ""
            _state.value = InlineVoiceSearchState.Recording(
              amplitude = recordingState.amplitude,
              partialText = partialText
            )
          }
          is RecordingState.Stopped -> {
            // Final transcription on all accumulated samples
            doFinalTranscription(newRecorder)
          }
          is RecordingState.Error -> {
            _state.value = InlineVoiceSearchState.Error(recordingState.message)
          }
        }
      }
    }

    // Periodic transcription coroutine
    periodicTranscriptionJob = newScope.launch {
      delay(INITIAL_DELAY_MS)
      while (true) {
        if (!transcriptionInProgress) {
          transcriptionInProgress = true
          try {
            val samples = newRecorder.getAccumulatedSamples()
            Timber.d("Periodic transcription: %d samples, max=%.4f", samples.size,
              samples.maxOrNull() ?: 0f)
            if (samples.isNotEmpty()) {
              val startTime = System.currentTimeMillis()
              val result = asrEngine.transcribeSamples(samples, AudioRecorder.SAMPLE_RATE)
              Timber.d("Periodic result: '%s' (normalized: '%s') in %dms",
                result.text, result.normalizedText, result.durationMs)
              val transcriptionDuration = System.currentTimeMillis() - startTime

              // Update adaptive interval
              adaptiveIntervalMs = maxOf(
                MIN_INTERVAL_MS,
                (transcriptionDuration * 1.5 + 1000).toLong()
              )

              val current = _state.value
              if (current is InlineVoiceSearchState.Recording) {
                _state.value = InlineVoiceSearchState.Recording(
                  amplitude = current.amplitude,
                  partialText = result.normalizedText
                )
              }
            }
          } catch (e: Exception) {
            Timber.e(e, "Periodic transcription failed")
          } finally {
            transcriptionInProgress = false
          }
        }
        delay(adaptiveIntervalMs)
      }
    }
  }

  override fun stopRecording() {
    periodicTranscriptionJob?.cancel()
    periodicTranscriptionJob = null
    recorder?.stopRecording()
    // recordingJob will continue until RecordingState.Stopped is emitted,
    // which triggers doFinalTranscription
  }

  private suspend fun doFinalTranscription(audioRecorder: AudioRecorder) {
    try {
      val samples = audioRecorder.getAccumulatedSamples()
      Timber.d("Final transcription: %d samples, max=%.4f", samples.size,
        samples.maxOrNull() ?: 0f)
      if (samples.isNotEmpty()) {
        val result = asrEngine.transcribeSamples(samples, AudioRecorder.SAMPLE_RATE)
        Timber.d("Final result: '%s' (normalized: '%s') in %dms",
          result.text, result.normalizedText, result.durationMs)
        _state.value = InlineVoiceSearchState.FinalResult(result.normalizedText)
      } else {
        _state.value = InlineVoiceSearchState.Idle
      }
    } catch (e: Exception) {
      Timber.e(e, "Final transcription failed")
      _state.value = InlineVoiceSearchState.Error(e.message ?: "Transcription failed")
    }
  }

  override fun reset() {
    periodicTranscriptionJob?.cancel()
    recordingJob?.cancel()
    scope?.cancel()
    scope = null
    recorder = null
    periodicTranscriptionJob = null
    recordingJob = null
    transcriptionInProgress = false
    _state.value = InlineVoiceSearchState.Idle
  }

  override fun release() {
    reset()
    asrEngine.release()
  }

  companion object {
    private const val INITIAL_DELAY_MS = 3000L
    private const val MIN_INTERVAL_MS = 3000L
  }
}
