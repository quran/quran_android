package com.quran.labs.androidquran.common;



public class ApplicationConstants {

	// activity codes
	public static final int DATA_CHECK_CODE = 1;
	public static final int SETTINGS_CODE = DATA_CHECK_CODE + 1;
	public static final int BOOKMARKS_CODE = SETTINGS_CODE + 1;
	public static final int QURAN_VIEW_CODE = BOOKMARKS_CODE + 1;
	
	// Pages
	public static final int PAGES_FIRST = 1;
	public static final int PAGES_LAST = 604;
	public static final int SURAS_COUNT = 114;
	public static final int JUZ2_COUNT = 30;
	public static final int AYA_MIN = 1;
	public static final int AYA_MAX = 286;
	
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
}
