package com.quran.labs.androidquran.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class QuranUtils {
    
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

   public static boolean isOnWifiNetwork(Context context){
      ConnectivityManager cm =
              (ConnectivityManager)context.getSystemService(
                      Context.CONNECTIVITY_SERVICE);

      NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      if (activeNetwork != null){
         return activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
      }
      else { return false; }
   }
}
