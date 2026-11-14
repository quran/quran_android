package com.quran.labs.androidquran.extra.feature.linebyline.model

import androidx.annotation.ColorRes
import com.quran.data.model.highlight.HighlightColor

sealed interface HighlightType {
  data object Selection : HighlightType
  data object Audio : HighlightType
  data object AudioWord : HighlightType
  data object Bookmark : HighlightType

  data class Highlight(val color: HighlightColor) : HighlightType

  /** A mark of a recitation session (e.g. a mistake), drawn under the text in its own colors. */
  data class Recitation(@ColorRes val color: Int, @ColorRes val nightColor: Int) : HighlightType
}
