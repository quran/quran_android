package com.quran.labs.androidquran;

import com.facebook.stetho.Stetho;
import com.quran.labs.androidquran.component.application.ApplicationComponent;
import com.quran.labs.androidquran.component.application.DaggerDebugApplicationComponent;
import com.quran.labs.androidquran.module.application.ApplicationModule;
import com.squareup.leakcanary.LeakCanary;

import timber.log.Timber;

public class DebugApplication extends QuranApplication {

  @Override
  public void onCreate() {
    super.onCreate();

    if (!LeakCanary.isInAnalyzerProcess(this)) {
      Timber.plant(new Timber.DebugTree());
      Stetho.initializeWithDefaults(this);
      LeakCanary.install(this);
    }
  }

  @Override
  protected ApplicationComponent initializeInjector() {
    return DaggerDebugApplicationComponent.builder()
        .applicationModule(new ApplicationModule(this))
        .build();
  }
}
