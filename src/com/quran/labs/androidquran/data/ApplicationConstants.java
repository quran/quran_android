package com.quran.labs.androidquran.data;



public class ApplicationConstants {

	// activity codes
	public static final int DATA_CHECK_CODE = 1;
	public static final int SETTINGS_CODE = DATA_CHECK_CODE + 1;
	public static final int BOOKMARKS_CODE = SETTINGS_CODE + 1;
	public static final int QURAN_VIEW_CODE = BOOKMARKS_CODE + 1;
	public static final int TRANSLATION_VIEW_CODE = QURAN_VIEW_CODE + 1;
	
	// Numerics
	public static final int DEFAULT_TEXT_SIZE = 15;
	
	// Pages
	public static final int PAGES_FIRST = 1;
	public static final int PAGES_LAST = 604;
	public static final int SURAS_COUNT = 114;
	public static final int SURAS_FIRST_INDEX = 0;
	public static final int SURAS_LAST_INDEX = 113;
	public static final int JUZ2_COUNT = 30;
	public static final int AYA_MIN = 1;
	public static final int AYA_MAX = 286;
	public static final int NO_PAGE_SAVED = -1;
	
	// Colors
	public static final String PAGE_BORDER_COLOR = "#802A2A";
	
	// dialogs
	public static final int JUMP_DIALOG = 1;
	
	// Preferences Key
	public static final String PREFERNCES = "QuranAndroid_Settings";
	
	// Settings Key
	public static final String PREF_USE_ARABIC_NAMES = "useArabicNames";
	public static final String PREF_FULL_SCREEN = "fullScreen"; 
	public static final String PREF_SHOW_CLOCK = "showClock";
	public static final String PREF_LAST_PAGE = "lastPage";
	public static final String PREF_BOOKMARKS = "bookmarks";
	public static final String PREF_KEEP_SCREEN_ON = "keepScreenOn";
	public static final String PREF_LOCK_ORIENTATION = "lockOrientation";
	public static final String PREF_LANDSCAPE_ORIENTATION = "landscapeOrientation";
	public static final String PREF_TRANSLATION_TEXT_SIZE = "translationTextSize";
	public static final String PREF_ACTIVE_TRANSLATION = "activeTranslation";
}
