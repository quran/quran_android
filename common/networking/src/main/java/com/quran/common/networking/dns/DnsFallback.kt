package com.quran.common.networking.dns

import okhttp3.Dns
import org.xbill.DNS.Address
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import java.net.InetAddress

class DnsFallback : Dns {
  private var initialized = false

  override fun lookup(hostname: String): MutableList<InetAddress> {
    if (!initialized) {
      val googleResolver = SimpleResolver("8.8.8.8")
      val cloudflareResolver = SimpleResolver("1.1.1.1")
      Lookup.setDefaultResolver(ExtendedResolver(arrayOf(googleResolver, cloudflareResolver)))
      initialized = true
    }

    return Address.getAllByName(hostname).toMutableList()
  }
}
