package com.quran.common.networking.dns

import okhttp3.Dns
import org.xbill.DNS.Address
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import java.net.InetAddress

class DnsFallback : Dns {

  override fun lookup(hostname: String): MutableList<InetAddress> {
    ensureInitialized()
    return Address.getAllByName(hostname).toMutableList()
  }

  companion object {
    private const val RESOLVER_TIMEOUT_SECONDS = 3

    @Volatile
    private var initialized = false
    private val initLock = Any()

    internal fun ensureInitialized(
      configureResolver: () -> Unit = ::configureDefaultResolver
    ) {
      if (!initialized) {
        synchronized(initLock) {
          if (!initialized) {
            configureResolver()
            initialized = true
          }
        }
      }
    }

    internal fun resetForTest() {
      synchronized(initLock) {
        initialized = false
      }
    }

    private fun configureDefaultResolver() {
      val googleResolver = SimpleResolver("8.8.8.8").apply {
        setTimeout(RESOLVER_TIMEOUT_SECONDS)
      }
      val cloudflareResolver = SimpleResolver("1.1.1.1").apply {
        setTimeout(RESOLVER_TIMEOUT_SECONDS)
      }
      Lookup.setDefaultResolver(ExtendedResolver(arrayOf(googleResolver, cloudflareResolver)))
    }
  }
}
