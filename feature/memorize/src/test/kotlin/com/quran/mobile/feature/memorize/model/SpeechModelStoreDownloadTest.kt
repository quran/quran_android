package com.quran.mobile.feature.memorize.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Downloads from a local server that can cut the connection, as mobile networks do. */
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class SpeechModelStoreDownloadTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val modelBytes = ByteArray(200_000) { (it % 251).toByte() }
  private val tokensBytes = "a 0\nb 1\n".toByteArray()
  private val modelRequests = AtomicInteger()
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

  /** How the model is served on its n-th request (1-based). */
  private var serve: (Int) -> Served = { Served.WHOLE }

  private enum class Served { WHOLE, CUT_SHORT, WRONG, MISSING }

  init {
    server.createContext("/model.onnx") { exchange ->
      when (serve(modelRequests.incrementAndGet())) {
        Served.WHOLE -> exchange.sendResponseHeaders(200, modelBytes.size.toLong()).also { exchange.responseBody.write(modelBytes) }
        Served.CUT_SHORT -> {
          // the length is announced, then the connection drops halfway
          exchange.sendResponseHeaders(200, modelBytes.size.toLong())
          exchange.responseBody.write(modelBytes, 0, modelBytes.size / 2)
          exchange.responseBody.flush()
        }
        Served.WRONG -> exchange.sendResponseHeaders(200, modelBytes.size.toLong()).also { exchange.responseBody.write(ByteArray(modelBytes.size)) }
        Served.MISSING -> exchange.sendResponseHeaders(404, -1)
      }
      exchange.close()
    }
    server.createContext("/tokens.txt") { exchange ->
      exchange.sendResponseHeaders(200, tokensBytes.size.toLong())
      exchange.responseBody.write(tokensBytes)
      exchange.close()
    }
    server.start()
  }

  private val base = "http://127.0.0.1:${server.address.port}"
  private val spec = SpeechModelSpec(
    id = "test-${UUID.randomUUID()}",
    model = ModelFile("model.onnx", "$base/model.onnx", sha(modelBytes), modelBytes.size.toLong()),
    tokens = ModelFile("tokens.txt", "$base/tokens.txt", sha(tokensBytes), tokensBytes.size.toLong()),
    license = "test",
    attribution = "test",
  )
  private val store = SpeechModelStore.forTest(context, spec, retryDelayMs = 10)

  @After
  fun stop() {
    server.stop(0)
  }

  @Test
  fun `a download cut off halfway is tried again`() {
    serve = { if (it == 1) Served.CUT_SHORT else Served.WHOLE }

    assertThat(download()).isEqualTo(SpeechModelStore.State.Installed)
    assertThat(modelRequests.get()).isEqualTo(2)
    assertThat(store.isInstalled()).isTrue()
  }

  @Test
  fun `a connection that keeps dropping fails after three tries`() {
    serve = { Served.CUT_SHORT }

    assertThat(download()).isInstanceOf(SpeechModelStore.State.Failed::class.java)
    assertThat(modelRequests.get()).isEqualTo(3)
    assertThat(store.isInstalled()).isFalse()
  }

  @Test
  fun `a file that isn't the one pinned is not downloaded again`() {
    serve = { Served.WRONG }

    assertThat(download()).isEqualTo(SpeechModelStore.State.Failed("model.onnx failed verification"))
    assertThat(modelRequests.get()).isEqualTo(1)
  }

  @Test
  fun `a missing file is not downloaded again`() {
    serve = { Served.MISSING }

    assertThat(download()).isEqualTo(SpeechModelStore.State.Failed("HTTP 404 for model.onnx"))
    assertThat(modelRequests.get()).isEqualTo(1)
  }

  private fun download(): SpeechModelStore.State = runBlocking {
    store.startDownload()
    withTimeout(20_000) {
      store.state.first { it is SpeechModelStore.State.Installed || it is SpeechModelStore.State.Failed }
    }
  }

  private fun sha(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
