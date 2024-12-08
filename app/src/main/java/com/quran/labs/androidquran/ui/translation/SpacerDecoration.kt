package com.quran.labs.androidquran.ui.translation

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacerDecoration(
  private var top: Int,
  private var bottom: Int
) : RecyclerView.ItemDecoration() {

  fun setOffsets(top: Int, bottom: Int): Boolean {
    return if (this.top != top || this.bottom != bottom) {
      this.top = top
      this.bottom = bottom
      true
    } else {
      false
    }
  }

  override fun getItemOffsets(
    outRect: Rect,
    view: View,
    parent: RecyclerView,
    state: RecyclerView.State
  ) {
    val adapter = parent.adapter ?: return
    when (parent.getChildAdapterPosition(view)) {
      0 -> outRect.top = top
      adapter.itemCount - 1 -> outRect.bottom = bottom
      else -> outRect.set(0, 0, 0, 0)
    }
  }
}
