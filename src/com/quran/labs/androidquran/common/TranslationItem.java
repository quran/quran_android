package com.quran.labs.androidquran.common;

import android.util.Log;

import com.quran.labs.androidquran.util.QuranUtils;

public class TranslationItem extends DownloadItem {
	
	public boolean isDownloaded() {
		Log.d("Quran Android: " + fileName, String.valueOf(QuranUtils.hasTranslation(fileName)));
		return QuranUtils.hasTranslation(fileName);
	}

}
