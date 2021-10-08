package com.quran.labs.androidquran.bridge

import com.quran.data.model.AyahSelection
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ReadingEventPresenterBridge constructor(
  readingEventPresenter: ReadingEventPresenter,
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

  fun dispose() {
    scope.cancel()
  }
}
