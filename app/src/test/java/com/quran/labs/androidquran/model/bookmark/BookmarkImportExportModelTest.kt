package com.quran.labs.androidquran.model.bookmark

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.quran.data.model.bookmark.BookmarkData
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
    bookmarkImportExportModel = BookmarkImportExportModel(
      context, BookmarkJsonModel(), bookmarkModel
    )
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

  companion object {
    private const val TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
        "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}"
  }
}
