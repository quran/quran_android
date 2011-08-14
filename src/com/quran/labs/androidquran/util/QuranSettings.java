package com.quran.labs.androidquran.util;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.quran.labs.androidquran.data.ApplicationConstants;


public class QuranSettings {
	
	private static QuranSettings instance = new QuranSettings();
	private boolean arabicNames = false;
	private boolean keepScreenOn = false;
	private boolean lockOrientation = false;
	private boolean landscapeOrientation = false;
	private boolean displayMarkerPopup = false;
	private int translationTextSize = ApplicationConstants.DEFAULT_TEXT_SIZE;
	private int lastPage = 0;
	private String activeTranslation = null;
	private boolean reshapeArabic = false;
	private int lastReader = 0;

	private QuranSettings() {
		
	}

	public int getLastReader() {
		return lastReader;
	}

	public void setLastReader(int lastReader) {
		this.lastReader = lastReader;
	}

	public Integer getLastPage() {
		return lastPage;
	}

	public void setLastPage(Integer lastPage) {
		this.lastPage = (lastPage == null)? ApplicationConstants.NO_PAGE_SAVED : lastPage;
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

	public boolean isKeepScreenOn() {
		return keepScreenOn;
	}

	public void setKeepScreenOn(boolean keepScreenOn) {
		this.keepScreenOn = keepScreenOn;
	}
	
	public boolean isLockOrientation() {
		return lockOrientation;
	}

	public void setLockOrientation(boolean lockOrientation) {
		this.lockOrientation = lockOrientation;
	}
	
	public boolean isLandscapeOrientation() {
		return landscapeOrientation;
	}

	public void setLandscapeOrientation(boolean landscapeOrientation) {
		this.landscapeOrientation = landscapeOrientation;
	}
	
	public boolean isDisplayMarkerPopup() {
		return displayMarkerPopup;
	}

	public void setMarkerPopup(boolean displayMarkerPopup) {
		this.displayMarkerPopup = displayMarkerPopup;
	}

	public int getTranslationTextSize() {
		return translationTextSize;
	}
	
	public String getActiveTranslation() {
		return activeTranslation;
	}
	
	public void setActiveTranslation(String activeTranslation){
		this.activeTranslation = activeTranslation;
	}
	
	public boolean isReshapeArabic() {
		return reshapeArabic;
	}

	public void setReshapeArabic(boolean reshapeArabic) {
		this.reshapeArabic = reshapeArabic;
	}

	public static void load(SharedPreferences preferences) {
		instance.arabicNames = preferences.getBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, false);
		instance.keepScreenOn = preferences.getBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, true);
		instance.lockOrientation = preferences.getBoolean(ApplicationConstants.PREF_LOCK_ORIENTATION, false);
		instance.landscapeOrientation = preferences.getBoolean(ApplicationConstants.PREF_LANDSCAPE_ORIENTATION, false);
		instance.displayMarkerPopup = preferences.getBoolean(ApplicationConstants.PREF_DISPLAY_MARKER_POPUP, false);
		instance.translationTextSize = preferences.getInt(ApplicationConstants.PREF_TRANSLATION_TEXT_SIZE, ApplicationConstants.DEFAULT_TEXT_SIZE);
		instance.lastPage = preferences.getInt(ApplicationConstants.PREF_LAST_PAGE, ApplicationConstants.NO_PAGE_SAVED);
		instance.activeTranslation = preferences.getString(ApplicationConstants.PREF_ACTIVE_TRANSLATION, null);
		instance.reshapeArabic = preferences.getBoolean(ApplicationConstants.PREF_RESHAPE_ARABIC, false);
		instance.lastReader = preferences.getInt(ApplicationConstants.PREF_LAST_READER, 0);
	}
	
	public static void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();
		editor.putBoolean(ApplicationConstants.PREF_USE_ARABIC_NAMES, instance.arabicNames);
		editor.putBoolean(ApplicationConstants.PREF_KEEP_SCREEN_ON, instance.keepScreenOn);
		editor.putBoolean(ApplicationConstants.PREF_LOCK_ORIENTATION, instance.lockOrientation);
		editor.putBoolean(ApplicationConstants.PREF_LANDSCAPE_ORIENTATION, instance.landscapeOrientation);
		editor.putBoolean(ApplicationConstants.PREF_DISPLAY_MARKER_POPUP, instance.displayMarkerPopup);
		editor.putInt(ApplicationConstants.PREF_TRANSLATION_TEXT_SIZE, instance.translationTextSize);
		editor.putInt(ApplicationConstants.PREF_LAST_PAGE, instance.lastPage);
		editor.putString(ApplicationConstants.PREF_ACTIVE_TRANSLATION, instance.activeTranslation);
		editor.putBoolean(ApplicationConstants.PREF_RESHAPE_ARABIC, instance.reshapeArabic);
		editor.putInt(ApplicationConstants.PREF_LAST_READER, instance.lastReader);
		editor.commit();
	}
}
