package com.quran.data.model.selection

import com.quran.data.model.selection.SelectedAyahPlacementType.BOTTOM

data class SelectedAyahPosition(
  val x: Float = 0f,
  val y: Float = 0f,
  val xScroll: Float = 0f,
  val yScroll: Float = 0f,
  val pipOffset: Float = 0f,
  val pipPosition: SelectedAyahPlacementType = BOTTOM
) {
  fun withX(x: Float) = copy(x = x)
  fun withY(y: Float) = copy(y = y)
  fun withXScroll(xScroll: Float) = copy(xScroll = xScroll)
  fun withYScroll(yScroll: Float) = copy(yScroll = yScroll)
}
