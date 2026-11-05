package com.quran.page.common.data

import com.quran.page.common.data.coordinates.PageGlyphsCoords

data class AyahCoordinates @JvmOverloads constructor(
  val page: Int,
  val ayahCoordinates: Map<String, List<AyahBounds>>,
  val glyphCoordinates: PageGlyphsCoords?,
  // one bounds per line of each ayah, aligned with the line
  val ayahLineCoordinates: Map<String, List<AyahBounds>> = emptyMap(),
  val underlineRaiseRatio: Float = 0f
)
