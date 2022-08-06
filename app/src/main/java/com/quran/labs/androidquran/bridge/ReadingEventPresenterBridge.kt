package com.quran.labs.androidquran.bridge

import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.SelectionIndicator
import com.quran.data.model.selection.withSelectionIndicator
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

  private fun currentSelection(): AyahSelection = ayahSelectionFlow.value

  /**
   * Set the highlighted sura and ayah
   */
  fun setSelection(sura: Int, ayah: Int, scrollToAyah: Boolean) {
    val selectionIndicator = if (scrollToAyah) SelectionIndicator.ScrollOnly else SelectionIndicator.None
    val ayahSelection = AyahSelection.Ayah(SuraAyah(sura, ayah), selectionIndicator)
    readingEventPresenter.onAyahSelection(ayahSelection)
  }

  /**
   * Clear the selected ayah
   */
  fun clearSelectedAyah() {
    readingEventPresenter.onAyahSelection(AyahSelection.None)
  }

  /**
   * Set the selection indicator for the current selected ayah
   */
  fun withSelectionIndicator(selectionIndicator: SelectionIndicator) {
    readingEventPresenter.onAyahSelection(
      currentSelection().withSelectionIndicator(selectionIndicator)
    )
  }

  /**
   * Keep current ayah selected, but clear the toolbar for it
   */
  fun clearMenuForSelection() {
    val current = currentSelection()
    if (current != AyahSelection.None) {
      readingEventPresenter.onAyahSelection(current.withSelectionIndicator(SelectionIndicator.None))
    }
  }

  fun dispose() {
    scope.cancel()
  }
}
