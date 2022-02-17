package com.quran.data.model.highlight

import androidx.annotation.ColorRes

data class HighlightType(
  private val id: Long,
  @ColorRes val colorResId: Int,
  val mode: Mode,
  val isSingle: Boolean = false,
  val isTransitionAnimated: Boolean = false,
) : Comparable<HighlightType> {

  enum class Mode {
    HIGHLIGHT,  // Highlights the text of the ayah (rectangular overlay on the text)
    BACKGROUND, // Applies a background color to the entire line (full height/width, even ayahs that are centered like first 2 pages)
    UNDERLINE,  // Draw an underline below the text of the ayah
    COLOR,      // Change the text color of the ayah/word (apply a color filter)
    HIDE        // Hide the ayah/word (i.e. won't be rendered)
  }

  override fun compareTo(other: HighlightType): Int {
    return id.compareTo(other.id)
  }
}
