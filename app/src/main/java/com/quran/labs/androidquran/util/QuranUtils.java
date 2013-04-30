package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.quran.labs.androidquran.data.Constants;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class QuranUtils {
   private static boolean mIsArabicFormatter = false;
   private static NumberFormat mNumberFormatter;
    
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

   public static String getLocalizedNumber(Context context, int number){
      if (QuranSettings.isArabicNames(context)){
         if (mNumberFormatter == null || !mIsArabicFormatter){
            mIsArabicFormatter = true;
            mNumberFormatter =
                    DecimalFormat.getIntegerInstance(new Locale("ar"));
         }
      }
      else {
         if (mNumberFormatter == null || mIsArabicFormatter){
            mIsArabicFormatter = false;
            mNumberFormatter =
                    DecimalFormat.getIntegerInstance();
         }
      }

      return mNumberFormatter.format(number);
   }

   public static boolean isDualPages(Context context){
      if (Constants.IS_TABLET_ENABLED){
         return context.getResources().getConfiguration().orientation ==
                 Configuration.ORIENTATION_LANDSCAPE;
      }
      return false;
   }
}
