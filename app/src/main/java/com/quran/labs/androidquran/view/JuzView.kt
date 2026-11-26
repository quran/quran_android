package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.ThemeUtil

class JuzView(
  private val context: Context,
  type: Int,
  overlayText: String?
) : Drawable() {

  private var radius = 0f
  private var percentage: Int = 0
  private var overlayText: String? = null
  private var textOffset = 0f
  private val strokeWidthPx: Float
  private val arcInsetPx: Float

  private val circleRect = RectF()
  private val arcRect = RectF()
  private val circlePaint = Paint()
  private val circleStrokePaint = Paint()
  private var overlayTextPaint: TextPaint? = null
  private val circleBackgroundPaint = Paint()

  init {
    val resources = context.resources
    val density = resources.displayMetrics.density
    strokeWidthPx = density
    arcInsetPx = 2f * density

    val circleColor = ThemeUtil.resolveThemeColor(
      context,
      R.attr.quranAccent,
      ContextCompat.getColor(context, R.color.accent_color)
    )
    val circleBackground = ThemeUtil.resolveThemeColor(
      context,
      R.attr.quranSuraNumberBg,
      ContextCompat.getColor(context, R.color.accent_color_dark)
    )
    val circleStroke = ThemeUtil.resolveThemeColor(
      context,
      R.attr.quranSuraNumberStroke,
      circleColor
    )

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

    circleStrokePaint.apply {
      style = Paint.Style.STROKE
      strokeWidth = strokeWidthPx
      color = circleStroke
      isAntiAlias = true
    }

    update(type, overlayText)
  }

  fun update(type: Int, overlayText: String?) {
    this.overlayText = overlayText
    if (!overlayText.isNullOrEmpty() && overlayTextPaint == null) {
      val resources = context.resources
      val textPaintColor = ThemeUtil.resolveThemeColor(
        context,
        R.attr.quranOnAccent,
        ContextCompat.getColor(context, R.color.header_background)
      )
      val textPaintSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        13.5f,
        resources.displayMetrics
      )
      val textPaint = TextPaint().apply {
        isAntiAlias = true
        color = textPaintColor
        textSize = textPaintSize
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
      }
      val textHeight = textPaint.descent() - textPaint.ascent()
      textOffset = textHeight / 2f - textPaint.descent()
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
    radius = (right - left) / 2f
    val yOffset = (bottom - top - 2f * radius) / 2f
    val halfStroke = strokeWidthPx / 2f
    circleRect.set(
      left + halfStroke,
      top + yOffset + halfStroke,
      right - halfStroke,
      top + yOffset + 2f * radius - halfStroke
    )
    arcRect.set(
      left + arcInsetPx,
      top + yOffset + arcInsetPx,
      right - arcInsetPx,
      top + yOffset + 2f * radius - arcInsetPx
    )
  }

  override fun draw(canvas: Canvas) {
    val cx = circleRect.centerX()
    val cy = circleRect.centerY()
    val bgRadius = (radius - strokeWidthPx / 2f).coerceAtLeast(0f)
    canvas.drawCircle(cx, cy, bgRadius, circleBackgroundPaint)
    canvas.drawArc(arcRect, -90f, (3.6 * percentage).toFloat(), true, circlePaint)
    canvas.drawCircle(cx, cy, bgRadius, circleStrokePaint)
    overlayTextPaint?.let { textPaint ->
      val text = overlayText
      if (text != null) {
        canvas.drawText(text, cx, cy + textOffset, textPaint)
      }
    }
  }

  @Deprecated("Deprecated in Java")
  override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

  override fun setAlpha(alpha: Int) {
    circlePaint.alpha = alpha
    circleBackgroundPaint.alpha = alpha
    circleStrokePaint.alpha = alpha
    overlayTextPaint?.alpha = alpha
    invalidateSelf()
  }

  override fun setColorFilter(cf: ColorFilter?) {
    circlePaint.colorFilter = cf
    circleBackgroundPaint.colorFilter = cf
    circleStrokePaint.colorFilter = cf
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
