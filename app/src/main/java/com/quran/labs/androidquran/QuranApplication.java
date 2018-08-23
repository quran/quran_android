package com.quran.labs.androidquran;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import androidx.annotation.NonNull;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.quran.labs.androidquran.component.activity.PagerActivityComponent;
import com.quran.labs.androidquran.component.application.DaggerApplicationComponent;
import com.quran.labs.androidquran.component.application.ApplicationComponent;
import com.quran.labs.androidquran.module.application.ApplicationModule;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.RecordingLogTree;

import java.util.Locale;

import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public class QuranApplication extends Application {
  private ApplicationComponent applicationComponent;
  private PagerActivityComponent pagerActivityComponent;

  @Override
  public void onCreate() {
    super.onCreate();
    Fabric.with(this, new Crashlytics.Builder()
        .core(new CrashlyticsCore.Builder()
            .disabled(BuildConfig.DEBUG)
            .build())
        .build());
    Timber.plant(new RecordingLogTree());
    this.applicationComponent = initializeInjector();
  }

  protected ApplicationComponent initializeInjector() {
    return DaggerApplicationComponent.builder()
        .applicationModule(new ApplicationModule(this))
        .build();
  }

  public ApplicationComponent getApplicationComponent() {
    return this.applicationComponent;
  }

  public void refreshLocale(@NonNull Context context, boolean force) {
    final String language = QuranSettings.getInstance(this).isArabicNames() ? "ar" : null;

    final Locale locale;
    if ("ar".equals(language)) {
      locale = new Locale("ar");
    } else if (force) {
      // get the system locale (since we overwrote the default locale)
      locale = Resources.getSystem().getConfiguration().locale;
    } else {
      // nothing to do...
      return;
    }

    updateLocale(context, locale);
    final Context appContext = context.getApplicationContext();
    if (context != appContext) {
      updateLocale(appContext, locale);
    }
  }

  private void updateLocale(@NonNull Context context, @NonNull Locale locale) {
    final Resources resources = context.getResources();
    Configuration config = resources.getConfiguration();
    config.locale = locale;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      config.setLayoutDirection(config.locale);
    }
    resources.updateConfiguration(config, resources.getDisplayMetrics());
  }

  public PagerActivityComponent getPagerActivityComponent() {
    return pagerActivityComponent;
  }
}
