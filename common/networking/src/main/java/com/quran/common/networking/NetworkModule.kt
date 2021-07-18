package com.quran.common.networking

import com.quran.common.networking.dns.DnsModule
import dagger.Module
import dagger.Provides
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module(includes = [DnsModule::class])
object NetworkModule {
  private const val DEFAULT_READ_TIMEOUT_SECONDS = 20
  private const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 20

  @Provides
  @Singleton
  fun provideOkHttpClient(dns: Dns): OkHttpClient {
    return OkHttpClient.Builder()
      .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
      .dns(dns)
      .build()
  }
}
