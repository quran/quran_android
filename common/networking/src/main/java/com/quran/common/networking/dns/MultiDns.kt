package com.quran.common.networking.dns

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class MultiDns(private val servers: List<Dns>) : Dns {

  override fun lookup(hostname: String): MutableList<InetAddress> {
    var lastException: Exception? = null
    for (i in 0 until servers.size) {
      try {
        return servers[i].lookup(hostname)
      } catch (unknownHostException: UnknownHostException) {
        lastException = unknownHostException
      }
    }

    throw lastException!!
  }
}
