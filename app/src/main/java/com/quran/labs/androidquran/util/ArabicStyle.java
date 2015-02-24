package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Typeface;

public class ArabicStyle {

  private static Typeface mTypeface;
  private static final String FONT = "fonts/DroidSansArabic.ttf";

  public static Typeface getTypeface(Context context) {
    if (mTypeface == null) {
      mTypeface = Typeface.createFromAsset(context.getAssets(), FONT);
    }
    return mTypeface;
  }

  public static String legacyGetArabicNumbers(String input) {
    char[] retChars = new char[input.length()];
    for (int n = 0; n < input.length(); n++) {
      retChars[n] = input.charAt(n);
      if (retChars[n] >= '0' && retChars[n] <= '9') {
        retChars[n] += 0x0660 - '0';
      }
    }
    return String.valueOf(retChars);
  }

  public static String reshape(String text) {
    ArabicReshaper rs = new ArabicReshaper();
    return rs.reshape(text);
  }
}
