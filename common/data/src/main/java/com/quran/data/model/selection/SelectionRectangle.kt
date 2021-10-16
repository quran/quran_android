package com.quran.data.model.selection

data class SelectionRectangle(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float
) {

  fun centerX() = left + ((right - left) / 2)
  fun centerY() = top + ((bottom - top) / 2)
}
