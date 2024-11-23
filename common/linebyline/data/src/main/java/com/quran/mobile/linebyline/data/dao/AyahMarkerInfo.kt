package com.quran.mobile.linebyline.data.dao

import com.quran.mobile.linebyline.data.Ayah_markers

data class AyahMarkerInfo(
  val ayahId: Int,
  val page: Int,
  val sura: Int,
  val ayah: Int,
  val lineId: Int,
  val codePoint: String,
  val centerX: Float,
  val centerY: Float
)

fun Ayah_markers.asAyahMarker() =
  AyahMarkerInfo(
    ayah_id.toInt(),
    page.toInt(),
    sura.toInt(),
    ayah.toInt(),
    line.toInt(),
    code_point,
    center_x.toFloat(),
    center_y.toFloat()
  )
