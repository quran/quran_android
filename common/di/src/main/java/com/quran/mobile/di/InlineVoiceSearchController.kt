package com.quran.mobile.di

import kotlinx.coroutines.flow.StateFlow

sealed interface InlineVoiceSearchState {
  data object Idle : InlineVoiceSearchState
  data object ModelNotReady : InlineVoiceSearchState
  data class Recording(val amplitude: Float, val partialText: String) : InlineVoiceSearchState
  data class FinalResult(val text: String) : InlineVoiceSearchState
  data class Error(val message: String) : InlineVoiceSearchState
}

interface InlineVoiceSearchController {
  val isEnabled: Boolean
  val state: StateFlow<InlineVoiceSearchState>
  fun startRecording()
  fun stopRecording()
  fun reset()
  fun release()
}
