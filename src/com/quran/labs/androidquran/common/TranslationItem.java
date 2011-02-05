package com.quran.labs.androidquran.common;

import com.quran.labs.androidquran.util.QuranUtils;

public class TranslationItem extends DownloadItem {
	
	public boolean isDownloaded() {
		return QuranUtils.hasTranslation(fileName);
	}

}
