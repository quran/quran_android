package com.quran.labs.androidquran.data;

import com.quran.data.page.provider.naskh.NaskhConstants;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.util.TypefaceManager;

public class QuranFileConstants {
  // server urls
  public static final int FONT_TYPE = TypefaceManager.TYPE_NOOR_HAYAH;

  // arabic database
  public static final String ARABIC_DATABASE = NaskhConstants.ARABIC_DATABASE;
  public static final String ARABIC_SHARE_TABLE = DatabaseHandler.ARABIC_TEXT_TABLE;

  public static final boolean ARABIC_SHARE_TEXT_HAS_BASMALLAH = false;
  public static final boolean FETCH_QUARTER_NAMES_FROM_DATABASE = false;
}
