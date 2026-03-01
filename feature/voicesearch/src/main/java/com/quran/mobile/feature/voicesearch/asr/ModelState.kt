package com.quran.mobile.feature.voicesearch.asr

sealed interface ModelState {
  data object NotDownloaded : ModelState
  data class Downloading(val progress: Float) : ModelState
  data object Ready : ModelState
  data class Error(val message: String) : ModelState
}
