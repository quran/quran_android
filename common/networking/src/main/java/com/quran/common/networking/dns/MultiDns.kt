package com.quran.common.networking.dns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

      val job = SupervisorJob()
      val externalScope = CoroutineScope(Dispatchers.IO + job)

      val tasks: List<Pair<Deferred<Result<MutableList<InetAddress>>>, Dns>> =
        servers.map { dns ->
          externalScope.async {
            runCatching { dns.lookup(hostname).toMutableList() }
          } to dns
        }

      try {
        val remaining = tasks.toMutableList()
        val exceptions = mutableListOf<Throwable>()

        while (remaining.isNotEmpty()) {
          val (result, completedDeferred, provider) = select<Triple<Result<MutableList<InetAddress>>, Deferred<Result<MutableList<InetAddress>>>, Dns>> {
            remaining.forEach { (deferred, dns) ->
              deferred.onAwait { answer ->
                Timber.d("DNS server $dns responded for $hostname in ${System.currentTimeMillis() - start}ms")
                Triple(answer, deferred, dns)
              }
            }
          }

          remaining.removeAll { it.first == completedDeferred }

          result.onSuccess { successResult ->
            Timber.d("DNS got result from $provider; cancelling others and returning")
            job.cancel()
            return@runBlocking successResult
          }.onFailure { exception ->
            exceptions.add(exception)
          }
        }

        val primaryException = UnknownHostException("All DNS servers failed for hostname: $hostname")
        exceptions.forEach(primaryException::addSuppressed)
        throw primaryException
      } finally {
        job.cancel()
      }
    }
  }
}
