package com.quran.labs.androidquran.util;

import android.os.Build;

public class QuranUtils {
	
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
