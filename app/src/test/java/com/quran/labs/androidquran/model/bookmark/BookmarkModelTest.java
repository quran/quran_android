package com.quran.labs.androidquran.model.bookmark;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quran.data.model.bookmark.Tag;
import com.quran.labs.BaseTestExtension;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;

import io.reactivex.rxjava3.observers.TestObserver;

public class BookmarkModelTest {

  @Mock BookmarksDBAdapter bookmarksAdapter;
  @Mock RecentPageModel recentPageModel;
  private BookmarkModel model;

  @Before
  public void setupTest() {
    MockitoAnnotations.openMocks(BookmarkModelTest.this);
    model = new BookmarkModel(bookmarksAdapter, recentPageModel);
  }

  @Test
  public void testUpdateTag() {
    when(bookmarksAdapter.updateTag(anyLong(), anyString())).thenReturn(true);

    Tag tag = new Tag(1, "First Tag");
    TestObserver<Void> testObserver = new TestObserver<>();
    model.updateTag(tag)
        .subscribe(testObserver);
    BaseTestExtension.awaitTerminalEvent(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertComplete();

    verify(bookmarksAdapter, times(1)).updateTag(tag.getId(), tag.getName());
  }

  @Test
  public void testUpdateBookmarkTags() {
    when(bookmarksAdapter.tagBookmarks(
        any(long[].class), anySet(), anyBoolean())).thenReturn(true);

    TestObserver<Boolean> testObserver = new TestObserver<>();
    model.updateBookmarkTags(new long[] { }, new HashSet<>(), false)
        .subscribe(testObserver);
    BaseTestExtension.awaitTerminalEvent(testObserver);
    testObserver.assertNoErrors();
    testObserver.assertComplete();
  }
}
