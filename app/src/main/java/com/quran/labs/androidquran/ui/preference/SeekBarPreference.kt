package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranUtils

open class SeekBarPreference(
  context: Context,
  attrs: AttributeSet
) : Preference(context, attrs), SeekBar.OnSeekBarChangeListener {

  private lateinit var valueText: TextView
  protected lateinit var previewText: TextView
  protected lateinit var previewBox: View

  private val suffix = attrs.getAttributeValue(ANDROID_NS, "text")
  private val default = attrs.getAttributeIntValue(
    ANDROID_NS,
    "defaultValue",
    Constants.DEFAULT_TEXT_SIZE
  )
  private val maxValue = attrs.getAttributeIntValue(ANDROID_NS, "max", 100)
  private var currentValue = 0
  protected var value = 0

  init {
    layoutResource = R.layout.seekbar_pref
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    val seekBar = holder.findViewById(R.id.seekbar) as SeekBar
    valueText = holder.findViewById(R.id.value) as TextView
    previewText = holder.findViewById(R.id.pref_preview) as TextView
    previewBox = holder.findViewById(R.id.preview_square)
    previewText.visibility = getPreviewVisibility()
    seekBar.setOnSeekBarChangeListener(this)
    value = if (shouldDisableView) getPersistedInt(default) else 0
    seekBar.apply {
      max = maxValue
      progress = value
    }
  }

  override fun onSetInitialValue(defaultValue: Any?) {
    super.onSetInitialValue(defaultValue)
    value = if (shouldPersist()) {
      getPersistedInt(default)
    } else {
      if (defaultValue != null) default else 0
    }
  }

  override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
    val t = QuranUtils.getLocalizedNumber(context, progress)
    valueText.text = if (suffix == null) t else "$t$suffix"
    currentValue = progress
  }

  override fun onStartTrackingTouch(seekBar: SeekBar?) {}

  override fun onStopTrackingTouch(seekBar: SeekBar?) {
    if (shouldPersist()) {
      persistInt(currentValue)
      callChangeListener(currentValue)
    }
  }

  /**
   * Visibility of the preview view under the seek bar
   */
  protected open fun getPreviewVisibility(): Int = View.GONE

  companion object {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
  }
}
