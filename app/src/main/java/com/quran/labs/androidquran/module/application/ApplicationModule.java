package com.quran.labs.androidquran.module.application;

import android.app.Application;
import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

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
  Display provideDisplay(Context appContext) {
    final WindowManager w = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
    return w.getDefaultDisplay();
  }

  @Provides
  @Singleton
  QuranSettings provideQuranSettings() {
    return QuranSettings.getInstance(application);
  }
}
