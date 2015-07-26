package com.quran.labs.androidquran.ui.util;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;

public class TypefaceManager {
  private static Typeface sTypeface;

  public static Typeface getHafsTypeface(@NonNull Context context) {
    if (sTypeface == null) {
      sTypeface = Typeface.createFromAsset(context.getAssets(), "uthmanic_hafs_ver09.otf");
    }
    return sTypeface;
  }
}
