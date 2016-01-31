package com.quran.labs.androidquran.module;

import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import android.app.Application;
import android.content.Context;

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
  @Singleton
  Context provideApplicationContext() {
    return this.application;
  }

  @Provides
  @Singleton
  BookmarksDBAdapter provideBookmarkDatabaseAdapter(Context context) {
    return new BookmarksDBAdapter(context);
  }
}
