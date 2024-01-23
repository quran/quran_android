package com.quran.mobile.feature.audiobar

import androidx.compose.runtime.Composable
import com.quran.labs.androidquran.common.audio.repository.AudioStatusRepository
import com.slack.circuit.runtime.presenter.Presenter

class AudioBarPresenter(
  private val audioStatusRepository: AudioStatusRepository
) : Presenter<AudioBarState> {

  @Composable
  override fun present(): AudioBarState {
    TODO("Not yet implemented")
  }
}
