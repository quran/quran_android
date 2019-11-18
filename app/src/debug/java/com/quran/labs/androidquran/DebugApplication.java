package com.quran.labs.androidquran;

import timber.log.Timber;

public class DebugApplication extends QuranApplication {

  @Override
  public void onCreate() {
    super.onCreate();
    Timber.plant(new Timber.DebugTree());
  }
}
