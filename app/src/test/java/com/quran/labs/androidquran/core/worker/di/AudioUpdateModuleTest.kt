package com.quran.labs.androidquran.core.worker.di

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AudioUpdateModuleTest {

  private lateinit var mockWebServer: MockWebServer

  @Before
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()
  }

  @After
  fun tearDown() {
    mockWebServer.shutdown()
  }

  @Test
  fun `provideAudioUpdateService uses injected OkHttpClient`() = runTest {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("{\"current_revision\":1,\"updates\":[]}")
    )
    var intercepted = false
    val client = OkHttpClient.Builder()
      .addInterceptor(Interceptor { chain ->
        intercepted = true
        val original = chain.request()
        val rewrittenUrl = mockWebServer.url(
          original.url.encodedPath + (original.url.encodedQuery?.let { "?$it" } ?: "")
        )
        chain.proceed(
          original.newBuilder()
            .url(rewrittenUrl)
            .header("X-Test-Injected", "yes")
            .build()
        )
      })
      .build()

    val service = AudioUpdateModule.provideAudioUpdateService(client)
    val response = service.getUpdates(1)

    assertThat(intercepted).isTrue()
    assertThat(response.isSuccessful).isTrue()
    val recordedRequest = mockWebServer.takeRequest()
    assertThat(recordedRequest.getHeader("X-Test-Injected")).isEqualTo("yes")
    assertThat(recordedRequest.path).isEqualTo("/data/audio_updates.php?revision=1")
  }
}
