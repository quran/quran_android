package com.quran.labs.androidquran.service

import android.os.Environment
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.service.download.NoOpQuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier
import com.quran.labs.androidquran.service.util.QuranDownloadNotifier.NotificationDetails
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranDownloadServiceTest {

  private lateinit var mockWebServer: MockWebServer
  private lateinit var tempDir: File
  private lateinit var service: QuranDownloadService

  @Before
  fun setUp() {
    mockWebServer = MockWebServer()
    mockWebServer.start()
    tempDir = Files.createTempDirectory("download_service_test").toFile()
    ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(), 1024, 1024, 1024)
    service = Robolectric.buildService(QuranDownloadService::class.java)
      .create()
      .get()
    service.okHttpClient = OkHttpClient.Builder().build()
    service.notifier = NoOpQuranDownloadNotifier()
  }

  @After
  fun tearDown() {
    ShadowStatFs.reset()
    service.onDestroy()
    mockWebServer.shutdown()
    tempDir.deleteRecursively()
  }

  @Test
  fun `downloadUrl replaces partial file instead of appending when server returns 200 to Range`() {
    val partialFile = File(tempDir, "audio.mp3.part")
    partialFile.writeText("STALE_PART_")
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody("FRESH_FULL_CONTENT")
    )

    val result = service.downloadUrl(
      url = mockWebServer.url("/audio.mp3").toString(),
      path = tempDir.absolutePath,
      filename = "audio.mp3",
      notificationInfo = NotificationDetails("test", "k", 1, null)
    )

    assertThat(result).isEqualTo(0)
    val request = mockWebServer.takeRequest()
    assertThat(request.getHeader("Range")).isEqualTo("bytes=11-")
    val actualFile = File(tempDir, "audio.mp3")
    assertThat(actualFile.exists()).isTrue()
    assertThat(actualFile.readText()).isEqualTo("FRESH_FULL_CONTENT")
    assertThat(partialFile.exists()).isFalse()
  }

  @Test
  fun `downloadUrl appends to partial file when server returns 206 Partial Content`() {
    val partialFile = File(tempDir, "audio.mp3.part")
    partialFile.writeText("PART1_")
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(206)
        .setBody("PART2")
    )

    val result = service.downloadUrl(
      url = mockWebServer.url("/audio.mp3").toString(),
      path = tempDir.absolutePath,
      filename = "audio.mp3",
      notificationInfo = NotificationDetails("test", "k", 1, null)
    )

    assertThat(result).isEqualTo(0)
    val actualFile = File(tempDir, "audio.mp3")
    assertThat(actualFile.readText()).isEqualTo("PART1_PART2")
  }

  @Test
  fun `downloadUrl rejects path traversal filename`() {
    val evilOutside = File(tempDir.parentFile, "evil_download.zip")
    evilOutside.delete()

    val result = service.downloadUrl(
      url = mockWebServer.url("/evil.zip").toString(),
      path = tempDir.absolutePath,
      filename = "../evil_download.zip",
      notificationInfo = NotificationDetails("test", "k", 1, null)
    )

    assertThat(result).isEqualTo(QuranDownloadNotifier.ERROR_INVALID_DOWNLOAD)
    assertThat(mockWebServer.requestCount).isEqualTo(0)
    assertThat(evilOutside.exists()).isFalse()
  }

  @Test
  fun `getFilenameFromUrl strips query string and fragment`() {
    val name = QuranDownloadService.getFilenameFromUrl(
      "https://example.com/files/page001.png?token=abc&v=2#section"
    )
    assertThat(name).isEqualTo("page001.png")
  }
}
