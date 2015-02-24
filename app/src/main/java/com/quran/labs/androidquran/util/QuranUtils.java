package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.preference.PreferenceManager;

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

   public static boolean isOnWifiNetwork(Context context) {
     ConnectivityManager cm =
         (ConnectivityManager) context.getSystemService(
             Context.CONNECTIVITY_SERVICE);

     NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
     return activeNetwork != null &&
         activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
   }

  public static boolean haveInternet(Context context) {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
        Context.CONNECTIVITY_SERVICE);
    return cm != null && cm.getActiveNetworkInfo() != null &&
        cm.getActiveNetworkInfo()
            .isConnectedOrConnecting();
  }

   public static String getLocalizedNumber(Context context, int number){
      if (QuranSettings.isArabicNames(context)){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
          // TODO: fix this to take a number directly
          return ArabicStyle.legacyGetArabicNumbers("" + number);
        }

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

   public static boolean isDualPages(Context context, QuranScreenInfo qsi){
      if (context != null && qsi != null){
        final Resources resources = context.getResources();
        if (qsi.isTablet(context) &&
            resources.getConfiguration().orientation ==
                Configuration.ORIENTATION_LANDSCAPE){
            final SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getBoolean(Constants.PREF_TABLET_ENABLED,
                 resources.getBoolean(R.bool.use_tablet_interface_by_default));
        }
      }
      return false;
   }

  /*
  public static String getDebugInfo(Context context){
    StringBuilder builder = new StringBuilder();
    QuranScreenInfo info = QuranScreenInfo.getInstance();
    if (info != null){
      builder.append("\nDisplay: ").append(info.getWidthParam());
      if (info.isTablet(context)){
        builder.append(", tablet width: ").append(info.getWidthParam());
      }
      builder.append("\n");

      if (QuranFileUtils.haveAllImages(
          context, info.getWidthParam())){
        builder.append("all images found for ").
            append(info.getWidthParam()).append("\n");
      }

      if (info.isTablet(context) &&
          QuranFileUtils.haveAllImages(context,
              info.getTabletWidthParam())){
        builder.append("all tablet images found for ")
            .append(info.getTabletWidthParam()).append("\n");
      }
    }

    int memClass = ((ActivityManager)context
        .getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
    builder.append("memory class: ").append(memClass).append("\n\n");
    return builder.toString();
  }
  */
}
