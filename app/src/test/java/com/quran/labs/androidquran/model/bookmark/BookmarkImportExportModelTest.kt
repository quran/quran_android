package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.Tag
import com.quran.labs.BaseTestExtension
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeBookmarkModel
import io.reactivex.rxjava3.observers.TestObserver
import okio.Buffer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class BookmarkImportExportModelTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val bookmarkModel = FakeBookmarkModel()
  private lateinit var bookmarkImportExportModel: BookmarkImportExportModel

  @Before
  fun setUp() {
    bookmarkModel.reset()
    clearFileProviderCache()
    bookmarkImportExportModel = BookmarkImportExportModel(
      context, BookmarkJsonModel(), bookmarkModel
    )
  }

  /**
   * FileProvider caches path strategies in a static map keyed by authority. When Robolectric
   * runs multiple export tests in sequence, each test gets a different external-files temp
   * directory, but the cached strategy still points to the first test's directory. Clearing
   * the cache before each test forces FileProvider to re-initialize with the current directory.
   */
  private fun clearFileProviderCache() {
    try {
      val cacheField = FileProvider::class.java.getDeclaredField("sCache")
      cacheField.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      val cache = cacheField.get(null) as? MutableMap<*, *>
      cache?.clear()
    } catch (_: Exception) {
      // If the field is renamed in a future AndroidX version the tests will still run;
      // they may fail for a different reason, at which point this helper needs updating.
    }
  }

  @Test
  fun testReadBookmarks() {
    val buffer = Buffer().writeUtf8(TAGS_JSON)
    val testObserver = TestObserver<BookmarkData>()
    bookmarkImportExportModel.readBookmarks(buffer)
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)
    testObserver.assertValueCount(1)
    testObserver.assertNoErrors()
  }

  @Test
  fun testReadInvalidBookmarks() {
    val testObserver = TestObserver<BookmarkData>()

    val source = Buffer()
    source.writeUtf8(")")

    bookmarkImportExportModel.readBookmarks(source)
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)
    testObserver.assertValueCount(0)
    testObserver.assertError(IOException::class.java)
  }

  @Test
  fun testExportBookmarksWithDataProducesUri() {
    bookmarkModel.setTags(listOf(Tag(1L, "Export Tag"), Tag(2L, "Another Tag")))
    bookmarkModel.setBookmarks(
      listOf(Bookmark(1L, 2, 255, 50, System.currentTimeMillis()))
    )

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    assertThat(testObserver.values()[0]).isNotNull()
  }

  @Test
  fun testExportBookmarksCsvWithDataProducesUri() {
    bookmarkModel.setTags(listOf(Tag(1L, "CSV Tag")))
    bookmarkModel.setBookmarks(
      listOf(Bookmark(1L, 1, 1, 1, System.currentTimeMillis()))
    )

    val testObserver = TestObserver<Uri>()
    bookmarkImportExportModel.exportBookmarksCSVObservable()
      .subscribe(testObserver)
    BaseTestExtension.awaitTerminalEvent(testObserver)

    testObserver.assertNoErrors()
    testObserver.assertValueCount(1)
    assertThat(testObserver.values()[0]).isNotNull()
  }

  companion object {
    private const val TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
        "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}"
  }
}
