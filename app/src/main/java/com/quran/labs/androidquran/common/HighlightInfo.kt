package com.quran.labs.androidquran.common

import com.quran.labs.androidquran.ui.helpers.HighlightType

class HighlightInfo(
  val sura: Int,
  val ayah: Int,
  val word: Int,
  val highlightType: HighlightType,
  val scrollToAyah: Boolean
)
