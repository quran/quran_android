package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.data.QuranFileConstants;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;

public class TypefaceManager {
  public static final int TYPE_UTHMANI_HAFS = 1;
  public static final int TYPE_NOOR_HAYAH = 2;

  private static Typeface sTypeface;

  public static Typeface getUthmaniTypeface(@NonNull Context context) {
    if (sTypeface == null) {
      final String fontName;
      switch (QuranFileConstants.FONT_TYPE) {
        case TYPE_NOOR_HAYAH: {
          fontName = "noorehira.ttf";
          break;
        }
        case TYPE_UTHMANI_HAFS:
        default: {
          fontName = "uthmanic_hafs_ver09.otf";
        }
      }
      sTypeface = Typeface.createFromAsset(context.getAssets(), fontName);
    }
    return sTypeface;
  }
}
