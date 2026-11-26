package com.quran.common.networking.dns

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.xbill.DNS.ExtendedResolver
import org.xbill.DNS.Lookup
import org.xbill.DNS.Resolver
import org.xbill.DNS.SimpleResolver
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DnsFallbackTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private var previousResolver: Resolver? = null

  @Before
  fun setUp() {
    previousResolver = Lookup.getDefaultResolver()
    DnsFallback.resetForTest()
  }

  @After
  fun tearDown() {
    DnsFallback.resetForTest()
    previousResolver?.let { Lookup.setDefaultResolver(it) }
  }

  @Test
  fun `concurrent ensureInitialized invokes resolver configuration only once`() {
    val threadCount = 16
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(threadCount)
    val invocations = AtomicInteger(0)
    val pool = Executors.newFixedThreadPool(threadCount)

    try {
      repeat(threadCount) {
        pool.submit {
          try {
            startLatch.await()
            DnsFallback.ensureInitialized {
              Thread.sleep(10L)
              invocations.incrementAndGet()
            }
          } finally {
            doneLatch.countDown()
          }
        }
      }

      startLatch.countDown()
      assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(invocations.get()).isEqualTo(1)
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun `concurrent lookup safely initializes default ExtendedResolver`() {
    val threadCount = 8
    val startLatch = CountDownLatch(1)
    val doneLatch = CountDownLatch(threadCount)
    val fallback = DnsFallback()
    val pool = Executors.newFixedThreadPool(threadCount)
    val failures = AtomicInteger(0)

    try {
      repeat(threadCount) {
        pool.submit {
          try {
            startLatch.await()
            val result = fallback.lookup("127.0.0.1")
            if (result != listOf(InetAddress.getByName("127.0.0.1"))) {
              failures.incrementAndGet()
            }
          } catch (_: Throwable) {
            failures.incrementAndGet()
          } finally {
            doneLatch.countDown()
          }
        }
      }

      startLatch.countDown()
      assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(failures.get()).isEqualTo(0)
      assertThat(Lookup.getDefaultResolver()).isInstanceOf(ExtendedResolver::class.java)
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun `lookup initializes ExtendedResolver with Google and Cloudflare resolvers`() {
    val dnsFallback = DnsFallback()

    val result = dnsFallback.lookup("127.0.0.1")

    assertThat(result).contains(InetAddress.getByName("127.0.0.1"))
    val resolver = Lookup.getDefaultResolver()
    assertThat(resolver).isInstanceOf(ExtendedResolver::class.java)
    val extendedResolver = resolver as ExtendedResolver
    assertThat(extendedResolver.resolvers).hasLength(2)
    assertThat(extendedResolver.resolvers[0]).isInstanceOf(SimpleResolver::class.java)
    assertThat(extendedResolver.resolvers[1]).isInstanceOf(SimpleResolver::class.java)
  }

  @Test
  fun `provideDnsCache uses dns_cache subdirectory`() {
    val rootCacheDir = tempFolder.newFolder("cache")
    val cache = DnsModule().provideDnsCache(rootCacheDir)
    cache.close()

    assertThat(cache.directory).isEqualTo(File(rootCacheDir, "dns_cache"))
  }
}
