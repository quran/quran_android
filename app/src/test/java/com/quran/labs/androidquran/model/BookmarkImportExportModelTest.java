package com.quran.labs.androidquran.model;

import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.Test;

import android.content.Context;

import okio.Buffer;
import rx.observers.TestSubscriber;

import static org.mockito.Mockito.mock;

public class BookmarkImportExportModelTest {
  private static final String TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
          "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}";

  private BookmarkImportExportModel bookmarkImportExportModel;

  @Before
  public void setUp() {
    Context context = mock(Context.class);
    BookmarksDBAdapter databaseAdapter = mock(BookmarksDBAdapter.class);
    bookmarkImportExportModel = new BookmarkImportExportModel(
        context, new BookmarkJsonModel(), databaseAdapter);
  }

  @Test
  public void testReadBookmarks() {
    Buffer buffer = new Buffer().writeUtf8(TAGS_JSON);
    TestSubscriber<BookmarkData> testSubscriber = new TestSubscriber<>();
    bookmarkImportExportModel.readBookmarks(buffer)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValueCount(1);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
  }

  @Test
  public void testReadInvalidBookmarks() {
    TestSubscriber<BookmarkData> testSubscriber = new TestSubscriber<>();
    bookmarkImportExportModel.readBookmarks(null)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValueCount(0);
    testSubscriber.assertNotCompleted();
    testSubscriber.assertError(NullPointerException.class);
  }
}