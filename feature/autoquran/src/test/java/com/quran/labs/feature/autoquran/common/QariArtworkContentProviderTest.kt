package com.quran.labs.feature.autoquran.common

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.feature.autoquran.di.QuranAutoInjector
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import com.quran.mobile.di.QuranApplicationComponent
import com.quran.mobile.di.QuranApplicationComponentProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QariArtworkContentProviderTest {

  @Test
  fun `openFile rejects non-read-only modes`() {
    val provider = Robolectric.buildContentProvider(QariArtworkContentProvider::class.java)
      .create(ProviderInfo().apply { authority = "com.quran.test.autoquran.artwork" })
      .get()
    val uri = Uri.parse("content://com.quran.test.autoquran.artwork/artwork/qari/1")

    listOf("rw", "w", "wt", "rwt", "wa").forEach { mode ->
      val error = try {
        provider.openFile(uri, mode)
        null
      } catch (e: FileNotFoundException) {
        e
      }
      assertThat(error).isNotNull()
    }
  }

  @Test
  fun `openFile fails closed when injector is unavailable`() {
    val provider = Robolectric.buildContentProvider(QariArtworkContentProvider::class.java)
      .create(ProviderInfo().apply { authority = "com.quran.test.autoquran.artwork" })
      .get()
    val uri = Uri.parse("content://com.quran.test.autoquran.artwork/artwork/qari/1")

    val pfd = provider.openFile(uri, "r")
    val bytes = ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }

    assertThat(bytes).isEmpty()
  }

  @Test
  @Config(application = FakeAutoApplication::class, sdk = [33])
  fun `openFile serves known qari and rejects unknown qari when injected`() {
    val provider = Robolectric.buildContentProvider(QariArtworkContentProvider::class.java)
      .create(ProviderInfo().apply { authority = "com.quran.test.autoquran.artwork" })
      .get()

    val knownUri = Uri.parse("content://com.quran.test.autoquran.artwork/artwork/qari/1")
    val unknownUri = Uri.parse("content://com.quran.test.autoquran.artwork/artwork/qari/999")

    val knownPfd = provider.openFile(knownUri, "r")
    // Robolectric's ParcelFileDescriptor.createPipe() is file-backed rather than a blocking OS pipe,
    // so give the background executor a brief moment to flush before reading if needed.
    var knownBytes = ByteArray(0)
    val deadline = System.currentTimeMillis() + 2000L
    while (knownBytes.isEmpty() && System.currentTimeMillis() < deadline) {
      knownBytes = ParcelFileDescriptor.AutoCloseInputStream(knownPfd.dup()).use { it.readBytes() }
      if (knownBytes.isEmpty()) Thread.sleep(20)
    }
    knownPfd.close()

    val unknownPfd = provider.openFile(unknownUri, "r")
    Thread.sleep(100)
    val unknownBytes = ParcelFileDescriptor.AutoCloseInputStream(unknownPfd)
      .use { it.readBytes() }

    assertThat(FakeAutoApplication.injectCount.get()).isEqualTo(1)
    assertThat(knownBytes).isNotEmpty()
    assertThat(unknownBytes).isEmpty()
  }

  @Test
  @Config(application = FakeAutoApplication::class, sdk = [33])
  fun `concurrent openFile calls inject provider at most once`() {
    FakeAutoApplication.injectCount.set(0)
    val provider = Robolectric.buildContentProvider(QariArtworkContentProvider::class.java)
      .create(ProviderInfo().apply { authority = "com.quran.test.autoquran.artwork" })
      .get()
    val uri = Uri.parse("content://com.quran.test.autoquran.artwork/artwork/qari/1")

    val threads = 8
    val startLatch = CountDownLatch(1)
    val pool = Executors.newFixedThreadPool(threads)

    try {
      val futures = (0 until threads).map {
        pool.submit<ByteArray> {
          startLatch.await()
          val pfd = provider.openFile(uri, "r")
          var bytes = ByteArray(0)
          val deadline = System.currentTimeMillis() + 2000L
          while (bytes.isEmpty() && System.currentTimeMillis() < deadline) {
            bytes = ParcelFileDescriptor.AutoCloseInputStream(pfd.dup()).use { it.readBytes() }
            if (bytes.isEmpty()) Thread.sleep(20)
          }
          pfd.close()
          bytes
        }
      }
      startLatch.countDown()
      futures.forEach { future ->
        assertThat(future.get(10, TimeUnit.SECONDS)).isNotEmpty()
      }
      assertThat(FakeAutoApplication.injectCount.get()).isEqualTo(1)
    } finally {
      pool.shutdownNow()
    }
  }

  class FakeAutoApplication : Application(), QuranApplicationComponentProvider {
    private val component = object : QuranApplicationComponent, QuranAutoInjector {
      private val fakePageProvider = object : PageProvider {
        override fun getDataSource(): QuranDataSource = throw NotImplementedError()
        override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
          throw NotImplementedError()
        override fun getImageVersion(): Int = throw NotImplementedError()
        override fun mushafType(): MushafType = throw NotImplementedError()
        override fun getImagesBaseUrl(): String = throw NotImplementedError()
        override fun getImagesZipBaseUrl(): String = throw NotImplementedError()
        override fun getPatchBaseUrl(): String = throw NotImplementedError()
        override fun getAyahInfoBaseUrl(): String = throw NotImplementedError()
        override fun getDatabasesBaseUrl(): String = throw NotImplementedError()
        override fun getAudioDatabasesBaseUrl(): String = throw NotImplementedError()
        override fun getAudioDirectoryName(): String = throw NotImplementedError()
        override fun getDatabaseDirectoryName(): String = throw NotImplementedError()
        override fun getAyahInfoDirectoryName(): String = throw NotImplementedError()
        override fun getImagesDirectoryName(): String = throw NotImplementedError()
        override fun getPreviewTitle(): Int = throw NotImplementedError()
        override fun getPreviewDescription(): Int = throw NotImplementedError()
        override fun getQaris(): List<Qari> = listOf(
          Qari(
            id = 1,
            nameResource = android.R.string.ok,
            url = "https://example.com/1/",
            path = "qari_1",
            hasGaplessAlternative = false,
            db = null
          )
        )
        override fun getDefaultQariId(): Int = 1
      }

      override fun inject(service: QuranBrowsableAudioPlaybackService) = Unit

      override fun inject(provider: QariArtworkContentProvider) {
        injectCount.incrementAndGet()
        provider.pageProvider = fakePageProvider
      }
    }

    override fun provideQuranApplicationComponent(): QuranApplicationComponent = component

    companion object {
      val injectCount = AtomicInteger(0)
    }
  }
}
