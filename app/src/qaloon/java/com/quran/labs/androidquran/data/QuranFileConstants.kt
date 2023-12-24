package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.labs.androidquran.database.DatabaseHandler

object QuranFileConstants {
  // server urls
  const val FONT_TYPE = TypefaceManager.TYPE_UTHMANIC_QALOON

  // arabic database
  const val ARABIC_DATABASE = "quran.ar.qaloon.db"
  const val ARABIC_SHARE_TABLE = DatabaseHandler.ARABIC_TEXT_TABLE
  const val ARABIC_SHARE_TEXT_HAS_BASMALLAH = true
  const val FETCH_QUARTER_NAMES_FROM_DATABASE = false

  const val FALLBACK_PAGE_TYPE = "qaloon"
  const val SEARCH_EXTRA_REPLACEMENTS = ""

  var ICON_RESOURCE_ID = com.quran.labs.androidquran.pages.qaloon.R.drawable.icon
}
