package com.quran.mobile.linebyline.data.dao

import com.quran.mobile.linebyline.data.Sura_headers

data class SuraHeader(
  val sura: Int,
  val page: Int,
  val lineId: Int,
  val centerX: Float,
  val centerY: Float
)

fun Sura_headers.asSuraHeader() =
  SuraHeader(sura.toInt(), page.toInt(), line.toInt(), center_x.toFloat(), center_y.toFloat())
