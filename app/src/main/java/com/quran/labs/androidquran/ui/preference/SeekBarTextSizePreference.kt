package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

class SeekBarTextSizePreference(context: Context?, attrs: AttributeSet?) : SeekBarPreference(context, attrs) {

  override fun getPreviewVisibility(): Int = View.VISIBLE

  override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
    super.onProgressChanged(seek, value, fromTouch)
    mPreviewText.textSize = value.toFloat()
  }
}
