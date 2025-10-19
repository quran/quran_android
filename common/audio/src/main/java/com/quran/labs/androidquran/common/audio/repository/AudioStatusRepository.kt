package com.quran.labs.androidquran.common.audio.repository

import com.quran.data.di.AppScope
import com.quran.labs.androidquran.common.audio.model.playback.AudioStatus
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
class AudioStatusRepository @Inject constructor() {
  private val audioPlaybackInternalFlow = MutableStateFlow<AudioStatus>(AudioStatus.Stopped)

  val audioPlaybackFlow = audioPlaybackInternalFlow.asStateFlow()

  fun updateAyahPlayback(audioStatus: AudioStatus) {
    audioPlaybackInternalFlow.value = audioStatus
  }
}

