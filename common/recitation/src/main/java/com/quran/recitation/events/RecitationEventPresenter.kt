package com.quran.recitation.events

import com.quran.data.model.SuraAyah
import com.quran.recitation.common.RecitationSession
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecitationEventPresenter @Inject constructor() {
  private val _recitationChangeFlow = MutableSharedFlow<SuraAyah>(
      replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val _listeningStateFlow = MutableStateFlow<Boolean>(false)
  private val _recitationSessionFlow = MutableStateFlow<RecitationSession?>(null)
  private val _recitationSelectionFlow = MutableStateFlow<RecitationSelection>(RecitationSelection.None)

  val recitationChangeFlow: Flow<SuraAyah> = _recitationChangeFlow.asSharedFlow()
  val listeningStateFlow: StateFlow<Boolean> = _listeningStateFlow.asStateFlow()
  val recitationSessionFlow: StateFlow<RecitationSession?> = _recitationSessionFlow.asStateFlow()
  val recitationSelectionFlow: StateFlow<RecitationSelection> = _recitationSelectionFlow.asStateFlow()

  fun onRecitationChange(ayah: SuraAyah) {
    _recitationChangeFlow.tryEmit(ayah)
  }

  fun isListening(): Boolean = listeningStateFlow.value
  fun recitationSession(): RecitationSession? = recitationSessionFlow.value
  fun hasRecitationSession(): Boolean = recitationSession() != null

  fun onListeningStateChange(isListening: Boolean) {
    _listeningStateFlow.value = isListening
  }

  fun onRecitationSessionChange(session: RecitationSession?) {
    // Whenever the session changes, also clear any selections
    if (session != _recitationSessionFlow.value) {
      _recitationSelectionFlow.value = RecitationSelection.None
      _recitationSessionFlow.value = session
    }
  }

  fun onRecitationSelection(selection: RecitationSelection) {
    _recitationSelectionFlow.tryEmit(selection)
  }

}
