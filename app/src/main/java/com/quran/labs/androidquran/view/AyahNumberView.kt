package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import com.quran.labs.androidquran.R

class AyahNumberView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  data class Indicator(
    @DrawableRes val icon: Int,
    @ColorInt val color: Int,
    val label: String,
    val isReadingBookmark: Boolean
  )

  private val density = resources.displayMetrics.density
  private val horizontalPadding = 12 * density
  private val gap = 8 * density
  private val indicatorGap = 12 * density
  private val labelGap = 4 * density
  private val iconSize = (16 * density).toInt()
  private val dividerWidth = density
  private val dividerHeight = 14 * density
  private val maxLabelWidth = 150 * density

  private var boxColor = 0
  private var nightBoxColor = 0
  private var boxHeight = 0
  private var isNightMode = false

  private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val dividerPaint = Paint()
  private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
  private val icons = mutableMapOf<Int, Drawable>()
  private val box = RectF()

  private var suraAyah = ""
  private var indicators: List<Indicator> = emptyList()
  private var labels: List<String?> = emptyList()

  init {
    context.withStyledAttributes(attrs, R.styleable.AyahNumberView) {
      setTextColor(getColor(R.styleable.AyahNumberView_android_textColor, 0))
      textPaint.textSize = getDimension(R.styleable.AyahNumberView_android_textSize, 0f)
      boxColor = getColor(R.styleable.AyahNumberView_backgroundColor, 0)
      nightBoxColor = getColor(R.styleable.AyahNumberView_nightBackgroundColor, 0)
      boxHeight = getDimensionPixelSize(R.styleable.AyahNumberView_verseBoxHeight, 0)
    }
    boxPaint.color = boxColor
  }

  val boxCenterX: Int
    get() = box.centerX().toInt()

  val boxBottomY: Int
    get() = box.bottom.toInt()

  fun setAyah(suraAyah: String, indicators: List<Indicator>) {
    if (suraAyah != this.suraAyah || indicators != this.indicators) {
      this.suraAyah = suraAyah
      this.indicators = indicators
      contentDescription = (listOf(suraAyah) + indicators.map { it.label }).joinToString()
      layoutBox()
      invalidate()
    }
  }

  fun setNightMode(isNightMode: Boolean) {
    if (this.isNightMode != isNightMode) {
      boxPaint.color = if (isNightMode) nightBoxColor else boxColor
      this.isNightMode = isNightMode
      invalidate()
    }
  }

  fun setTextColor(@ColorInt textColor: Int) {
    textPaint.color = textColor
    dividerPaint.color = ColorUtils.setAlphaComponent(textColor, 0x40)
    invalidate()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    layoutBox()
  }

  // drop reading bookmark names first, then every label, until the box fits
  private fun layoutBox() {
    val all = indicators.map {
      TextUtils.ellipsize(it.label, textPaint, maxLabelWidth, TextUtils.TruncateAt.END).toString()
    }
    val options = listOf(
      all,
      all.mapIndexed { i, label -> label.takeUnless { indicators[i].isReadingBookmark } },
      all.map { null }
    )
    val available = width - paddingStart - paddingEnd
    labels = options.firstOrNull { boxWidth(it) <= available } ?: options.last()

    val boxWidth = boxWidth(labels)
    val left = if (isRtl()) width - paddingStart - boxWidth else paddingStart.toFloat()
    val top = (height - boxHeight) / 2f
    box.set(left, top, left + boxWidth, top + boxHeight)
  }

  private fun boxWidth(labels: List<String?>): Float {
    var width = 2 * horizontalPadding + textPaint.measureText(suraAyah)
    if (indicators.isNotEmpty()) {
      width += 2 * gap + dividerWidth + indicators.size * iconSize + (indicators.size - 1) * indicatorGap
      labels.filterNotNull().forEach { width += labelGap + textPaint.measureText(it) }
    }
    return width
  }

  private fun isRtl() = layoutDirection == LAYOUT_DIRECTION_RTL

  override fun onDraw(canvas: Canvas) {
    val radius = box.height() / 2
    canvas.drawRoundRect(box, radius, radius, boxPaint)

    val rtl = isRtl()
    var x = if (rtl) box.right - horizontalPadding else box.left + horizontalPadding
    // the left edge of the next item, laid out in reading order
    fun next(itemWidth: Float): Float {
      val left = if (rtl) x - itemWidth else x
      x += if (rtl) -itemWidth else itemWidth
      return left
    }

    val baseline = box.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
    canvas.drawText(suraAyah, next(textPaint.measureText(suraAyah)), baseline, textPaint)
    if (indicators.isEmpty()) return

    next(gap)
    val dividerLeft = next(dividerWidth)
    canvas.drawRect(
      dividerLeft, box.centerY() - dividerHeight / 2,
      dividerLeft + dividerWidth, box.centerY() + dividerHeight / 2,
      dividerPaint
    )
    next(gap)

    val iconTop = (box.centerY() - iconSize / 2f).toInt()
    indicators.forEachIndexed { i, indicator ->
      if (i > 0) next(indicatorGap)
      val iconLeft = next(iconSize.toFloat()).toInt()
      icon(indicator.icon).apply {
        setTint(indicator.color)
        setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
        draw(canvas)
      }
      labels[i]?.let { label ->
        next(labelGap)
        canvas.drawText(label, next(textPaint.measureText(label)), baseline, textPaint)
      }
    }
  }

  private fun icon(@DrawableRes res: Int): Drawable =
    icons.getOrPut(res) { requireNotNull(ContextCompat.getDrawable(context, res)).mutate() }
}
