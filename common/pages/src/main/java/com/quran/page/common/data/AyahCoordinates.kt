package com.quran.page.common.data

import com.quran.page.common.data.coordinates.PageGlyphsCoords

data class AyahCoordinates @JvmOverloads constructor(
  val page: Int,
  val ayahCoordinates: Map<String, List<AyahBounds>>,
  val glyphCoordinates: PageGlyphsCoords?,
  // one bounds per line of each ayah, aligned with the line
  val ayahLineCoordinates: Map<String, List<AyahBounds>> = emptyMap(),
  val underlineRaiseRatio: Float = 0f,
  // word level glyphs derived from a GlyphTypeResolver when the database has no glyph types. Only
  // used for word keys, hiding, and word taps during a recitation session, so existing ayah
  // highlights and taps behave as before.
  val wordGlyphCoordinates: PageGlyphsCoords? = null
)
