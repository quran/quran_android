package com.quran.labs.androidquran;

import com.crashlytics.android.Crashlytics;
import com.quran.labs.androidquran.util.QuranSettings;
import com.squareup.leakcanary.LeakCanary;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;

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
      Locale.setDefault(locale);
      Configuration config = getResources().getConfiguration();
      config.locale = locale;
      getResources().updateConfiguration(config,
          getResources().getDisplayMetrics());
    }
  }
}
