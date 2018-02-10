package com.quran.labs.androidquran.data;

import com.quran.labs.androidquran.ui.util.TypefaceManager;

import android.os.Build;

public class QuranFileConstants {
  // server urls
  public static final int FONT_TYPE = TypefaceManager.TYPE_NOOR_HAYAH;

  // arabic database
  public static final String ARABIC_DATABASE =
      Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1 ?
          "quran.ar_naskh.db" : "quran.ar.db";
}
