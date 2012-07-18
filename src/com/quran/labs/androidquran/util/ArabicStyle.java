package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Typeface;

public class ArabicStyle {

	private static Typeface mTypeface;
   private static final String FONT = "fonts/DroidSansArabic.ttf";

	public static Typeface getTypeface(Context context) {
      if (mTypeface == null){
         mTypeface = Typeface.createFromAsset(context.getAssets(), FONT);
      }
		return mTypeface;
	}
	public static String reshape(Context context, String text){
		ArabicReshaper rs = new ArabicReshaper();
		return rs.reshape(text);
	}
}
