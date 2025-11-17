package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.R

/**
 * A custom Preference that shows a color picker dialog when clicked.
 * Allows users to select custom colors using RGB sliders.
 */
open class ColorPickerPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

  private var currentColor: Int = Color.BLACK
  private var onColorChangedListener: ((color: Int) -> Unit)? = null

  init {
    widgetLayoutResource = R.layout.preference_color_picker
    isPersistent = false // handled manually
  }

  fun setInitialColor(color: Int) {
    currentColor = color
    notifyChanged()
  }

  fun getCurrentColor(): Int = currentColor

  fun setOnColorChangedListener(listener: (color: Int) -> Unit) {
    onColorChangedListener = listener
  }

  override fun onClick() {
    showColorPickerDialog()
  }

  private fun showColorPickerDialog() {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)

    val redSlider = dialogView.findViewById<SeekBar>(R.id.red_slider)
    val greenSlider = dialogView.findViewById<SeekBar>(R.id.green_slider)
    val blueSlider = dialogView.findViewById<SeekBar>(R.id.blue_slider)
    val valueText = dialogView.findViewById<TextView>(R.id.color_value_text)

    val red = Color.red(currentColor)
    val green = Color.green(currentColor)
    val blue = Color.blue(currentColor)

    redSlider.progress = red
    greenSlider.progress = green
    blueSlider.progress = blue

    fun updateColor() {
      valueText.text = context.getString(
        R.string.color_rgb_values, redSlider.progress, greenSlider.progress, blueSlider.progress
      )
    }

    redSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) updateColor()
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    greenSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) updateColor()
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    blueSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) updateColor()
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) {}
      override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })



    updateColor()

    val dialog = AlertDialog.Builder(context).setTitle(R.string.color_picker_dialog_title).setView(dialogView)
      .setPositiveButton(R.string.color_picker_apply) { _, _ ->
        val newColor =
          Color.argb(255, redSlider.progress, greenSlider.progress, blueSlider.progress)
        onColorSelected(newColor)
      }.setNegativeButton(android.R.string.cancel, null).setNeutralButton(R.string.color_picker_reset_default) { _, _ ->
        resetToDefault()
      }.create()

    dialog.show()

    val defaultButtonColor = ContextCompat.getColor(context, android.R.color.holo_blue_dark)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
      ?.setTextColor(ColorStateList.valueOf(defaultButtonColor))
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
      ?.setTextColor(ColorStateList.valueOf(defaultButtonColor))
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
      ?.setTextColor(ColorStateList.valueOf(defaultButtonColor))
  }

  protected open fun onColorSelected(color: Int) {
    if (currentColor != color) {
      currentColor = color
      notifyChanged()
      onColorChangedListener?.invoke(color)
    }
  }

  protected open fun resetToDefault() {
    // reset to stored preference value. to be overridden by subclasses
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)

    val colorSummary = holder.itemView.findViewById<TextView>(android.R.id.summary)
    colorSummary?.text = String.format("#%06X", 0xFFFFFF and currentColor)


  }
}
