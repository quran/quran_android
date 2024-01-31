package com.quran.mobile.feature.audiobar.presenter

import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.audiobar.state.AudioBarEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject

@ActivityScope
class AudioBarEventRepository @Inject constructor() {
  private val internalAudioBarEventFlow = MutableSharedFlow<AudioBarEvent>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val audioBarEventFlow: Flow<AudioBarEvent> = internalAudioBarEventFlow

  internal fun onAudioBarEvent(event: AudioBarEvent) {
    internalAudioBarEventFlow.tryEmit(event)
  }
}
