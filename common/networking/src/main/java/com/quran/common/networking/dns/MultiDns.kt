package com.quran.common.networking.dns

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class MultiDns(private val servers: List<Dns>) : Dns {

  override fun lookup(hostname: String): MutableList<InetAddress> {
    var lastException: Exception? = null
    for (i in servers.indices) {
      try {
        return servers[i].lookup(hostname).toMutableList()
      } catch (unknownHostException: UnknownHostException) {
        lastException = unknownHostException
      }
    }

    throw lastException!!
  }
}
