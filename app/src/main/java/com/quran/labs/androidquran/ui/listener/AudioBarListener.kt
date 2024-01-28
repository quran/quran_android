package com.quran.labs.androidquran.ui.listener


interface AudioBarListener {
  fun onPlayPressed()
  fun onContinuePlaybackPressed()
  fun onPausePressed()
  fun onNextPressed()
  fun onPreviousPressed()
  fun onStopPressed()
  fun setPlaybackSpeed(speed: Float)
  fun onCancelPressed(stopDownload: Boolean)
  fun setRepeatCount(repeatCount: Int)
  fun onAcceptPressed()
  fun onAudioSettingsPressed()
  fun onShowQariList()
}

