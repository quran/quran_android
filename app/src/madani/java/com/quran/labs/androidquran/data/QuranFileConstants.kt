package com.quran.labs.androidquran.data

import android.os.Build
import com.quran.data.page.provider.MadaniConstants
import com.quran.labs.androidquran.database.DatabaseHandler
import com.quran.labs.androidquran.ui.util.TypefaceManager

object QuranFileConstants {
  // server urls
  const val FONT_TYPE = TypefaceManager.TYPE_UTHMANI_HAFS

  // arabic database
  @JvmField
  val ARABIC_DATABASE = MadaniConstants.ARABIC_DATABASE

  @JvmField
  val ARABIC_SHARE_TABLE =
    if (Build.VERSION.SDK_INT >= 21) DatabaseHandler.SHARE_TEXT_TABLE else DatabaseHandler.ARABIC_TEXT_TABLE

  @JvmField
  val ARABIC_SHARE_TEXT_HAS_BASMALLAH = Build.VERSION.SDK_INT >= 21

  const val FETCH_QUARTER_NAMES_FROM_DATABASE = false
}
