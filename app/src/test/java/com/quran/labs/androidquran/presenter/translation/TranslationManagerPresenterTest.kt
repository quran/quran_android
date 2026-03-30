package com.quran.labs.androidquran.presenter.translation

import android.app.Application
import app.cash.turbine.test
import com.google.common.truth.Truth
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.dao.translation.TranslationList
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.source
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class TranslationManagerPresenterTest {

  companion object {
    private const val CLI_ROOT_DIRECTORY = "src/test/resources"
  }

  private lateinit var mockWebServer: MockWebServer
  private lateinit var quranSettings: QuranSettings
  private lateinit var quranFileUtils: QuranFileUtils
  private val fakeTranslationsAdapter = FakeTranslationsDBAdapter()

  @Before
  fun setup() {
    mockWebServer = MockWebServer()
    QuranSettings.setInstance(null)
    val appContext = ApplicationProvider.getApplicationContext<Application>()
    quranSettings = QuranSettings.getInstance(appContext)
    quranFileUtils = FakeQuranFileUtils.create(appContext)
    fakeTranslationsAdapter.clearTranslations()
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
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
    // Arrange
    val translationManager = translationManager(true)
    quranSettings.setLastUpdatedTranslationDate(0L)
    enqueueMockResponse()

    // Act + Assert
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

  @Test
  fun `getTranslations with fresh cache emits only one item`() = runTest {
    // Arrange
    quranSettings.setLastUpdatedTranslationDate(System.currentTimeMillis())
    // Enqueue a response so MockWebServer doesn't close the connection before cache emits.
    // The key assertion is that take(1) causes only 1 item to be emitted, not 2.
    enqueueMockResponse()
    val manager = translationManager(withCache = true)

    // Act - collect all items; take(1) internal to getTranslations means flow completes after 1
    val allItems = manager.getTranslations(false).toList()

    // Assert - take(1) is used (fresh cache), so exactly 1 item is emitted
    Truth.assertThat(allItems).hasSize(1)
  }

  @Test
  fun `updateItem writes translation to adapter`() = runTest {
    // Arrange
    val translation = makeTranslation(id = 1, fileName = "test_v4.db", minimumVersion = 4)
    val item = TranslationItem(translation, localVersion = 1)

    // Act
    translationManager().updateItem(item)

    // Assert
    val stored = fakeTranslationsAdapter.getTranslations().toList().flatten()
    Truth.assertThat(stored).hasSize(1)
    Truth.assertThat(stored[0].filename).isEqualTo("test_v4.db")
  }

  @Test
  fun `updateItem with minimumVersion 5 or above deletes old filename before writing`() = runTest {
    // Arrange
    val oldEntry = com.quran.mobile.translation.model.LocalTranslation(
      id = 1L,
      filename = "old_schema.db",
      name = "Old Translation",
      translator = null,
      translatorForeign = null,
      url = "",
      languageCode = "en",
      version = 1,
      minimumVersion = 4,
      displayOrder = 0
    )
    fakeTranslationsAdapter.addTranslation(oldEntry)
    val translation = makeTranslation(id = 2, fileName = "old_schema.db", minimumVersion = 5)
    val item = TranslationItem(translation, localVersion = 1)

    // Act
    translationManager().updateItem(item)

    // Assert - old entry deleted by filename, new entry written
    val stored = fakeTranslationsAdapter.getTranslations().toList().flatten()
    Truth.assertThat(stored).hasSize(1)
    Truth.assertThat(stored[0].id).isEqualTo(2L)
  }

  @Test
  fun `updateItemOrdering writes all items to adapter`() = runTest {
    // Arrange
    val items = listOf(
      TranslationItem(makeTranslation(id = 1, fileName = "t1.db"), localVersion = 1),
      TranslationItem(makeTranslation(id = 2, fileName = "t2.db"), localVersion = 1),
      TranslationItem(makeTranslation(id = 3, fileName = "t3.db"), localVersion = 1),
    )

    // Act
    translationManager().updateItemOrdering(items)

    // Assert
    val stored = fakeTranslationsAdapter.getTranslations().toList().flatten()
    Truth.assertThat(stored).hasSize(3)
  }

  @Test
  fun `checkForUpdates does nothing when cache is fresh`() {
    // Arrange
    quranSettings.setLastUpdatedTranslationDate(System.currentTimeMillis())

    // Act
    translationManager().checkForUpdates()

    // Assert - staleness check is false so no coroutine is launched
    Truth.assertThat(mockWebServer.requestCount).isEqualTo(0)
  }

  private fun enqueueMockResponse() {
    val mockResponse = MockResponse()
    val file = File(CLI_ROOT_DIRECTORY, "translations.json")
    val buffer = Buffer()
    buffer.writeAll(file.source())
    mockResponse.setBody(buffer)
    mockWebServer.enqueue(mockResponse)
  }

  private fun makeTranslation(
    id: Int = 1,
    minimumVersion: Int = 1,
    currentVersion: Int = 1,
    fileName: String = "translation_$id.db",
  ): Translation {
    return Translation(
      id = id,
      minimumVersion = minimumVersion,
      currentVersion = currentVersion,
      displayName = "Translation $id",
      downloadType = "database",
      fileName = fileName,
      fileUrl = "https://example.com/$fileName",
      saveTo = fileName,
      languageCode = "en",
    )
  }

  private fun translationManager(withCache: Boolean = false): TranslationManagerPresenter {
    val appContext = ApplicationProvider.getApplicationContext<Application>()
    val okHttp = OkHttpClient.Builder().build()

    return object : TranslationManagerPresenter(
      appContext, okHttp, quranSettings, fakeTranslationsAdapter, quranFileUtils
    ) {
      override val cachedFile: File =
        if (withCache) File(CLI_ROOT_DIRECTORY, "translations.json") else super.cachedFile

      override fun writeTranslationList(list: TranslationList) {
        // no op
      }
    }.apply {
      host = mockWebServer.url("").toString()
    }
  }
}
