package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.data.Constants;

import java.util.List;

public class TranslationUtils {

   public static String getDefaultTranslation(Context context,
                                       List<TranslationItem> items){
      if (items == null || items.size() == 0){ return null; }
      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(context);
      String db = prefs.getString(Constants.PREF_ACTIVE_TRANSLATION, null);

      boolean changed = false;
      if (db == null){
         changed = true;
         db = items.get(0).filename;
      }
      else {
         boolean found = false;
         for (TranslationItem item : items){
            if (item.filename.equals(db)){
               found = true;
               break;
            }
         }

         if (!found){
            changed = true;
            db = items.get(0).filename;
         }
      }

      if (changed && db != null){
         prefs.edit().putString(Constants.PREF_ACTIVE_TRANSLATION, db)
                 .commit();
      }

      return db;
   }
}
