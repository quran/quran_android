package com.quran.reading.common

import com.quran.data.di.ActivityScope
import com.quran.data.model.selection.AyahSelection
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ActivityScope
class ReadingEventPresenter @Inject constructor() {
  private val clicksInternalFlow = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = DROP_OLDEST
  )
  private val ayahSelectionInternalFlow = MutableStateFlow<AyahSelection>(AyahSelection.None)

  val clicksFlow: Flow<Unit> = clicksInternalFlow.asSharedFlow()
  val ayahSelectionFlow: StateFlow<AyahSelection> = ayahSelectionInternalFlow.asStateFlow()

  fun onClick() {
    clicksInternalFlow.tryEmit(Unit)
  }

  fun currentAyahSelection(): AyahSelection = ayahSelectionFlow.value

  fun onAyahSelection(selection: AyahSelection) {
    if (ayahSelectionInternalFlow.value != selection) {
      ayahSelectionInternalFlow.value = selection
    }
  }
}
