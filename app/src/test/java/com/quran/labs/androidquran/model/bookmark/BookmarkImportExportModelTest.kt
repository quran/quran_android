package com.quran.labs.androidquran.model.bookmark

import android.content.Context

import com.quran.data.model.bookmark.BookmarkData

import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

import java.io.IOException

import io.reactivex.observers.TestObserver
import okio.Buffer

class BookmarkImportExportModelTest {

  companion object {
    private const val TAGS_JSON = "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
        "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}"
  }

  @Mock
  private lateinit var context: Context

  @Mock
  private lateinit var bookmarkModel: BookmarkModel

  private lateinit var bookmarkImportExportModel: BookmarkImportExportModel

  @Before
  fun setUp() {
    MockitoAnnotations.openMocks(this@BookmarkImportExportModelTest)
    bookmarkImportExportModel = BookmarkImportExportModel(
      context, BookmarkJsonModel(), bookmarkModel)
  }

  @Test
  fun testReadBookmarks() {
    val buffer = Buffer().writeUtf8(TAGS_JSON)
    val testObserver = TestObserver<BookmarkData>()
    bookmarkImportExportModel.readBookmarks(buffer)
      .subscribe(testObserver)
    testObserver.awaitTerminalEvent()
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
    testObserver.awaitTerminalEvent()
    testObserver.assertValueCount(0)
    testObserver.assertError(IOException::class.java)
  }
}
