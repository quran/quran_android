package com.quran.labs.androidquran.extra.feature.linebyline.model

data class DisplayText(
  val suraText: String,
  val juzText: String,
  val rub3Text: String,
  val manzilText: String,
  val localizedPageText: String
) {
  val juzAreaText = "$juzText$rub3Text$manzilText"
}
