package com.quran.common.networking.dns

import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.UnknownHostException

@Module
class DnsModule {

  @Provides
  fun providesDns(servers: List<@JvmSuppressWildcards Dns>): Dns {
    return MultiDns(servers)
  }

  @Provides
  fun provideDnsCache(cacheDirectory: File): Cache {
    return Cache(cacheDirectory, 5 * 1024 * 1024L)
  }

  @Provides
  fun provideServers(dnsCache: Cache): List<Dns> {
    val bootstrapClient = OkHttpClient.Builder()
        .cache(dnsCache)
        .build()

    return listOfNotNull(Dns.SYSTEM, DnsFallback(), provideCloudflareDns(bootstrapClient))
  }

  private fun provideCloudflareDns(bootstrapClient: OkHttpClient): Dns? {
    return try {
      DnsOverHttps.Builder()
          .client(bootstrapClient)
          .url("https://1.1.1.1/dns-query".toHttpUrl())
          .build()
    } catch (_: UnknownHostException) {
      null
    }
  }
}
