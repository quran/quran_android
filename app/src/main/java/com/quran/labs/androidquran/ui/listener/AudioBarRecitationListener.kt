package com.quran.labs.androidquran.ui.listener


interface AudioBarRecitationListener {
  fun onRecitationPressed()
  fun onRecitationLongPressed()
  fun onRecitationTranscriptPressed()
  fun onHideVersesPressed()
  fun onEndRecitationSessionPressed()
  fun onPlayRecitationPressed()
  fun onPauseRecitationPressed()
}
