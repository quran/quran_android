package com.quran.labs.androidquran.hybrid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.quran.labs.androidquran.util.QuranUtils
import kotlin.math.min

/**
 * Pure Canvas View rendering the iconic 8-point rotated-square Rub' al-Hizb medallion
 * with localized Arabic-Indic numeral inside using Color 2 (`editorialAccent`) tokens.
 */
class RubElHizbView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  private var number: Int = 0
  private var formattedNumber: String = ""
  @ColorInt private var medallionFillColor: Int = ColorUtils.setAlphaComponent(0xFF9B6D22.toInt(), 33)
  @ColorInt private var medallionBorderColor: Int = 0xFF9B6D22.toInt()
  @ColorInt private var numeralColor: Int = 0xFF9B6D22.toInt()
  private var preferredSizePx: Int = HybridViewTokens.dp(context, 40f)

  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = medallionFillColor
  }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = HybridViewTokens.dpF(context, 1.35f)
    color = medallionBorderColor
  }
  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = BOLD_TYPEFACE
    color = numeralColor
  }

  private val squareRect = RectF()

  internal val medallionFillColorForTest: Int get() = medallionFillColor
  internal val medallionBorderColorForTest: Int get() = medallionBorderColor
  internal val numeralColorForTest: Int get() = numeralColor
  internal val strokeWidthPxForTest: Float get() = strokePaint.strokeWidth

  fun setSizeDp(sizeDp: Float) {
    val newPx = HybridViewTokens.dp(context, sizeDp)
    if (preferredSizePx != newPx) {
      preferredSizePx = newPx
      requestLayout()
    }
  }

  fun setNumber(value: Int) {
    if (number != value) {
      number = value
      formattedNumber = QuranUtils.getLocalizedNumber(value)
      invalidate()
    }
  }

  fun applyPalette(palette: HybridViewPalette) {
    medallionFillColor = palette.medallionFill
    medallionBorderColor = palette.medallionBorder
    numeralColor = palette.editorialAccent
    fillPaint.color = medallionFillColor
    strokePaint.color = medallionBorderColor
    textPaint.color = numeralColor
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val w = resolveSize(preferredSizePx, widthMeasureSpec)
    val h = resolveSize(preferredSizePx, heightMeasureSpec)
    val side = min(w, h)
    setMeasuredDimension(side, side)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val side = min(width, height).toFloat()
    if (side <= 0f) return

    val cx = width * 0.5f
    val cy = height * 0.5f
    val squareSide = side * (26f / 40f)
    val halfSquare = squareSide * 0.5f
    val cornerRadius = side * (4f / 40f)

    squareRect.set(-halfSquare, -halfSquare, halfSquare, halfSquare)

    // Square 1 (0 degrees): soft Color-2 fill + 1.35dp Color-2 stroke
    canvas.save()
    canvas.translate(cx, cy)
    canvas.drawRoundRect(squareRect, cornerRadius, cornerRadius, fillPaint)
    canvas.drawRoundRect(squareRect, cornerRadius, cornerRadius, strokePaint)
    canvas.restore()

    // Square 2 (45 degrees): 1.35dp Color-2 stroke only (avoids double-alpha interior stacking)
    canvas.save()
    canvas.translate(cx, cy)
    canvas.rotate(45f)
    canvas.drawRoundRect(squareRect, cornerRadius, cornerRadius, strokePaint)
    canvas.restore()

    // Centered localized numeral in Color 2 (editorialAccent)
    if (formattedNumber.isNotEmpty()) {
      val digitCount = formattedNumber.length
      val sizeFactor = when {
        digitCount >= 3 -> 0.27f
        digitCount == 2 -> 0.30f
        else -> 0.33f
      }
      textPaint.textSize = side * sizeFactor
      val fm = textPaint.fontMetrics
      val baseline = cy - (fm.ascent + fm.descent) * 0.5f
      canvas.drawText(formattedNumber, cx, baseline, textPaint)
    }
  }

  private companion object {
    val BOLD_TYPEFACE: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  }
}
