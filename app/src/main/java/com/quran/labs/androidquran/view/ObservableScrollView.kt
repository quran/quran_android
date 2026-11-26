package com.quran.labs.androidquran.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

class ObservableScrollView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = android.R.attr.scrollViewStyle
) : ScrollView(context, attrs, defStyle) {

  private var listener: OnScrollListener? = null

  fun interface OnScrollListener {
    fun onScrollChanged(scrollView: ObservableScrollView, x: Int, y: Int, oldx: Int, oldy: Int)
  }

  fun setOnScrollListener(listener: OnScrollListener?) {
    this.listener = listener
  }

  override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
    super.onScrollChanged(l, t, oldl, oldt)
    listener?.onScrollChanged(this, l, t, oldl, oldt)
  }
}
