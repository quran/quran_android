package com.quran.labs.androidquran.presenter.translation

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth
import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationList
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

class TranslationManagerPresenterTest {

  companion object {
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var mockWebServer: MockWebServer
  private val mockSettings = mock(QuranSettings::class.java)

  @Before
  fun setup() {
    mockWebServer = MockWebServer()
  }

  @After
  fun tearDown() {
    try {
      mockWebServer.shutdown()
    } catch (e: Exception) {
      // no op
    }
  }

  @Test
  fun testGetCachedTranslationList() = runTest {
    translationManager().cachedTranslationList().test {
      awaitComplete()
    }
  }

  @Test
  fun testGetTranslationsWhenStaleCache() = runTest {
    val translationManager = translationManager(true)
    Mockito.`when`(mockSettings.lastUpdatedTranslationDate).thenReturn(0)
    enqueueMockResponse()

    translationManager.getTranslations(false).test {
      val firstItem = awaitItem()
      val secondItem = awaitItem()
      // in this test, both the cache and the network return the same result
      Truth.assertThat(firstItem).isEqualTo(secondItem)
      awaitComplete()
    }
  }

  @Test
  fun testGetRemoteTranslationList() = runTest {
    enqueueMockResponse()
    translationManager().remoteTranslationList().test {
      val item = awaitItem()
      Truth.assertThat(item.translations).hasSize(57)
      awaitComplete()
    }
  }

  @Test
  fun testRemoteTranslationListServerIssue() = runTest {
    val mockResponse = MockResponse()
    mockResponse.setResponseCode(500)
    mockWebServer.enqueue(mockResponse)

    translationManager().remoteTranslationList().test {
      val throwable = awaitError()
      Truth.assertThat(throwable).isInstanceOf(IOException::class.java)
    }
  }

  private fun enqueueMockResponse() {
    val mockResponse = MockResponse()
    val file = File(CLI_ROOT_DIRECTORY, "translations.json")
    val buffer = Buffer()
    buffer.writeAll(file.source())
    mockResponse.setBody(buffer)
    mockWebServer.enqueue(mockResponse)
  }

  private fun translationManager(withCache: Boolean = false): TranslationManagerPresenter {
    val mockAppContext = mock(Context::class.java)
    val mockOkHttp = OkHttpClient.Builder().build()

    return object : TranslationManagerPresenter(
      mockAppContext, mockOkHttp, mockSettings, mock(TranslationsDBAdapter::class.java),
      mock(QuranFileUtils::class.java)
    ) {
      override val cachedFile: File =
        if (withCache) File(CLI_ROOT_DIRECTORY, "translations.json") else super.cachedFile

      // TODO: this is necessary because we don't have a way to mock the database adapter yet
      override suspend fun mergeWithServerTranslations(serverTranslations: List<Translation>): List<TranslationItem> {
        return serverTranslations.map {
          TranslationItem(it, 0)
        }
      }

      override fun writeTranslationList(list: TranslationList) {
        // no op
      }
    }.apply {
      host = mockWebServer.url("").toString()
    }
  }
}
