package com.quran.labs.androidquran.util;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.quran.labs.androidquran.data.ApplicationConstants;

public class BookmarksManager {
	
	private static final String SEPARATOR = ",";
	private static BookmarksManager instance;
	private ArrayList<Integer> bookmarks;
	private HashMap<Integer, Boolean> bookmarksMap;
	
	private BookmarksManager() {		
		bookmarks = new ArrayList<Integer>();
		bookmarksMap = new HashMap<Integer, Boolean>();
	}
	
	public static boolean toggleBookmarkState(int page, SharedPreferences preferences) {
		boolean ret = getInstance().bookmarksMap.containsKey(page);
		if (ret) {
			getInstance().bookmarks.remove(new Integer(page));
			getInstance().bookmarksMap.remove(new Integer(page));
		} else { 
			getInstance().bookmarks.add(0, page);
			getInstance().bookmarksMap.put(new Integer(page), new Boolean(true));
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
		getInstance().bookmarks.clear();
		for (String p : pages) {
			try {
				Integer page = Integer.valueOf(p);
				getInstance().bookmarks.add(page);
				getInstance().bookmarksMap.put(page, new Boolean(true));
			}
			catch (NumberFormatException nfe){}
		}
	}
	
	public static BookmarksManager getInstance() {
		if (instance == null)
			instance = new BookmarksManager();
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
