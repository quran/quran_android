package com.quran.common.networking.dns

import com.google.common.truth.Truth.assertThat
import okhttp3.Cache
import okhttp3.Dns
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger

class MultiDnsTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val primaryAddress = InetAddress.getByAddress("example.com", byteArrayOf(1, 2, 3, 4))
  private val fallbackAddress = InetAddress.getByAddress("example.com", byteArrayOf(5, 6, 7, 8))

  private class FakeDns(
    private val delayMs: Long = 0L,
    private val result: List<InetAddress>? = null,
    private val failure: Throwable? = null
  ) : Dns {
    val calls = AtomicInteger(0)

    override fun lookup(hostname: String): List<InetAddress> {
      calls.incrementAndGet()
      if (delayMs > 0) {
        Thread.sleep(delayMs)
      }
      failure?.let { throw it }
      return result ?: throw UnknownHostException(hostname)
    }
  }

  @Test
  fun `primary success does not invoke fallback resolver`() {
    val slowPrimary = FakeDns(delayMs = 50L, result = listOf(primaryAddress))
    val failingPrimary = FakeDns(failure = UnknownHostException("primary-1 failed"))
    val fallback = FakeDns(result = listOf(fallbackAddress))
    val multiDns = MultiDns(
      servers = listOf(slowPrimary, failingPrimary),
      fallbackServers = listOf(fallback)
    )

    val resolved = multiDns.lookup("example.com")

    assertThat(resolved).containsExactly(primaryAddress)
    assertThat(fallback.calls.get()).isEqualTo(0)
  }

  @Test
  fun `all primary failures invoke fallback resolver`() {
    val primary1 = FakeDns(failure = UnknownHostException("primary-1 failed"))
    val primary2 = FakeDns(failure = UnknownHostException("primary-2 failed"))
    val fallback = FakeDns(result = listOf(fallbackAddress))
    val multiDns = MultiDns(
      servers = listOf(primary1, primary2),
      fallbackServers = listOf(fallback)
    )

    val resolved = multiDns.lookup("example.com")

    assertThat(resolved).containsExactly(fallbackAddress)
    assertThat(primary1.calls.get()).isEqualTo(1)
    assertThat(primary2.calls.get()).isEqualTo(1)
    assertThat(fallback.calls.get()).isEqualTo(1)
  }

  @Test
  fun `all primary and fallback failures throw UnknownHostException with suppressed errors`() {
    val primary = FakeDns(failure = UnknownHostException("primary failed"))
    val fallback = FakeDns(failure = UnknownHostException("fallback failed"))
    val multiDns = MultiDns(
      servers = listOf(primary),
      fallbackServers = listOf(fallback)
    )

    val error = try {
      multiDns.lookup("example.com")
      null
    } catch (e: UnknownHostException) {
      e
    }

    assertThat(error).isNotNull()
    assertThat(error!!.suppressed).hasLength(2)
  }

  @Test
  fun `DnsModule provideServers excludes DnsFallback from primary list`() {
    val cache = Cache(tempFolder.newFolder("dns-cache"), 1024L * 1024L)
    val servers = DnsModule().provideServers(cache)

    assertThat(servers).isNotEmpty()
    assertThat(servers.none { it is DnsFallback }).isTrue()
    cache.close()
  }
}
