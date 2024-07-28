package com.quran.labs.androidquran.common.drawing

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

class AyahMarkerDrawable(@ColorInt val ringColor: Int,
                         @ColorInt val innerColor: Int) : Drawable() {
  private val ringPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = ringColor
    style = Paint.Style.STROKE
  }

  private val internalPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = innerColor
    style = Paint.Style.FILL
  }

  private var lastWidth: Int = 0

  override fun draw(canvas: Canvas) {
    val width = bounds.width()
    if (width != lastWidth) {
      lastWidth = width

      // width = 2 * 0.025f * image width
      val imageWidth = width / (2 * 0.025f)
      ringPaint.strokeWidth = 0.003f * imageWidth
    }

    val radius = width / 2.0f
    canvas.drawCircle(radius, radius, radius, internalPaint)
    canvas.drawCircle(radius, radius, radius, ringPaint)
  }

  override fun setAlpha(alpha: Int) {
    ringPaint.alpha = alpha
    internalPaint.alpha = alpha
  }

  override fun getOpacity() = PixelFormat.TRANSLUCENT

  override fun setColorFilter(colorFilter: ColorFilter?) {
    ringPaint.colorFilter = colorFilter
    internalPaint.colorFilter = colorFilter
  }
}
