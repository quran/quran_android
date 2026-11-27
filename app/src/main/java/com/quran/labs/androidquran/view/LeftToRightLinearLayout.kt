package com.quran.labs.androidquran.view

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

open class LeftToRightLinearLayout @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
  init {
    layoutDirection = LAYOUT_DIRECTION_LTR
  }
}
