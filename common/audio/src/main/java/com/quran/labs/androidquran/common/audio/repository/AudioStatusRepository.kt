package com.quran.labs.androidquran.common.audio.repository

import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioStatusRepository @Inject constructor() {
  private val audioPlaybackInternalFlow = MutableStateFlow<AudioStatus>(AudioStatus.Stopped)

  val audioPlaybackFlow = audioPlaybackInternalFlow.asStateFlow()

  fun updateAyahPlayback(audioStatus: AudioStatus) {
    audioPlaybackInternalFlow.value = audioStatus
  }
}

