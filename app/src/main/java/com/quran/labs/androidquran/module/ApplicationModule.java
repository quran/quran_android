package com.quran.labs.androidquran.module;

import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.squareup.okhttp.OkHttpClient;

import android.app.Application;
import android.content.Context;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {
  private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
  private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 15;

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

  @Provides
  @Singleton
  OkHttpClient provideOkHttpClient() {
    OkHttpClient client = new OkHttpClient();
    client.setReadTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    client.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return client;
  }
}
