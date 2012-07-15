package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.SharedPreferences;

import android.preference.PreferenceManager;
import com.quran.labs.androidquran.data.Constants;


public class QuranSettings {

   public static boolean isArabicNames(Context context){
      return getBooleanPreference(context, Constants.PREF_USE_ARABIC_NAMES);
   }

   public static boolean isLockOrientation(Context context){
      return getBooleanPreference(context, Constants.PREF_LOCK_ORIENTATION);
   }

   public static boolean isLandscapeOrientation(Context context){
      return getBooleanPreference(context, Constants.PREF_LANDSCAPE_ORIENTATION);
   }

   public static boolean isReshapeArabic(Context context){
      return getBooleanPreference(context, Constants.PREF_RESHAPE_ARABIC);
   }

   private static boolean getBooleanPreference(Context context, String pref){
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      return prefs.getBoolean(pref, false);
   }

   public static void setLastPage(Context context, int page){
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      prefs.edit().putInt(Constants.PREF_LAST_PAGE, page).commit();
   }
}
