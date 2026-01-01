package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Sealed class for available background options.
 */
sealed class BackgroundOption(val storageKey: String) {
  object White : BackgroundOption(Constants.BACKGROUND_WHITE)
  object Cream : BackgroundOption(Constants.BACKGROUND_CREAM)
  object LightParchment : BackgroundOption(Constants.BACKGROUND_LIGHT_PARCHMENT)
  object SoftIvory : BackgroundOption(Constants.BACKGROUND_SOFT_IVORY)
  object Custom : BackgroundOption(Constants.BACKGROUND_CUSTOM)

  fun getDisplayName(context: Context): String {
    return when (this) {
      is White -> context.getString(R.string.page_background_white)
      is Cream -> context.getString(R.string.page_background_cream)
      is LightParchment -> context.getString(R.string.page_background_light_parchment)
      is SoftIvory -> context.getString(R.string.page_background_soft_ivory)
      is Custom -> context.getString(R.string.page_background_custom_color)
    }
  }

  fun getColor(context: Context): Int {
    return when (this) {
      is White -> Color.WHITE
      is Cream -> Color.parseColor(Constants.COLOR_CREAM)
      is LightParchment -> Color.parseColor(Constants.COLOR_LIGHT_PARCHMENT)
      is SoftIvory -> Color.parseColor(Constants.COLOR_SOFT_IVORY)
      is Custom -> {
        QuranSettings.getInstance(context).getCustomPageBackgroundInt()
      }
    }
  }

  companion object {
    fun getAllOptions(): List<BackgroundOption> {
      return listOf(White, Cream, LightParchment, SoftIvory, Custom)
    }
  }
}

/**
 * A ColorPickerPreference specifically for page background colors.
 * Provides preset background colors and allows custom color selection.
 */
class PageBackgroundPickerPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : ColorPickerPreference(context, attrs, defStyleAttr) {

  init {
    key = context.getString(R.string.prefs_page_background)
    title = context.getString(R.string.prefs_page_background_title)

    val currentBackgroundColor = QuranSettings.getInstance(context).getCustomPageBackgroundInt()
    setInitialColor(currentBackgroundColor)

    setOnColorChangedListener { color ->
      QuranSettings.getInstance(context).setCustomPageBackgroundColor(color)
      triggerUiUpdate()
    }
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    val previewBox = holder.itemView.findViewById<View>(R.id.preview_square)
    previewBox?.setBackgroundColor(getCurrentColor())
  }

  override fun onColorSelected(color: Int) {
    super.onColorSelected(color)
    notifyChanged()
  }

  override fun resetToDefault() {
    applyBackgroundSelection(BackgroundOption.White)
  }

  /**
   * Override to show both preset backgrounds and custom color option
   */
  override fun onClick() {
    showPageBackgroundPickerDialog()
  }

  private fun showPageBackgroundPickerDialog() {
    val backgroundOptions = BackgroundOption.getAllOptions()
    val backgroundNames = backgroundOptions.map { it.getDisplayName(context) }.toTypedArray()

    val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
      .setTitle(R.string.page_background_picker_title)
      .setItems(backgroundNames) { _, which ->
        val selectedOption = backgroundOptions[which]
        handleBackgroundSelection(selectedOption)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    dialog.show()
  }

  private fun handleBackgroundSelection(option: BackgroundOption) {
    when (option) {
      is BackgroundOption.Custom -> {
        super.onClick() // show color picker dialog
      }
      else -> {
        applyBackgroundSelection(option)
      }
    }
  }

  private fun applyBackgroundSelection(option: BackgroundOption) {
    val color = option.getColor(context)
    super.onColorSelected(color)
    QuranSettings.getInstance(context).setPageBackground(option.storageKey)
    triggerUiUpdate()
  }

  private fun triggerUiUpdate() {
    try {
      (context as? android.app.Activity)?.recreate() // to trigger immediate UI update
    } catch (_: Exception) {
      // changes will apply on next app launch
    }
  }
}
