package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.database.DatabaseHandler

object QuranFileConstants {
  // server urls
  const val FONT_TYPE = TypefaceManager.TYPE_UTHMANIC_WARSH

  // arabic database
  const val ARABIC_DATABASE = "quran.ar.warsh.db"
  const val ARABIC_SHARE_TABLE = DatabaseHandler.ARABIC_TEXT_TABLE
  const val ARABIC_SHARE_TEXT_HAS_BASMALLAH = true
  const val FETCH_QUARTER_NAMES_FROM_DATABASE = true

  const val FALLBACK_PAGE_TYPE = "warsh"

  var ICON_RESOURCE_ID = com.quran.labs.androidquran.pages.warsh.R.drawable.icon
}
