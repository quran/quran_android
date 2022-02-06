package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar

class SeekBarTextSizePreference(
  context: Context, attrs: AttributeSet
) : SeekBarPreference(context, attrs) {

  override fun getPreviewVisibility(): Int = View.VISIBLE

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    super.onProgressChanged(seekBar, progress, fromUser)
    previewText.textSize = progress.toFloat()
  }
}
