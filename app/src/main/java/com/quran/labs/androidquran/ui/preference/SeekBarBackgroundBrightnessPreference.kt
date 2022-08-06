package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

class SeekBarBackgroundBrightnessPreference(
  context: Context, attrs: AttributeSet
) : SeekBarPreference(context, attrs) {

  override fun getPreviewVisibility(): Int = View.GONE

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    super.onProgressChanged(seekBar, progress, fromUser)
    previewBox.visibility = View.VISIBLE

    val boxColor = Color.argb(255, progress, progress, progress)
    previewBox.setBackgroundColor(boxColor)
  }
}
