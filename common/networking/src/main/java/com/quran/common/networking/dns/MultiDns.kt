package com.quran.common.networking.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import okhttp3.Dns
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException

class MultiDns(private val servers: List<Dns>) : Dns {

  override fun lookup(hostname: String): MutableList<InetAddress> {
    return runBlocking {
      val start = System.currentTimeMillis()
      coroutineScope {
        val deferreds = servers.map { dns ->
          async(Dispatchers.IO) { runCatching { dns.lookup(hostname).toMutableList() } }
        }

        val remaining = deferreds.toMutableList()
        val exceptions = mutableListOf<Throwable>()

        while (remaining.isNotEmpty()) {
          val (result, completedDeferred) = select<Pair<Result<MutableList<InetAddress>>, *>> {
            remaining.withIndex().forEach { (index, deferred) ->
              deferred.onAwait { answer ->
                Timber.d("DNS server ${servers[index]} responded for $hostname in ${System.currentTimeMillis() - start}ms")
                answer to deferred }
            }
          }

          remaining.remove(completedDeferred)
          result.onSuccess { successResult ->
            // first one to succeed wins
            remaining.forEach { it.cancel() }
            return@coroutineScope successResult
          }.onFailure { exception ->
            // This one failed, let's wait for the others
            exceptions.add(exception)
          }
        }

        // all lookups failed
        val primaryException = UnknownHostException("All DNS servers failed for hostname: $hostname")
        exceptions.forEach { primaryException.addSuppressed(it) }
        throw primaryException
      }
    }
  }
}
