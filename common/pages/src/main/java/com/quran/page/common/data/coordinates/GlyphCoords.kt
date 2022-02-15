package com.quran.page.common.data.coordinates

import android.graphics.RectF
import com.quran.data.model.AyahGlyph

data class GlyphCoords(
  val glyph: AyahGlyph,
  val line: Int,
  val bounds: RectF
)
