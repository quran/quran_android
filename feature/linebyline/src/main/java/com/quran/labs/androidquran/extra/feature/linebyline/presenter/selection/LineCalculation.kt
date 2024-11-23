package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import kotlin.math.floor
import kotlin.math.roundToInt

class LineCalculation(
  val width: Int,
  private val height: Int,
  lineHeightWidthRatio: Float = 174f / 1080f,
  private val lines: Int = 15,
  resizeLinesToFit: Boolean = false
) {
  private val lineHeight: Int
  private val positions: IntArray

  init {
    lineHeight = if (resizeLinesToFit) {
      (1f * height / lines).toInt()
    } else {
      (width * lineHeightWidthRatio).toInt()
    }

    val lastLineIndex = lines - 1
    positions = IntArray(lines) {
      floor((1f * height - lineHeight) / lastLineIndex * it).roundToInt()
    }
  }

  fun lineIndexForY(y: Float): Int {
    return positions.indexOfFirst { y >= it && y <= (it + lineHeight) }
  }

  fun lineRangeFor(lineIndex: Int): Pair<Int, Int> {
    val lineHeightWithoutOverlap = (height - lineHeight) / (lines - 1)
    val offset = (lineHeight - lineHeightWithoutOverlap) / 2
    return (positions[lineIndex] + offset) to
        (positions[lineIndex] + offset + lineHeightWithoutOverlap)
  }

  internal fun matches(width: Int, height: Int): Boolean {
    return this.width == width && this.height == height
  }
}

fun LineCalculation?.from(width: Int, height: Int) =
  if (this?.matches(width, height) == true) {
    this
  } else {
    LineCalculation(width, height)
  }

