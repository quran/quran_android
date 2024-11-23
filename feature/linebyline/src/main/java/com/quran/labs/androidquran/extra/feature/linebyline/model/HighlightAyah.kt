package com.quran.labs.androidquran.extra.feature.linebyline.model

import com.quran.mobile.linebyline.data.dao.AyahHighlight

data class HighlightAyah(
  val highlightType: HighlightType,
  val ayahHighlights: List<AyahHighlight>,
  val shouldScroll: Boolean = false
)
