package com.quran.labs.androidquran.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.quran.labs.androidquran.common.ApplicationConstants;


public class QuranSettings {
	
	private static QuranSettings instance = new QuranSettings();
	private boolean arabicNames = false;
	private boolean hideTitle = false;
	
	private QuranSettings() {
		
	}
	
	public static QuranSettings getInstance(){
		return instance;
	}

	public boolean isArabicNames() {
		return arabicNames;
	}

	public void setArabicNames(boolean useArabicNames) {
		this.arabicNames = useArabicNames;
	}
	
	

	public boolean isHideTitle() {
		return hideTitle;
	}

	public void setHideTitle(boolean hideTitle) {
		this.hideTitle = hideTitle;
	}

	public static void load(SharedPreferences preferences) {
		instance.arabicNames = preferences.getBoolean(ApplicationConstants.USE_ARABIC_NAMES, false);
		instance.hideTitle = preferences.getBoolean(ApplicationConstants.HIDE_TITLE, false);
	}
	
	public static void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();
		editor.putBoolean(ApplicationConstants.USE_ARABIC_NAMES, instance.arabicNames);
		editor.putBoolean(ApplicationConstants.HIDE_TITLE, instance.hideTitle);
		editor.commit();
	}

}
