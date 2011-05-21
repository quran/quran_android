package com.quran.labs.androidquran.util;

import android.content.res.AssetManager;
import android.graphics.Typeface;

public class ArabicStyle {

	private static Typeface typeface;

	public static void setAssetManager(AssetManager asset) {
		typeface = Typeface.createFromAsset(asset, "fonts/DroidSansArabic.ttf");
	}

	public static Typeface getTypeface() {
		return typeface;
	}
	public static String reshape(String text){
		Reshaping rs = new Reshaping();
		return rs.reshape(text);
	}
}
