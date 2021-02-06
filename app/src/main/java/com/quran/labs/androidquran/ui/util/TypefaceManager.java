package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.graphics.Typeface;

import com.quran.labs.androidquran.data.QuranFileConstants;

import androidx.annotation.NonNull;

public class TypefaceManager {
  public static final int TYPE_UTHMANI_HAFS = 1;
  public static final int TYPE_NOOR_HAYAH = 2;
  public static final int TYPE_UTHMANIC_WARSH = 3;

  private static Typeface typeface;
  private static Typeface arabicTafseerTypeface;
  private static Typeface arabicHeaderFooterTypeface;

  public static Typeface getUthmaniTypeface(@NonNull Context context) {
    if (typeface == null) {
      final String fontName;
      switch (QuranFileConstants.FONT_TYPE) {
        case TYPE_NOOR_HAYAH: {
          fontName = "noorehira.ttf";
          break;
        }
        case TYPE_UTHMANIC_WARSH: {
          fontName = "uthmanic_warsh_ver09.ttf";
          break;
        }
        case TYPE_UTHMANI_HAFS:
        default: {
          fontName = "uthmanic_hafs_ver12.otf";
        }
      }
      typeface = Typeface.createFromAsset(context.getAssets(), fontName);
    }
    return typeface;
  }

  public static Typeface getTafseerTypeface(@NonNull Context context) {
    if (arabicTafseerTypeface == null) {
      arabicTafseerTypeface = Typeface.createFromAsset(context.getAssets(), "kitab.ttf");
    }
    return arabicTafseerTypeface;
  }

  public static Typeface getHeaderFooterTypeface(@NonNull Context context) {
    if (arabicHeaderFooterTypeface == null) {
      arabicHeaderFooterTypeface = Typeface.createFromAsset(context.getAssets(), "UthmanTN1Ver10.otf");
    }
    return arabicHeaderFooterTypeface;
  }
}
