package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R

class JuzView(
  private val context: Context,
  type: Int,
  overlayText: String?
) : Drawable() {

  private var radius = 0
  private var percentage: Int = 0
  private var overlayText: String? = null
  private var textOffset = 0f

  private val circleRect = RectF()
  private val circlePaint = Paint()
  private var overlayTextPaint: TextPaint? = null
  private val circleBackgroundPaint = Paint()

  init {
    val circleColor = ContextCompat.getColor(context, R.color.accent_color)
    val circleBackground = ContextCompat.getColor(context, R.color.accent_color_dark)

    circlePaint.apply {
      style = Paint.Style.FILL
      color = circleColor
      isAntiAlias = true
    }

    circleBackgroundPaint.apply {
      style = Paint.Style.FILL
      color = circleBackground
      isAntiAlias = true
    }

    update(type, overlayText)
  }

  fun update(type: Int, overlayText: String?) {
    this.overlayText = overlayText
    if (!overlayText.isNullOrEmpty() && overlayTextPaint == null) {
      val resources = context.resources
      val textPaintColor = ContextCompat.getColor(context, R.color.header_background)
      val textPaintSize = resources.getDimensionPixelSize(R.dimen.juz_overlay_text_size)
      val textPaint = TextPaint().apply {
        isAntiAlias = true
        color = textPaintColor
        textSize = textPaintSize.toFloat()
        textAlign = Paint.Align.CENTER
      }
      val textHeight = textPaint.descent() - textPaint.ascent()
      textOffset = textHeight / 2 - textPaint.descent()
      overlayTextPaint = textPaint
    }

    this.percentage = when (type) {
      TYPE_JUZ -> 100
      TYPE_THREE_QUARTERS -> 75
      TYPE_HALF -> 50
      TYPE_QUARTER -> 25
      else -> 0
    }
    invalidateSelf()
  }

  override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
    super.setBounds(left, top, right, bottom)
    radius = (right - left) / 2
    val yOffset = (bottom - top - 2 * radius) / 2
    circleRect.set(
      left.toFloat(), (top + yOffset).toFloat(),
      right.toFloat(), (top + yOffset + 2 * radius).toFloat()
    )
  }

  override fun draw(canvas: Canvas) {
    canvas.drawCircle(
      circleRect.centerX(),
      circleRect.centerY(),
      radius.toFloat(),
      circleBackgroundPaint
    )
    canvas.drawArc(
      circleRect, -90f,
      (3.6 * percentage).toFloat(), true, circlePaint
    )
    overlayTextPaint?.let { textPaint ->
      val text = overlayText
      if (text != null) {
        canvas.drawText(
          text, circleRect.centerX(),
          circleRect.centerY() + textOffset, textPaint
        )
      }
    }
  }

  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

  override fun setAlpha(alpha: Int) {
    circlePaint.alpha = alpha
    circleBackgroundPaint.alpha = alpha
    overlayTextPaint?.alpha = alpha
    invalidateSelf()
  }

  override fun setColorFilter(cf: ColorFilter?) {
    circlePaint.colorFilter = cf
    circleBackgroundPaint.colorFilter = cf
    overlayTextPaint?.colorFilter = cf
    invalidateSelf()
  }

  companion object {
    const val TYPE_JUZ = 1
    const val TYPE_QUARTER = 2
    const val TYPE_HALF = 3
    const val TYPE_THREE_QUARTERS = 4
  }
}
