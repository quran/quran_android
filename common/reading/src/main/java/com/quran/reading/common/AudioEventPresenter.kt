package com.quran.reading.common

import com.quran.data.model.SuraAyah
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEventPresenter @Inject constructor() {
  private val audioPlaybackAyahInternalFlow = MutableStateFlow<SuraAyah?>(null)

  val audioPlaybackAyahFlow: StateFlow<SuraAyah?> = audioPlaybackAyahInternalFlow.asStateFlow()

  fun onAyahPlayback(suraAyah: SuraAyah?) {
    if (audioPlaybackAyahInternalFlow.value != suraAyah) {
      audioPlaybackAyahInternalFlow.value = suraAyah
    }
  }

  fun currentPlaybackAyah(): SuraAyah? = audioPlaybackAyahFlow.value
}
