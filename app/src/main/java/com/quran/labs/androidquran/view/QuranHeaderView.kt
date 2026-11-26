package com.quran.labs.androidquran.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.quran.labs.androidquran.util.QuranUtils

class QuranHeaderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

  private var title: View? = null
  private var pageNumber: View? = null
  private val isRtl: Boolean = QuranUtils.isRtl()

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)

    val titleView = getChildAt(0) ?: return
    val pageNumberView = getChildAt(1) ?: return
    title = titleView
    pageNumber = pageNumberView

    measureChildWithMargins(pageNumberView, widthMeasureSpec, 0, heightMeasureSpec, 0)
    measureChildWithMargins(
      titleView, widthMeasureSpec,
      pageNumberView.measuredWidth, heightMeasureSpec, 0
    )
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val titleView = title ?: return
    val pageNumberView = pageNumber ?: return
    val left = if (isRtl) pageNumberView else titleView
    val right = if (isRtl) titleView else pageNumberView

    var top = ((b - t) - titleView.measuredHeight) / 2
    left.layout(
      paddingLeft,
      top,
      paddingLeft + left.measuredWidth,
      top + left.measuredHeight
    )
    top = ((b - t) - pageNumberView.measuredHeight) / 2
    right.layout(
      r - (right.measuredWidth + paddingRight),
      top,
      r - paddingRight,
      top + right.measuredHeight
    )
  }

  override fun generateLayoutParams(attrs: AttributeSet): LayoutParams {
    return MarginLayoutParams(context, attrs)
  }

  override fun generateLayoutParams(p: LayoutParams): LayoutParams {
    return MarginLayoutParams(p)
  }

  override fun generateDefaultLayoutParams(): LayoutParams {
    return MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
  }
}
