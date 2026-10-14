package com.quran.labs.androidquran.view

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R

class TagsViewGroup @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
  private val chipHeight = resources.getDimensionPixelSize(R.dimen.tag_height)
  private val chipSpacing = resources.getDimensionPixelSize(R.dimen.tag_margin)
  private val chipPadding = resources.getDimensionPixelSize(R.dimen.tag_padding)
  private val chipTextSize = resources.getDimensionPixelSize(R.dimen.tag_text_size).toFloat()
  private val chipTextColor = ContextCompat.getColor(context, R.color.accent_color)

  fun setTags(tags: List<Tag>) {
    removeAllViews()
    tags.forEach { tag ->
      addView(
        TextView(context).apply {
          text = tag.name
          setTextSize(TypedValue.COMPLEX_UNIT_PX, chipTextSize)
          setTextColor(chipTextColor)
          setBackgroundResource(R.drawable.tag_chip_background)
          setPadding(chipPadding, 0, chipPadding, 0)
          gravity = Gravity.CENTER
          isSingleLine = true
          // names never truncate in practice; this only guards one absurdly long name
          ellipsize = TextUtils.TruncateAt.END
        },
        LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight)
      )
    }
    requestLayout()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val available = width - paddingLeft - paddingRight
    val childHeightSpec = MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY)
    val childWidthSpec = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST)

    var lines = 0
    var lineWidth = 0
    forEachChip { child ->
      child.measure(childWidthSpec, childHeightSpec)
      when {
        lines == 0 -> {
          lines = 1
          lineWidth = child.measuredWidth
        }

        lineWidth + chipSpacing + child.measuredWidth > available -> {
          lines++
          lineWidth = child.measuredWidth
        }

        else -> lineWidth += chipSpacing + child.measuredWidth
      }
    }

    val contentHeight = if (lines == 0) 0 else lines * chipHeight + (lines - 1) * chipSpacing
    setMeasuredDimension(width, contentHeight + paddingTop + paddingBottom)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
    val start = paddingLeft
    val end = (r - l) - paddingRight
    val available = end - start

    var lineWidth = 0
    var top = paddingTop
    var isFirstOnLine = true
    forEachChip { child ->
      val childWidth = child.measuredWidth
      if (!isFirstOnLine && lineWidth + chipSpacing + childWidth > available) {
        top += chipHeight + chipSpacing
        lineWidth = 0
        isFirstOnLine = true
      }
      if (!isFirstOnLine) {
        lineWidth += chipSpacing
      }

      if (isRtl) {
        child.layout(end - lineWidth - childWidth, top, end - lineWidth, top + chipHeight)
      } else {
        child.layout(start + lineWidth, top, start + lineWidth + childWidth, top + chipHeight)
      }
      lineWidth += childWidth
      isFirstOnLine = false
    }
  }

  private inline fun forEachChip(action: (View) -> Unit) {
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      if (child.visibility != GONE) {
        action(child)
      }
    }
  }
}
