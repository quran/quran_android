package com.quran.recitation.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecitationPlaybackEventPresenter @Inject constructor() {

  private val _loadedRecitationFlow = MutableStateFlow<String?>(null)
  private val _playingStateFlow = MutableStateFlow<Boolean>(false)
  private val _playbackPositionFlow = MutableStateFlow<Int>(0)
  val loadedRecitationFlow: StateFlow<String?> = _loadedRecitationFlow.asStateFlow()
  val playingStateFlow: StateFlow<Boolean> = _playingStateFlow.asStateFlow()
  val playbackPositionFlow: StateFlow<Int> = _playbackPositionFlow.asStateFlow()

  fun isPlaying(): Boolean = playingStateFlow.value

  fun onLoadedRecitationChange(loadedRecitation: String?) {
    _loadedRecitationFlow.value = loadedRecitation
  }

  fun onPlayingStateChange(playingState: Boolean) {
    _playingStateFlow.value = playingState
  }

  fun onPlaybackPositionChange(playbackPosition: Int) {
    _playbackPositionFlow.value = playbackPosition
  }

}
