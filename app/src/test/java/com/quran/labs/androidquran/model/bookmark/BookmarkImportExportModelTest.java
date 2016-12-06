package com.quran.labs.androidquran.model.bookmark;

import android.content.Context;

import com.quran.labs.androidquran.dao.BookmarkData;

import org.junit.Before;
import org.junit.Test;

import io.reactivex.observers.TestObserver;
import okio.Buffer;

import static org.mockito.Mockito.mock;

public class BookmarkImportExportModelTest {
  private static final String TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
          "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}";

  private BookmarkImportExportModel bookmarkImportExportModel;

  @Before
  public void setUp() {
    Context context = mock(Context.class);
    BookmarkModel model = mock(BookmarkModel.class);
    bookmarkImportExportModel = new BookmarkImportExportModel(
        context, new BookmarkJsonModel(), model);
  }

  @Test
  public void testReadBookmarks() {
    Buffer buffer = new Buffer().writeUtf8(TAGS_JSON);
    TestObserver<BookmarkData> testObserver = new TestObserver<>();
    bookmarkImportExportModel.readBookmarks(buffer)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValueCount(1);
    testObserver.assertNoErrors();
  }

  @Test
  public void testReadInvalidBookmarks() {
    TestObserver<BookmarkData> testObserver = new TestObserver<>();
    bookmarkImportExportModel.readBookmarks(null)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValueCount(0);
    testObserver.assertError(NullPointerException.class);
  }
}
