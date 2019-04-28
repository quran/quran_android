package com.quran.labs.androidquran;

import com.squareup.leakcanary.LeakCanary;

import timber.log.Timber;

public class DebugApplication extends QuranApplication {

  @Override
  public void onCreate() {
    super.onCreate();

    if (!LeakCanary.isInAnalyzerProcess(this)) {
      Timber.plant(new Timber.DebugTree());
      LeakCanary.install(this);
    }
  }
}
