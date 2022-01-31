package com.quran.labs.androidquran.ui.preference

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.quran.labs.androidquran.R

class QuranHeaderPreference : Preference {

  constructor(context: Context) : super(context) {
    init()
  }

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    init()
  }

  constructor(
    context: Context, attrs: AttributeSet, defStyleAttr: Int
  ) : super(context, attrs, defStyleAttr) {
    init()
  }

  constructor(
    context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int
  ) : super(context, attrs, defStyleAttr, defStyleRes) {
    init()
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    if (isEnabled) {
      val tv = holder.findViewById(R.id.title) as? TextView
      tv?.setTextColor(Color.WHITE)
    }
  }

  private fun init() {
    layoutResource = R.layout.about_header
    isSelectable = false
  }
}
