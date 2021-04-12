package com.quran.labs.androidquran.data;

import android.os.Build;

import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.util.TypefaceManager;

public class QuranFileConstants {
  // server urls
  public static final int FONT_TYPE = TypefaceManager.TYPE_UTHMANI_HAFS;

  // arabic database
  public static final String ARABIC_DATABASE =
      Build.VERSION.SDK_INT >= 21 ? "quran.ar.uthmani.v2.db" : "quran.ar.uthmani_simple.db";

  public static final String ARABIC_SHARE_TABLE =
      Build.VERSION.SDK_INT >= 21 ?
          DatabaseHandler.SHARE_TEXT_TABLE : DatabaseHandler.ARABIC_TEXT_TABLE;
  public static final boolean ARABIC_SHARE_TEXT_HAS_BASMALLAH = true;
  public static final boolean FETCH_QUARTER_NAMES_FROM_DATABASE = false;
}
