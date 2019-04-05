package com.quran.labs.androidquran.module.application;

import com.facebook.stetho.okhttp3.StethoInterceptor;
import com.quran.common.networking.dns.DnsModule;

import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import okhttp3.Dns;
import okhttp3.OkHttpClient;

@Module(includes = { DnsModule.class })
public class DebugNetworkModule {

  private static final int DEFAULT_READ_TIMEOUT_SECONDS = 20;
  private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 15;

  @Provides
  @Singleton
  static OkHttpClient provideOkHttpClient(Dns dns) {
    return new OkHttpClient.Builder()
        .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addNetworkInterceptor(new StethoInterceptor())
        .dns(dns)
        .build();
  }
}
