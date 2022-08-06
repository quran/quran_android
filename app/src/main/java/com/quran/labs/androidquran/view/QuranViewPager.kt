package com.quran.labs.androidquran.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class QuranViewPager : ViewPager {

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    return try {
      super.onTouchEvent(ev)
    } catch (e: IllegalArgumentException) {
      false
    }
  }
}
