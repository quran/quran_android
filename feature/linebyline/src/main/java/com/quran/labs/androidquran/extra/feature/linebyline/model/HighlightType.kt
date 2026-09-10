package com.quran.labs.androidquran.extra.feature.linebyline.model

import com.quran.data.model.highlight.HighlightColor

sealed interface HighlightType {
  data object Selection : HighlightType
  data object Audio : HighlightType
  data object AudioWord : HighlightType
  data object Bookmark : HighlightType

  data class Highlight(val color: HighlightColor) : HighlightType
}
