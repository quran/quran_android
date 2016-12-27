package com.quran.labs.androidquran.module.application;

import android.app.Application;
import android.content.Context;

import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {
  private final Application application;

  public ApplicationModule(Application application) {
    this.application = application;
  }

  @Provides
  Context provideApplicationContext() {
    return this.application;
  }

  @Provides
  @Singleton
  QuranSettings provideQuranSettings() {
    return QuranSettings.getInstance(application);
  }

  @Provides
  @Singleton
  QuranScreenInfo provideQuranScreenInfo() {
    return QuranScreenInfo.getOrMakeInstance(application);
  }
}
