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
		if (!QuranSettings.getInstance().isReshapeArabic())
			return text;
		ArabicReshaper rs = new ArabicReshaper();
		return rs.reshape(text);
	}
}
