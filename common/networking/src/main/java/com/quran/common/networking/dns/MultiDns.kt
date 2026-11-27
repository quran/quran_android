package com.quran.common.networking.dns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException

class MultiDns(
  private val servers: List<Dns>,
  private val fallbackServers: List<Dns> = emptyList(),
  private val staggerDelayMs: Long = DEFAULT_STAGGER_DELAY_MS,
) : Dns {

  override fun lookup(hostname: String): MutableList<InetAddress> {
    if (servers.isEmpty() && fallbackServers.isEmpty()) {
      throw UnknownHostException("All DNS servers failed for hostname: $hostname")
    }

    if (servers.size == 1 && fallbackServers.isEmpty()) {
      return try {
        lookupNonEmpty(servers[0], hostname)
      } catch (e: UnknownHostException) {
        throw e
      } catch (t: Throwable) {
        UnknownHostException("All DNS servers failed for hostname: $hostname").apply {
          addSuppressed(t)
        }.let { throw it }
      }
    }

    return runBlocking {
      val exceptions = mutableListOf<Throwable>()
      lookupStaggered(servers, hostname, exceptions)?.let { return@runBlocking it }
      lookupStaggered(fallbackServers, hostname, exceptions)?.let { return@runBlocking it }

      val primaryException = UnknownHostException("All DNS servers failed for hostname: $hostname")
      exceptions.forEach(primaryException::addSuppressed)
      throw primaryException
    }
  }

  private suspend fun lookupStaggered(
    candidates: List<Dns>,
    hostname: String,
    exceptions: MutableList<Throwable>,
  ): MutableList<InetAddress>? {
    if (candidates.isEmpty()) return null

    val start = System.currentTimeMillis()
    val remaining = mutableListOf<Pair<Deferred<Result<MutableList<InetAddress>>>, Dns>>()
    var primaryTask: Pair<Deferred<Result<MutableList<InetAddress>>>, Dns>? = null

    try {
      val primaryServer = candidates[0]
      val task = primaryDnsScope.async {
        runCatching { lookupNonEmpty(primaryServer, hostname) }
      } to primaryServer
      primaryTask = task

      val earlyPrimaryResult = if (candidates.size == 1) {
        task.first.await()
      } else {
        withTimeoutOrNull(staggerDelayMs) {
          task.first.await()
        }
      }

      if (earlyPrimaryResult != null) {
        earlyPrimaryResult.onSuccess { successResult ->
          Timber.d(
            "DNS got fast result from $primaryServer for $hostname in ${System.currentTimeMillis() - start}ms"
          )
          return successResult
        }.onFailure { exception ->
          exceptions.add(exception)
        }
      } else {
        remaining.add(task)
      }

      for (i in 1 until candidates.size) {
        val dns = candidates[i]
        val deferred = fallbackDnsScope.async {
          runCatching { lookupNonEmpty(dns, hostname) }
        }
        remaining.add(deferred to dns)
      }

      while (remaining.isNotEmpty()) {
        val (result, completedDeferred, provider) = select<
            Triple<Result<MutableList<InetAddress>>, Deferred<Result<MutableList<InetAddress>>>, Dns>
        > {
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
          return successResult
        }.onFailure { exception ->
          exceptions.add(exception)
        }
      }

      return null
    } finally {
      primaryTask?.first?.cancel()
      remaining.forEach { it.first.cancel() }
    }
  }

  private fun lookupNonEmpty(dns: Dns, hostname: String): MutableList<InetAddress> {
    val result = dns.lookup(hostname)
    if (result.isEmpty()) {
      throw UnknownHostException("Empty DNS response from $dns for hostname: $hostname")
    }
    return result.toMutableList()
  }

  companion object {
    private const val DEFAULT_STAGGER_DELAY_MS = 150L
    private val primaryDnsDispatcher = Dispatchers.IO.limitedParallelism(8)
    private val fallbackDnsDispatcher = Dispatchers.IO.limitedParallelism(16)
    private val primaryDnsScope = CoroutineScope(SupervisorJob() + primaryDnsDispatcher)
    private val fallbackDnsScope = CoroutineScope(SupervisorJob() + fallbackDnsDispatcher)
  }
}
