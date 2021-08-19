package com.quran.labs.androidquran.presenter.translation

import android.content.Context

import com.quran.labs.androidquran.dao.translation.TranslationList
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.UrlUtil

import org.junit.After
import org.junit.Before
import org.junit.Test

import java.io.File
import java.io.IOException

import io.reactivex.rxjava3.observers.TestObserver
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.source

import com.google.common.truth.Truth
import com.quran.labs.awaitTerminalEvent
import org.mockito.Mockito.mock

class TranslationManagerPresenterTest {

  companion object {
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var mockWebServer: MockWebServer
  private lateinit var translationManager: TranslationManagerPresenter

  @Before
  fun setup() {
    val mockAppContext = mock(Context::class.java)
    val mockSettings = mock(QuranSettings::class.java)
    val mockOkHttp = OkHttpClient.Builder().build()
    mockWebServer = MockWebServer()
    translationManager = object : TranslationManagerPresenter(
      mockAppContext, mockOkHttp, mockSettings, null,
      mock(QuranFileUtils::class.java), UrlUtil()
    ) {
      public override fun writeTranslationList(list: TranslationList) {
        // no op
      }
    }
    translationManager.host = mockWebServer.url("").toString()
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
  fun testGetCachedTranslationListObservable() {
    val testObserver = TestObserver<TranslationList>()
    translationManager.cachedTranslationListObservable
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertNoErrors()
  }

  @Test
  @Throws(Exception::class)
  fun getRemoteTranslationListObservable() {
    val mockResponse = MockResponse()
    val file = File(CLI_ROOT_DIRECTORY, "translations.json")
    val buffer = Buffer()
    buffer.writeAll(file.source())
    mockResponse.setBody(buffer)
    mockWebServer.enqueue(mockResponse)

    val testObserver = TestObserver<TranslationList>()
    translationManager.remoteTranslationListObservable
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
    testObserver.assertValueCount(1)
    testObserver.assertNoErrors()
    val (translations) = testObserver.values()[0]
    Truth.assertThat(translations).hasSize(57)
  }

  @Test
  fun getRemoteTranslationListObservableIssue() {
    val mockResponse = MockResponse()
    mockResponse.setResponseCode(500)
    mockWebServer.enqueue(mockResponse)

    val testObserver = TestObserver<TranslationList>()
    translationManager.remoteTranslationListObservable
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertError(IOException::class.java)
  }
}
