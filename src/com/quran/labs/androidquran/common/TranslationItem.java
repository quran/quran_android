package com.quran.labs.androidquran.common;

import android.util.Log;

import com.quran.labs.androidquran.util.QuranFileUtils;

public class TranslationItem extends DownloadItem {
	
	public boolean isDownloaded() {
		Log.d("Quran Android: " + fileName,
				String.valueOf(QuranFileUtils.hasTranslation(fileName)));
		return QuranFileUtils.hasTranslation(fileName);
	}

}
