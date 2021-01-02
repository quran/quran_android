package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

class SeekBarTextBrightnessPreference(context: Context?, attrs: AttributeSet?) : SeekBarPreference(context, attrs) {

  override fun getPreviewVisibility(): Int = View.VISIBLE

  override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
    super.onProgressChanged(seek, value, fromTouch)
    val lineColor = Color.argb(value, 255, 255, 255)
    mPreviewText.setTextColor(lineColor)
  }
}
