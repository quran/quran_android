package com.quran.data.model.highlight

class HighlightInfo(
  val sura: Int,
  val ayah: Int,
  val word: Int,
  val highlightType: HighlightType,
  val scrollToAyah: Boolean
)
