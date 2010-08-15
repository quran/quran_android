package com.quran.labs.androidquran.util;

import java.util.ArrayList;

import com.quran.labs.androidquran.common.ApplicationConstants;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class BookmarksManager {
	
	private static final String SEPARATOR = ",";
	private static BookmarksManager instance = new BookmarksManager();
	private ArrayList<Integer> bookmarks;
	
	private BookmarksManager() {		
		bookmarks = new ArrayList<Integer>();
	}
	
	public static boolean toggleBookmarkState(int page, SharedPreferences preferences) {
		boolean ret = instance.bookmarks.contains(page);
		if (ret)
			instance.bookmarks.remove(new Integer(page));
		else 
			instance.bookmarks.add(0, page);
		save(preferences);
		return !ret;
	}

	public static void save(SharedPreferences preferences) {
		Editor editor = preferences.edit();
		String str = "";
		for (Integer page : instance.bookmarks) {
			str += String.valueOf(page) + SEPARATOR;
		}
		editor.putString(ApplicationConstants.PREF_BOOKMARKS, str);
		editor.commit();
	}
	
	public static void load(SharedPreferences preferences) {
		String str = preferences.getString(ApplicationConstants.PREF_BOOKMARKS, "");
		String [] pages = str.split(SEPARATOR);
		instance.bookmarks.clear();
		for (String p : pages) {
			instance.bookmarks.add(Integer.valueOf(p));
		}
	}
	
	public static BookmarksManager getInstance() {
		return instance;
	}
	
	public ArrayList<Integer> getBookmarks() {
		return bookmarks;
	}
	
	public void removeAt(int index) {
		if (index >= 0 && index < bookmarks.size()) {
			bookmarks.remove(index);
		}
	}

}
