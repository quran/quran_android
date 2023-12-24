package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import com.quran.labs.androidquran.ui.util.TypefaceManager

class UthmaniSpan(context: Context) : MetricAffectingSpan() {

  private val typeFace = TypefaceManager.getUthmaniTypeface(context)

  override fun updateDrawState(ds: TextPaint) {
    ds.typeface = typeFace
  }

  override fun updateMeasureState(paint: TextPaint) {
    paint.typeface = typeFace
  }
}
