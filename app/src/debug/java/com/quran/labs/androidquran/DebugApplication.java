package com.quran.labs.androidquran;

import com.facebook.stetho.Stetho;
import com.squareup.leakcanary.LeakCanary;

import android.os.Build;

public class DebugApplication extends QuranApplication {

  @Override
  public void onCreate() {
    super.onCreate();

    Stetho.initializeWithDefaults(this);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      LeakCanary.install(this);
    }
  }
}
