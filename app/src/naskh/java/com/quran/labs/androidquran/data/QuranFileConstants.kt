package com.quran.labs.androidquran.data

import com.quran.labs.androidquran.ui.util.TypefaceManager
import com.quran.data.page.provider.naskh.NaskhConstants
import com.quran.labs.androidquran.database.DatabaseHandler

object QuranFileConstants {
  // server urls
  const val FONT_TYPE = TypefaceManager.TYPE_NOOR_HAYAH

  // arabic database
  const val ARABIC_DATABASE = NaskhConstants.ARABIC_DATABASE
  const val ARABIC_SHARE_TABLE = DatabaseHandler.ARABIC_TEXT_TABLE
  const val ARABIC_SHARE_TEXT_HAS_BASMALLAH = false
  const val FETCH_QUARTER_NAMES_FROM_DATABASE = false
  const val FALLBACK_PAGE_TYPE = "naskh"
  const val SEARCH_EXTRA_REPLACEMENTS = ""

  var ICON_RESOURCE_ID = com.quran.labs.androidquran.pages.naskh.R.drawable.icon
}
