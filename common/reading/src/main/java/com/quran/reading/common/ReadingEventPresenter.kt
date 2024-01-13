package com.quran.reading.common

import com.quran.data.core.QuranInfo
import com.quran.data.di.ActivityScope
import com.quran.data.model.QuranRef.QuranId
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.endSuraAyah
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ActivityScope
class ReadingEventPresenter @Inject constructor(private val quranInfo: QuranInfo) {
  private val clicksInternalFlow = MutableSharedFlow<Unit>(
    replay = 0,
    extraBufferCapacity = 1,
    onBufferOverflow = DROP_OLDEST
  )
  private val quranClickInternalFlow = MutableSharedFlow<QuranId>(
    replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)
  private val detailsPanelInternalFlow = MutableStateFlow(false)
  private val ayahSelectionInternalFlow = MutableStateFlow<AyahSelection>(AyahSelection.None)

  val clicksFlow: Flow<Unit> = clicksInternalFlow.asSharedFlow()
  val quranClicksFlow: Flow<QuranId> = quranClickInternalFlow.asSharedFlow()
  val detailsPanelFlow: Flow<Boolean> = detailsPanelInternalFlow.asSharedFlow()
  val ayahSelectionFlow: StateFlow<AyahSelection> = ayahSelectionInternalFlow.asStateFlow()

  fun onClick() {
    clicksInternalFlow.tryEmit(Unit)
  }

  fun onClick(portion: QuranId) {
    quranClickInternalFlow.tryEmit(portion)
  }

  fun currentAyahSelection(): AyahSelection = ayahSelectionFlow.value

  fun onAyahSelection(selection: AyahSelection) {
    if (ayahSelectionInternalFlow.value != selection) {
      ayahSelectionInternalFlow.value = selection
    }
  }

  fun selectNextAyah() {
    val currentSelection = ayahSelectionFlow.value
    val currentEndAyah = currentSelection.endSuraAyah()
    if (currentEndAyah != null) {
      val ayat: Int = quranInfo.getNumberOfAyahs(currentEndAyah.sura)
      val updatedAyah = when {
        currentEndAyah.ayah + 1 <= ayat -> {
          SuraAyah(currentEndAyah.sura, currentEndAyah.ayah + 1)
        }
        currentEndAyah.sura < 114 -> {
          SuraAyah(currentEndAyah.sura + 1, 1)
        }
        else -> {
          null
        }
      }

      if (updatedAyah != null) {
        onAyahSelection(AyahSelection.Ayah(updatedAyah, SelectionIndicator.None))
      }
    }
  }

  fun selectPreviousAyah() {
    val currentSelection = ayahSelectionFlow.value
    val currentEndAyah = currentSelection.endSuraAyah()
    if (currentEndAyah != null) {
      val updatedAyah = when {
        currentEndAyah.ayah > 1 -> {
          SuraAyah(currentEndAyah.sura, currentEndAyah.ayah - 1)
        }
        currentEndAyah.sura > 1 -> {
          SuraAyah(currentEndAyah.sura - 1, quranInfo.getNumberOfAyahs(currentEndAyah.sura - 1))
        }
        else -> {
          null
        }
      }

      if (updatedAyah != null) {
        onAyahSelection(AyahSelection.Ayah(updatedAyah, SelectionIndicator.None))
      }
    }
  }

  fun onPanelOpened() {
    detailsPanelInternalFlow.value = true
  }

  fun onPanelClosed() {
    detailsPanelInternalFlow.value = false
  }
}
