package com.quran.labs.androidquran.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.quran.labs.androidquran.common.ApplicationConstants;


public class QuranSettings {
	
	private static QuranSettings instance = new QuranSettings();
	private boolean arabicNames = false;
	private boolean showClock = false;
	private boolean fullScreen = false;
	private boolean keepScreenOn = false;
	private int lastPage = 0;
	
	private QuranSettings() {
		
	}

	public int getLastPage() {
		return lastPage;
	}

	public void setLastPage(int lastPage) {
		this.lastPage = lastPage;
	}
	
	public boolean isFullScreen() {
		return fullScreen;
	}

	public void setFullScreen(boolean fullScreen) {
		this.fullScreen = fullScreen;
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

	public boolean isShowClock() {
		return showClock;
	}

	public void setShowClock(boolean showClock) {
		this.showClock = showClock;
	}

	public boolean isKeepScreenOn() {
		return keepScreenOn;
	}

	public void setKeepScreenOn(boolean keepScreenOn) {
		this.keepScreenOn = keepScreenOn;
	}

	public static void load(SharedPreferences preferences) {
		instance.arabicNames = preferences.getBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, false);
		instance.keepScreenOn = preferences.getBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, true);
		instance.fullScreen = preferences.getBoolean(ApplicationConstants.PREF_FULL_SCREEN, false);
		instance.showClock = preferences.getBoolean(ApplicationConstants.PREF_SHOW_CLOCK, false);
		instance.lastPage = preferences.getInt(ApplicationConstants.PREF_LAST_PAGE, ApplicationConstants.PAGES_FIRST);
	}
	
	public static void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();
		editor.putBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, instance.arabicNames);
		editor.putBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, instance.keepScreenOn);
		editor.putBoolean(ApplicationConstants.PREF_FULL_SCREEN, instance.fullScreen);
		editor.putBoolean(ApplicationConstants.PREF_SHOW_CLOCK, instance.showClock);
		editor.putInt(ApplicationConstants.PREF_LAST_PAGE, instance.lastPage);
		editor.commit();
	}
}
