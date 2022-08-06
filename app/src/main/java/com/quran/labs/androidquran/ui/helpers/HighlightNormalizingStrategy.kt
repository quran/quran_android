package com.quran.labs.androidquran.ui.helpers

import android.graphics.RectF
import com.quran.page.common.data.AyahBounds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

abstract class HighlightNormalizingStrategy {
  /*
  The parameters are passed by reference, they will be modified
   */
  abstract fun normalize(start: MutableList<AyahBounds>, end: MutableList<AyahBounds>)
  abstract fun isNormalized(start: List<AyahBounds>, end: List<AyahBounds>): Boolean
  fun apply(start: MutableList<AyahBounds>, end: MutableList<AyahBounds>) {
    if (isNormalized(start, end)) return
    normalize(start, end)
  }
}

open class NormalizeToMaxAyahBoundsWithDivisionStrategy : HighlightNormalizingStrategy() {

  /*
  Say we are going from a list of x AyahBounds to a list of y AyahBounds
  Then normalizing algorithm is as follows:

  1. a = min(x,y); b = max(x, y)
  2. diff = b.length - a.length
  3. split a[-1] into (diff + 1) parts equally
  4. animate x[i] to y[i] for i in 0 to b.length-1
   */

  override fun normalize(start: MutableList<AyahBounds>, end: MutableList<AyahBounds>) {
    val startSize = start.size
    val endSize = end.size
    val minSize = min(startSize, endSize)
    val maxSize = max(startSize, endSize)
    val minList = if (startSize < endSize) start else end
    val diff = maxSize - minSize

    val rectToBeDivided = minList[minSize - 1].bounds
    val originalLeft = rectToBeDivided.left
    val originalRight = rectToBeDivided.right
    val originalTop = rectToBeDivided.top
    val originalBottom = rectToBeDivided.bottom
    minList.removeAt(minSize - 1)
    val part = (originalRight - originalLeft) / (diff + 1)

    for (i in 0 until diff + 1) {
      val left = originalLeft + part * i
      val right = left + part
      val rect = RectF(left, originalTop, right, originalBottom)
      val ayahBounds = AyahBounds(0, 0, rect)
      minList.add(ayahBounds)
    }
  }

  override fun isNormalized(start: List<AyahBounds>, end: List<AyahBounds>): Boolean {
    return start.size == end.size
  }
}

class NormalizeToMinAyahBoundsWithGrowingDivisionStrategy : NormalizeToMaxAyahBoundsWithDivisionStrategy() {

  /*
  Say we are going from a list of x AyahBounds to a list of y AyahBounds
  Then normalizing algorithm is as follows:

  1. diff = max(x, y).length - min(x,y).length
  2. if x < y then, split x[-1] into (diff + 1) parts equally (Growing: use division strategy)
  3. else delete x[0..diff-1] (Shrinking: use deletion strategy)
  4. animate x[i] to y[i] for i in 0 to x.length-1
   */

  override fun normalize(start: MutableList<AyahBounds>, end: MutableList<AyahBounds>) {
    val startSize = start.size
    val endSize = end.size

    if (startSize >= endSize) {
      val diff = abs(startSize - endSize)
      val toBeDeleted = start.subList(0, diff)
      toBeDeleted.clear()
    } else {
      super.normalize(start, end)
    }
  }
}
