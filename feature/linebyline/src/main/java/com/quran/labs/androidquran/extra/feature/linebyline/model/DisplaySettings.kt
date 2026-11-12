package com.quran.labs.androidquran.extra.feature.linebyline.model

import com.quran.data.model.PageAppearance

data class DisplaySettings(
  val pageAppearance: PageAppearance,
  val textBrightness: Int,
  val nightModeBackgroundBrightness: Int,
  val dayBackgroundColor: Int?,
  val showHeaderFooter: Boolean,
  val showSidelines: Boolean,
  val showLineDividers: Boolean
)
