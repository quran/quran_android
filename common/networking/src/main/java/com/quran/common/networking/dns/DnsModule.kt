package com.quran.common.networking.dns

import dagger.Module
import dagger.Provides
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
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

    val googleDns = provideGoogleDns(bootstrapClient)
    return if (googleDns != null) {
      listOf(Dns.SYSTEM, googleDns)
    } else { listOf(Dns.SYSTEM) }
  }

  private fun provideGoogleDns(bootstrapClient: OkHttpClient): Dns? {
    return try {
      DnsOverHttps.Builder()
          .client(bootstrapClient)
          .url(HttpUrl.get("https://dns.google.com/experimental"))
          .bootstrapDnsHosts(InetAddress.getByName("216.58.204.78"),
              InetAddress.getByName("2a00:1450:4009:814:0:0:0:200e"))
          .build()
    } catch (exception: UnknownHostException) {
      null
    }
  }
}
