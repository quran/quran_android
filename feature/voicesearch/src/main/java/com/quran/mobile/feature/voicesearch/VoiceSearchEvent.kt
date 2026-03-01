package com.quran.mobile.feature.voicesearch

sealed interface VoiceSearchEvent {
  data object StartRecording : VoiceSearchEvent
  data object StopRecording : VoiceSearchEvent
  data object DownloadModel : VoiceSearchEvent
  data object CancelDownload : VoiceSearchEvent
  data object DismissError : VoiceSearchEvent
  data object Reset : VoiceSearchEvent
  data class SelectVerse(val sura: Int, val ayah: Int) : VoiceSearchEvent
  data object SearchText : VoiceSearchEvent
}
