package com.quran.labs.androidquran.extra.feature.linebyline.model

data class DisplaySettings(
  val isNightMode: Boolean,
  val textBrightness: Int,
  val nightModeBackgroundBrightness: Int,
  val showHeaderFooter: Boolean,
  val showSidelines: Boolean,
  val showLineDividers: Boolean
)
