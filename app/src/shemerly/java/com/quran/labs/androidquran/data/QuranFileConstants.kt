package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.ui.util.TypefaceManager

object QuranFileConstants {
  // server urls
  const val FONT_TYPE = TypefaceManager.TYPE_UTHMANI_HAFS

  // arabic database
  const val ARABIC_DATABASE = "quran.ar.uthmani.v2.db"
  const val ARABIC_SHARE_TABLE = DatabaseHandler.SHARE_TEXT_TABLE
  const val ARABIC_SHARE_TEXT_HAS_BASMALLAH = true
  const val FETCH_QUARTER_NAMES_FROM_DATABASE = false

  const val FALLBACK_PAGE_TYPE = "shemerly"
}
