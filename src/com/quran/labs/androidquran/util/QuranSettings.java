package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Build;
import android.preference.PreferenceManager;
import com.quran.labs.androidquran.data.Constants;


public class QuranSettings {

   public static boolean isArabicNames(Context context){
      return getBooleanPreference(context,
              Constants.PREF_USE_ARABIC_NAMES, false);
   }

   public static boolean isLockOrientation(Context context){
      return getBooleanPreference(context,
              Constants.PREF_LOCK_ORIENTATION, false);
   }

   public static boolean isLandscapeOrientation(Context context){
      return getBooleanPreference(context,
              Constants.PREF_LANDSCAPE_ORIENTATION, false);
   }

   public static boolean isReshapeArabic(Context context){
      return Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH;
   }

   public static boolean isNightMode(Context context){
      return getBooleanPreference(context,
              Constants.PREF_NIGHT_MODE, false);
   }

   public static boolean shouldDisplayMarkerPopup(Context context){
      return getBooleanPreference(context,
              Constants.PREF_DISPLAY_MARKER_POPUP, true);
   }

   public static boolean wantArabicInTranslationView(Context context){
      return getBooleanPreference(context,
              Constants.PREF_AYAH_BEFORE_TRANSLATION, true);
   }

   private static boolean getBooleanPreference(Context context,
                                               String pref,
                                               boolean defaultValue){
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      return prefs.getBoolean(pref, defaultValue);
   }

   public static int getTranslationTextSize(Context context){
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      return prefs.getInt(Constants.PREF_TRANSLATION_TEXT_SIZE,
              Constants.DEFAULT_TEXT_SIZE);
   }

   public static void setLastPage(Context context, int page){
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      prefs.edit().putInt(Constants.PREF_LAST_PAGE, page).commit();
   }
}
