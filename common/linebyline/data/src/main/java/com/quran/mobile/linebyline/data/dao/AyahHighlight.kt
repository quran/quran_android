package com.quran.mobile.linebyline.data.dao

import com.quran.mobile.linebyline.data.Ayah_highlights

data class AyahHighlight(
  val ayahId: Int,
  val page: Int,
  val sura: Int,
  val ayah: Int,
  val lineId: Int,
  val left: Float,
  val right: Float
)

fun Ayah_highlights.asAyahHighlight() =
  AyahHighlight(
    ayah_id.toInt(),
    page.toInt(),
    sura.toInt(),
    ayah.toInt(),
    line.toInt(),
    left.toFloat(),
    right.toFloat()
  )
