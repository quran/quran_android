package com.quran.mobile.linebyline.data.dao

import com.quran.mobile.linebyline.data.Ayah_glyphs

data class WordHighlight(
  val id: Int,
  val page: Int,
  val line: Int,
  val sura: Int,
  val ayah: Int,
  val glyphPosition: Int,
  val left: Float,
  val right: Float
)

fun Ayah_glyphs.asWordHighlight(): WordHighlight =
  WordHighlight(
    id.toInt(),
    page.toInt(),
    line.toInt(),
    sura.toInt(),
    ayah.toInt(),
    glyph_position.toInt(),
    left.toFloat(),
    right.toFloat()
  )
