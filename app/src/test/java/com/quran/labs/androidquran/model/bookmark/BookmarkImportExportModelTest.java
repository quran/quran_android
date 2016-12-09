package com.quran.labs.androidquran.model.bookmark;

import android.content.Context;
import android.net.Uri;

import com.quran.labs.androidquran.dao.BookmarkData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import okio.Buffer;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public class BookmarkImportExportModelTest {
  private static final String TAGS_JSON =
      "{\"bookmarks\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
          "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}";

  @Mock Context context;
  @Mock BookmarkModel bookmarkModel;
  private BookmarkImportExportModel bookmarkImportExportModel;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(BookmarkImportExportModelTest.this);
    bookmarkImportExportModel = new BookmarkImportExportModel(
        context, new BookmarkJsonModel(), bookmarkModel);
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

    Buffer source = new Buffer();
    source.writeUtf8(")");

    bookmarkImportExportModel.readBookmarks(source)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValueCount(0);
    testObserver.assertError(IOException.class);
  }

  @Test
  public void testExportNoExternalFilesDir() {
    TestObserver<Uri> testObserver = new TestObserver<>();

    when(bookmarkModel.getBookmarkDataObservable(anyInt())).thenReturn(
        Single.just(new BookmarkData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));
    when(context.getExternalFilesDir(anyString())).thenReturn(new File("/tmp/a/b/c"));

    bookmarkImportExportModel.exportBookmarksObservable()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValueCount(0);
    testObserver.assertError(IOException.class);
  }
}
