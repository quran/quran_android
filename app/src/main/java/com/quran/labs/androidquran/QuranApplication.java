package com.quran.labs.androidquran;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.util.ApiKeys;
import com.quran.labs.androidquran.util.QuranCrashListener;
import net.hockeyapp.android.CrashManager;

import java.util.Locale;

/**
 * Created by ahmedre on 8/3/13.
 */
public class QuranApplication extends Application {

  @Override
  public void onCreate() {
    super.onCreate();
    if (Constants.CRASH_REPORTING_ENABLED) {
      CrashManager.register(this, ApiKeys.HOCKEY_APP_KEY,
          QuranCrashListener.getInstance(this));
    }
    refreshLocale(false);
  }

  public void refreshLocale(boolean force) {
    SharedPreferences prefs = PreferenceManager
        .getDefaultSharedPreferences(this);
    String language = prefs.getBoolean(
        Constants.PREF_USE_ARABIC_NAMES, false) ? "ar" : null;

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
