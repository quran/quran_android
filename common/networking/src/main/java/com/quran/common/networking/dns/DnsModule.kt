package com.quran.common.networking.dns

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@BindingContainer
class DnsModule {

  @Provides
  fun providesDns(servers: List<@JvmSuppressWildcards Dns>): Dns {
    return MultiDns(servers, listOf(DnsFallback()))
  }

  @Provides
  fun provideDnsCache(cacheDirectory: File): Cache {
    return Cache(File(cacheDirectory, "dns_cache"), 5 * 1024 * 1024L)
  }

  @Provides
  fun provideServers(dnsCache: Cache): List<Dns> {
    val bootstrapClient = OkHttpClient.Builder()
        .cache(dnsCache)
        .connectTimeout(BOOTSTRAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BOOTSTRAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(BOOTSTRAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(BOOTSTRAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    return listOfNotNull(Dns.SYSTEM, provideCloudflareDns(bootstrapClient))
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

  companion object {
    private const val BOOTSTRAP_TIMEOUT_SECONDS = 5L
  }
}
