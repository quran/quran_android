package com.quran.labs.androidquran.module.application;

import com.facebook.stetho.okhttp3.StethoInterceptor;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

@Module
public class DebugNetworkModule {

  private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
  private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 15;

  @Provides
  @Singleton
  static OkHttpClient provideOkHttpClient() {
    return new OkHttpClient.Builder()
        .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addNetworkInterceptor(new StethoInterceptor())
        .build();
  }
}
