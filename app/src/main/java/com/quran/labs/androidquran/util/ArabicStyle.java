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
	public static String MakeItArabicNumbers(String txt){
        char[] retChars = new char[txt.length()];
        for(int n=0;n<txt.length();n++){
            retChars[n] = txt.charAt(n);
            if(retChars[n]>='0' && retChars[n]<='9')
                retChars[n]+= 0x0660 - '0';
        }
        StringBuilder ret = new StringBuilder();
        ret.append(retChars);
        return ret.toString();
    }

    public static String MakeItArabicNumbers(int number){
        String numberInString = "" + number;
        char[] retChars = new char[numberInString.length()];
        for(int n=0;n<numberInString.length();n++){
            retChars[n] = (char) (numberInString.charAt(n) + 0x0660 - '0');
        }
        StringBuilder ret = new StringBuilder();
        ret.append(retChars);
        return ret.toString();
    }
	public static String reshape(Context context, String text){
		ArabicReshaper rs = new ArabicReshaper();
		return rs.reshape(text);
	}
}
