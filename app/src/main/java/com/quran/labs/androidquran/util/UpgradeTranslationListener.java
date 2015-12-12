package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.task.TranslationListTask;

import android.content.Context;
import android.support.annotation.NonNull;

import java.util.List;

import timber.log.Timber;

public class UpgradeTranslationListener implements TranslationListTask.TranslationsUpdatedListener {

  @NonNull private final Context mAppContext;

  public UpgradeTranslationListener(@NonNull Context context) {
    mAppContext = context.getApplicationContext();
  }

  @Override
  public void translationsUpdated(List<TranslationItem> items) {
    if (items == null){ return; }

    boolean needsUpgrade = false;
    for (TranslationItem item : items){
      if (item.exists && item.localVersion != null &&
          item.latestVersion > 0 &&
          item.latestVersion > item.localVersion){
        needsUpgrade = true;
        break;
      }
    }

    Timber.d("done checking translations - " +
        (needsUpgrade ? "" : "no ") + "upgrade needed");
    QuranSettings.getInstance(mAppContext).setHaveUpdatedTranslations(needsUpgrade);
  }
}
