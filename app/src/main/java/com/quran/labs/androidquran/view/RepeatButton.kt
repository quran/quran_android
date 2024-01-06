package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.quran.labs.androidquran.R

class RepeatButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

  private var text = ""
  private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
  private var canDraw = false
  private var viewWidth = 0
  private var viewHeight = 0
  private var textXPosition = 0
  private var textYPosition = 0
  private val textYPadding = resources.getDimensionPixelSize(R.dimen.repeat_text_y_padding)

  init {
    paint.color = Color.WHITE
    val resources = context.resources
    paint.textSize = resources.getDimensionPixelSize(R.dimen.repeat_superscript_text_size).toFloat()
  }

  fun setText(text: String) {
    this.text = text
    updateCoordinates()
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    viewWidth = measuredWidth
    viewHeight = measuredHeight
    updateCoordinates()
  }

  private fun updateCoordinates() {
    canDraw = false
    val drawable = drawable
    if (drawable != null) {
      val bounds = drawable.bounds
      if (bounds.width() > 0) {
        val x = viewWidth - (viewWidth - bounds.width()) / 2
        textXPosition = if (x + bounds.width() > viewWidth) {
          viewWidth - bounds.width()
        } else {
          x
        }
        textYPosition = textYPadding + (viewHeight - bounds.height()) / 2
        canDraw = true
      }
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val length = text.length
    if (canDraw && length > 0) {
      canvas.drawText(text, 0, length, textXPosition.toFloat(), textYPosition.toFloat(), paint)
    }
  }
}
