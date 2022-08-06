package com.quran.labs.androidquran.ui.helpers

import android.animation.TypeEvaluator
import android.graphics.RectF
import com.quran.page.common.data.AyahBounds

class HighlightAnimationTypeEvaluator(
  private val strategy: HighlightNormalizingStrategy
) : TypeEvaluator<MutableList<AyahBounds>> {

  override fun evaluate(
    fraction: Float,
    start: MutableList<AyahBounds>,
    end: MutableList<AyahBounds>
  ): MutableList<AyahBounds> {
    strategy.apply(start, end)

    val size = start.size

    // return a new result object to avoid data race with onAnimationUpdate
    val result: MutableList<AyahBounds> = ArrayList(size)

    for (i in 0 until size) {
      val startValue = start[i].bounds
      val endValue = end[i].bounds
      val left = startValue.left + (endValue.left - startValue.left) * fraction
      val top = startValue.top + (endValue.top - startValue.top) * fraction
      val right = startValue.right + (endValue.right - startValue.right) * fraction
      val bottom = startValue.bottom + (endValue.bottom - startValue.bottom) * fraction
      val intermediateBounds = AyahBounds(0, 0, RectF(left, top, right, bottom))
      result.add(intermediateBounds)
    }
    return result
  }
}
