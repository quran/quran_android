package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.common.LocalTranslation;

import android.content.Context;

import java.util.List;

public class TranslationUtils {

  public static String getDefaultTranslation(Context context, List<LocalTranslation> items) {
    final LocalTranslation item = getDefaultTranslationItem(context, items);
    return item == null ? null : item.filename;
  }

  public static LocalTranslation getDefaultTranslationItem(Context context,
      List<LocalTranslation> items) {
    if (items == null || items.size() == 0) {
      return null;
    }
    QuranSettings settings = QuranSettings.getInstance(context.getApplicationContext());
    final String db = settings.getActiveTranslation();

    LocalTranslation result = null;
    boolean changed = false;
    if (db == null) {
      changed = true;
      result = items.get(0);
    } else {
      boolean found = false;
      for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
        LocalTranslation item = items.get(i);
        if (item.filename.equals(db)) {
          found = true;
          result = item;
          break;
        }
      }

      if (!found) {
        changed = true;
        result = items.get(0);
      }
    }

    if (changed && result != null) {
      settings.setActiveTranslation(result.filename);
    }

    return result;
  }
}
