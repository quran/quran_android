package com.quran.labs.androidquran.dao.bookmark

import com.quran.data.model.highlight.HighlightColor

sealed interface AyahMark {
  data object Bookmark : AyahMark
  data object Unhighlighted : AyahMark
  data class Highlighted(val color: HighlightColor) : AyahMark
}
