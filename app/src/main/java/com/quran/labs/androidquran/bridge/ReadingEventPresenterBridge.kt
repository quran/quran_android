package com.quran.labs.androidquran.bridge

import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.SuraAyah
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ReadingEventPresenterBridge constructor(
  private val readingEventPresenter: ReadingEventPresenter,
  private val handleClick: (() -> Unit),
  private val handleSelection: ((AyahSelection) -> Unit)
) {

  private val scope = MainScope()
  private val ayahSelectionFlow = readingEventPresenter.ayahSelectionFlow

  init {
    readingEventPresenter.clicksFlow
      .onEach { handleClick() }
      .launchIn(scope)

    ayahSelectionFlow
      .onEach { handleSelection(it) }
      .launchIn(scope)
  }

  fun currentSelection(): AyahSelection = ayahSelectionFlow.value

  // set highlighted sura / ayah in onCreate/onNewIntent
  fun setSelection(sura: Int, ayah: Int) {
    val ayahSelection = AyahSelection.Ayah(SuraAyah(sura, ayah))
    readingEventPresenter.onAyahSelection(ayahSelection)
  }

  fun dispose() {
    scope.cancel()
  }
}
