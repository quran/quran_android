package com.quran.data.model.selection

import com.quran.data.model.selection.SelectionIndicator.None
import com.quran.data.model.selection.SelectionIndicator.SelectedItemPosition
import com.quran.data.model.selection.SelectionIndicator.SelectedPointPosition

sealed class SelectionIndicator {
  object None : SelectionIndicator()
  object ScrollOnly : SelectionIndicator()
  data class SelectedPointPosition(
    val x: Float,
    val y: Float,
    val xScroll: Float = 0f,
    val yScroll: Float = 0f
  ): SelectionIndicator()
  data class SelectedItemPosition(
    val firstItem: SelectionRectangle,
    val lastItem: SelectionRectangle,
    val xScroll: Float = 0f,
    val yScroll: Float = 0f
  ): SelectionIndicator()
}

fun SelectionIndicator.withXScroll(xScroll: Float): SelectionIndicator {
  return when (this) {
    None, SelectionIndicator.ScrollOnly -> this
    is SelectedPointPosition -> this.copy(xScroll = xScroll)
    is SelectedItemPosition -> this.copy(xScroll = xScroll)
  }
}

fun SelectionIndicator.withYScroll(yScroll: Float): SelectionIndicator {
  return when (this) {
    None, SelectionIndicator.ScrollOnly -> this
    is SelectedPointPosition -> this.copy(yScroll = yScroll)
    is SelectedItemPosition -> this.copy(yScroll = yScroll)
  }
}
