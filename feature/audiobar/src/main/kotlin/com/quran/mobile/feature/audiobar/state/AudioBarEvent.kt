package com.quran.mobile.feature.audiobar.state

sealed class AudioBarEvent {
  data object ChangeQari : AudioBarEvent()
  data object Cancel : AudioBarEvent()
  data object Acknowledge : AudioBarEvent()

  // playback
  data object Play : AudioBarEvent()
  data object Pause : AudioBarEvent()
  data object Stop : AudioBarEvent()
  data object FastForward : AudioBarEvent()
  data object Rewind : AudioBarEvent()
  data object ShowSettings : AudioBarEvent()
  data class SetRepeat(val repeat: Int) : AudioBarEvent()
  data class SetSpeed(val speed: Float) : AudioBarEvent()

  // recitation
  data object Record : AudioBarEvent()
  data object Recitation : AudioBarEvent()
  data object RecitationLongPress : AudioBarEvent()
  data object Transcript : AudioBarEvent()
  data object HideVerses : AudioBarEvent()
  data object EndSession : AudioBarEvent()
  data object PlayRecitation : AudioBarEvent()
  data object PauseRecitation : AudioBarEvent()
}
