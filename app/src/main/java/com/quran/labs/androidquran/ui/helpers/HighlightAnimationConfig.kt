package com.quran.labs.androidquran.ui.helpers

import android.animation.TimeInterpolator
import android.animation.TypeEvaluator
import android.view.animation.AccelerateDecelerateInterpolator

sealed class HighlightAnimationConfig(
  val duration: Int,
  val typeEvaluator: TypeEvaluator<*>?,
  val interpolator: TimeInterpolator?
) {
  object None : HighlightAnimationConfig(
    duration = 0,
    typeEvaluator = null,
    interpolator = null
  )

  object Audio : HighlightAnimationConfig(
    duration = 500,
    typeEvaluator = HighlightAnimationTypeEvaluator(NormalizeToMinAyahBoundsWithGrowingDivisionStrategy()),
    interpolator = AccelerateDecelerateInterpolator()
  )
}
