package com.quran.labs.androidquran;

import com.facebook.stetho.Stetho;
import com.squareup.leakcanary.LeakCanary;

import android.os.Build;

import timber.log.Timber;

public class DebugApplication extends QuranApplication {

  @Override
  public void onCreate() {
    super.onCreate();

    Timber.plant(new Timber.DebugTree());
    Stetho.initializeWithDefaults(this);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      LeakCanary.install(this);
    }
  }
}
