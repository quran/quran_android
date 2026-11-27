package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import com.quran.labs.androidquran.R

class DividerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {
  private var dividerColor: Int = 0
  private var dividerHeight: Int = 0
  private val paint: Paint

  private var y: Int = 0
  private var highlightedColor: Int = 0
  private var isLineVisible: Boolean = true

  init {
    if (attrs != null) {
      val ta = context.obtainStyledAttributes(attrs, R.styleable.DividerView)
      try {
        dividerColor = ta.getColor(R.styleable.DividerView_dividerColor, dividerColor)
        dividerHeight = ta.getDimensionPixelSize(
          R.styleable.DividerView_dividerHeight, dividerHeight
        )
      } finally {
        ta.recycle()
      }
    }

    paint = Paint().apply {
      color = dividerColor
    }
  }

  fun toggleLine(showLine: Boolean) {
    if (isLineVisible != showLine) {
      isLineVisible = showLine
      invalidate()
    }
  }

  fun setDividerColor(color: Int) {
    if (dividerColor != color) {
      dividerColor = color
      paint.color = color
      invalidate()
    }
  }

  fun highlight(@ColorInt color: Int) {
    if (color != highlightedColor) {
      highlightedColor = color
      invalidate()
    }
  }

  fun unhighlight() {
    if (highlightedColor != 0) {
      highlightedColor = 0
      invalidate()
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    y = measuredHeight - dividerHeight
  }

  override fun onDraw(canvas: Canvas) {
    if (highlightedColor != 0) {
      canvas.drawColor(highlightedColor)
    }

    if (isLineVisible) {
      canvas.drawRect(0f, y.toFloat(), width.toFloat(), (y + dividerHeight).toFloat(), paint)
    }
  }
}
