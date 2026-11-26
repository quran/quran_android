package com.quran.common.networking.dns

import com.google.common.truth.Truth.assertThat
import okhttp3.Cache
import okhttp3.Dns
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MultiDnsTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val primaryIp = InetAddress.getByAddress("example.com", byteArrayOf(1, 2, 3, 4))
  private val fallbackIp = InetAddress.getByAddress("example.com", byteArrayOf(5, 6, 7, 8))

  @Test
  fun `primary success does not invoke fallback resolver`() {
    val fallbackCalls = AtomicInteger(0)
    val slowPrimary = FakeDns {
      Thread.sleep(50L)
      listOf(primaryIp)
    }
    val failingPrimary = FakeDns { throw UnknownHostException("primary-1 failed") }
    val fallback = FakeDns {
      fallbackCalls.incrementAndGet()
      listOf(fallbackIp)
    }
    val multiDns = MultiDns(
      servers = listOf(slowPrimary, failingPrimary),
      fallbackServers = listOf(fallback)
    )

    val resolved = multiDns.lookup("example.com")

    assertThat(resolved).containsExactly(primaryIp)
    assertThat(fallbackCalls.get()).isEqualTo(0)
  }

  @Test
  fun `all primary failures invoke fallback resolver`() {
    val p1Calls = AtomicInteger(0)
    val p2Calls = AtomicInteger(0)
    val fbCalls = AtomicInteger(0)
    val primary1 = FakeDns {
      p1Calls.incrementAndGet()
      throw UnknownHostException("primary-1 failed")
    }
    val primary2 = FakeDns {
      p2Calls.incrementAndGet()
      throw UnknownHostException("primary-2 failed")
    }
    val fallback = FakeDns {
      fbCalls.incrementAndGet()
      listOf(fallbackIp)
    }
    val multiDns = MultiDns(
      servers = listOf(primary1, primary2),
      fallbackServers = listOf(fallback)
    )

    val resolved = multiDns.lookup("example.com")

    assertThat(resolved).containsExactly(fallbackIp)
    assertThat(p1Calls.get()).isEqualTo(1)
    assertThat(p2Calls.get()).isEqualTo(1)
    assertThat(fbCalls.get()).isEqualTo(1)
  }

  @Test
  fun `all primary and fallback failures throw UnknownHostException with suppressed errors`() {
    val primary = FakeDns { throw UnknownHostException("primary failed") }
    val fallback = FakeDns { throw UnknownHostException("fallback failed") }
    val multiDns = MultiDns(
      servers = listOf(primary),
      fallbackServers = listOf(fallback)
    )

    val error = assertThrows(UnknownHostException::class.java) {
      multiDns.lookup("example.com")
    }
    assertThat(error.suppressed).hasLength(2)
  }

  @Test
  fun `DnsModule provideServers excludes DnsFallback from primary list`() {
    val cache = Cache(tempFolder.newFolder("dns-cache"), 1024L * 1024L)
    val servers = DnsModule().provideServers(cache)

    assertThat(servers).isNotEmpty()
    assertThat(servers.none { it is DnsFallback }).isTrue()
    cache.close()
  }

  @Test
  fun `fast primary succeeds without invoking fallback servers`() {
    val fallbackCalls = AtomicInteger(0)
    val primary = FakeDns { listOf(primaryIp) }
    val fallback1 = FakeDns {
      fallbackCalls.incrementAndGet()
      listOf(fallbackIp)
    }
    val fallback2 = FakeDns {
      fallbackCalls.incrementAndGet()
      listOf(fallbackIp)
    }

    val dns = MultiDns(listOf(primary, fallback1, fallback2), staggerDelayMs = 200L)
    val result = dns.lookup("example.com")

    assertThat(result).containsExactly(primaryIp)
    assertThat(fallbackCalls.get()).isEqualTo(0)
  }

  @Test
  fun `fast primary failure invokes fallback immediately without waiting stagger window`() {
    val primary = FakeDns { throw UnknownHostException("primary failed") }
    val fallback = FakeDns { listOf(fallbackIp) }

    val dns = MultiDns(listOf(primary, fallback), staggerDelayMs = 5_000L)
    val startNanos = System.nanoTime()
    val result = dns.lookup("example.com")
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

    assertThat(result).containsExactly(fallbackIp)
    assertThat(elapsedMs).isLessThan(1_000L)
  }

  @Test
  fun `slow primary triggers fallback after stagger window and returns fastest result`() {
    val releasePrimary = CountDownLatch(1)
    val primary = FakeDns {
      releasePrimary.await(5, TimeUnit.SECONDS)
      listOf(primaryIp)
    }
    val fallback = FakeDns { listOf(fallbackIp) }

    val dns = MultiDns(listOf(primary, fallback), staggerDelayMs = 20L)
    val startNanos = System.nanoTime()
    val result = dns.lookup("example.com")
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
    releasePrimary.countDown()

    assertThat(result).containsExactly(fallbackIp)
    assertThat(elapsedMs).isLessThan(1_000L)
  }

  @Test
  fun `all failing servers throws UnknownHostException with suppressed exceptions`() {
    val e1 = UnknownHostException("err1")
    val e2 = UnknownHostException("err2")
    val dns = MultiDns(
      listOf(
        FakeDns { throw e1 },
        FakeDns { throw e2 },
      ),
      staggerDelayMs = 20L,
    )

    val thrown = assertThrows(UnknownHostException::class.java) {
      dns.lookup("example.com")
    }
    assertThat(thrown.suppressed.toList()).containsExactly(e1, e2)
  }

  @Test
  fun `empty servers list throws UnknownHostException`() {
    val dns = MultiDns(emptyList())
    assertThrows(UnknownHostException::class.java) {
      dns.lookup("example.com")
    }
  }

  @Test
  fun `single server returns result or propagates failure`() {
    val okDns = MultiDns(listOf(FakeDns { listOf(primaryIp) }))
    assertThat(okDns.lookup("example.com")).containsExactly(primaryIp)

    val failingDns = MultiDns(listOf(FakeDns { throw UnknownHostException("single failed") }))
    assertThrows(UnknownHostException::class.java) {
      failingDns.lookup("example.com")
    }
  }

  @Test
  fun `empty list from primary falls back to secondary`() {
    val primary = FakeDns { emptyList() }
    val fallback = FakeDns { listOf(fallbackIp) }

    val dns = MultiDns(listOf(primary, fallback), staggerDelayMs = 200L)
    assertThat(dns.lookup("example.com")).containsExactly(fallbackIp)

    val singleEmpty = MultiDns(listOf(primary))
    assertThrows(UnknownHostException::class.java) {
      singleEmpty.lookup("example.com")
    }
  }

  @Test
  fun `eight concurrent stalled primary lookups resolve via fallback without starvation`() {
    val releasePrimary = CountDownLatch(1)
    val primary = FakeDns {
      releasePrimary.await(5, TimeUnit.SECONDS)
      listOf(primaryIp)
    }
    val fallback = FakeDns { listOf(fallbackIp) }
    val dns = MultiDns(listOf(primary, fallback), staggerDelayMs = 30L)
    val executor = Executors.newFixedThreadPool(8)

    try {
      val startNanos = System.nanoTime()
      val futures = (1..8).map {
        executor.submit(Callable { dns.lookup("example.com") })
      }
      val results = futures.map { it.get(2, TimeUnit.SECONDS) }
      val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)

      results.forEach { ips ->
        assertThat(ips).containsExactly(fallbackIp)
      }
      assertThat(elapsedMs).isLessThan(1_500L)
    } finally {
      releasePrimary.countDown()
      executor.shutdownNow()
    }
  }

  private class FakeDns(
    private val handler: (String) -> List<InetAddress>,
  ) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = handler(hostname)
  }
}
