package com.quran.page.common.data

import android.graphics.RectF

class AyahBounds(
  val line: Int,
  val position: Int,
  private val _bounds: RectF
) {
  constructor(
    line: Int,
    position: Int,
    minX: Int,
    minY: Int,
    maxX: Int,
    maxY: Int
  ) : this(line, position, RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat()))

  val bounds: RectF
    get() = RectF(_bounds)

  fun engulf(other: AyahBounds) {
    _bounds.union(other.bounds)
  }

  fun withBounds(bounds: RectF): AyahBounds = AyahBounds(line, position, bounds)
}
