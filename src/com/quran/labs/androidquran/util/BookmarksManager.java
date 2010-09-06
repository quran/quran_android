package com.quran.labs.androidquran.util;

import java.util.ArrayList;
import java.util.HashMap;

import com.quran.labs.androidquran.common.ApplicationConstants;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class BookmarksManager {
	
	private static final String SEPARATOR = ",";
	private static BookmarksManager instance = new BookmarksManager();
	private ArrayList<Integer> bookmarks;
	private HashMap<Integer, Boolean> bookmarksMap;
	
	private BookmarksManager() {		
		bookmarks = new ArrayList<Integer>();
		bookmarksMap = new HashMap<Integer, Boolean>();
	}
	
	public static boolean toggleBookmarkState(int page, SharedPreferences preferences) {
		boolean ret = instance.bookmarksMap.containsKey(page);
		if (ret) {
			instance.bookmarks.remove(new Integer(page));
			instance.bookmarksMap.remove(new Integer(page));
		} else { 
			instance.bookmarks.add(0, page);
			instance.bookmarksMap.put(new Integer(page), new Boolean(true));
		}
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
		if (str.length() == 0) return;
		
		String [] pages = str.split(SEPARATOR);
		instance.bookmarks.clear();
		for (String p : pages) {
			try {
				Integer page = Integer.valueOf(p);
				instance.bookmarks.add(page);
				instance.bookmarksMap.put(page, new Boolean(true));
			}
			catch (NumberFormatException nfe){}
		}
	}
	
	public static BookmarksManager getInstance() {
		return instance;
	}
	
	public ArrayList<Integer> getBookmarks() {
		return bookmarks;
	}
	
	public void removeAt(int index, SharedPreferences prefs) {
		if (index >= 0 && index < bookmarks.size()) {
			Integer page = bookmarks.remove(index);
			bookmarksMap.remove(page);
			save(prefs);
		}
	}
	
	public boolean contains(int page) {
		return bookmarksMap.containsKey(Integer.valueOf(page));
	}

}
