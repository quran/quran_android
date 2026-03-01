package com.quran.mobile.feature.voicesearch

import com.quran.mobile.feature.voicesearch.asr.ModelState
import com.quran.mobile.voicesearch.VerseMatch

data class VoiceSearchState(
  val screenState: ScreenState = ScreenState.Idle,
  val modelState: ModelState = ModelState.NotDownloaded,
  val transcribedText: String = "",
  val verseMatches: List<VerseMatch> = emptyList(),
  val amplitude: Float = 0f,
  val errorMessage: String? = null
)

enum class ScreenState {
  Idle,
  ModelDownloading,
  Ready,
  Recording,
  Transcribing,
  Results
}
