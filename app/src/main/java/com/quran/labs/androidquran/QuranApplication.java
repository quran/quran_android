package com.quran.labs.androidquran;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.util.QuranSettings;
import com.squareup.leakcanary.LeakCanary;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

import io.fabric.sdk.android.Fabric;

public class QuranApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    Fabric.with(this, new Crashlytics());
    LeakCanary.install(this);
    refreshLocale(false);
  }

  public void refreshLocale(boolean force) {
    String language = QuranSettings.getInstance(this).isArabicNames() ? "ar" : null;

    Locale locale = null;
    if ("ar".equals(language)) {
      locale = new Locale("ar");
    } else if (force) {
      // get the system locale (since we overwrote the default locale)
      locale = Resources.getSystem().getConfiguration().locale;
    }

    if (locale != null) {
      final Resources resources = getResources();
      Configuration config = resources.getConfiguration();
      config.locale = locale;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        config.setLayoutDirection(config.locale);
      }
      resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
  }
}
