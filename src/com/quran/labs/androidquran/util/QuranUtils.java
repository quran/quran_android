package com.quran.labs.androidquran.util;

import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.os.Build;

import com.quran.labs.androidquran.data.ApplicationConstants;

public class QuranUtils {
	private static final String BOOKMARKS_SEPARATOR = ",";
	
	public static List<Integer> getBookmarks(SharedPreferences preferences){
		List<Integer> bookmarks = new ArrayList<Integer>();
		
		String str = preferences.getString(ApplicationConstants.PREF_BOOKMARKS, "");
		if (str.length() == 0) return bookmarks;

		String [] pages = str.split(BOOKMARKS_SEPARATOR);
		for (String p : pages) {
			try {
				Integer page = Integer.valueOf(p);
				bookmarks.add(page);
			} catch (NumberFormatException nfe){}
		}
		
		return bookmarks;
	}
	
    public static boolean isSdk15() {
    	// Build.VERSION.SDK_INT is only 1.6+ :(
        if (Build.VERSION.RELEASE.startsWith("1.5"))  
            return true;  
        return false;  
     }
    
    public static boolean doesStringContainArabic(String s){
    	if (s == null) return false;
    	
    	int length = s.length();
    	for (int i=0; i<length; i++){
    		int current = (int)s.charAt(i);
    		// Skip space
    		if (current == 32)
    			continue;
        	// non-reshaped arabic
        	if ((current >= 1570) && (current <= 1610))
        		return true;
        	// re-shaped arabic
        	else if ((current >= 65133) && (current <= 65276))
        		return true;
        	// if the value is 42, it deserves another chance :p
        	// (in reality, 42 is a * which is useful in searching sqlite)
        	else if (current != 42)
        		return false;
    	}
    	return false;
    }
}
