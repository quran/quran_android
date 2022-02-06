package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.R

class QuranHeaderPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

  init {
    layoutResource = R.layout.about_header
    isSelectable = false
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    if (isEnabled) {
      val tv = holder.findViewById(R.id.title) as? TextView
      tv?.setTextColor(Color.WHITE)
    }
  }
}
