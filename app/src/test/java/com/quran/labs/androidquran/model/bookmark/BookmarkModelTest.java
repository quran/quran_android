package com.quran.labs.androidquran.model.bookmark;

import android.support.v4.util.Pair;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import io.reactivex.observers.TestObserver;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BookmarkModelTest {

  @Mock BookmarksDBAdapter bookmarksAdapter;
  @Mock RecentPageModel recentPageModel;
  private BookmarkModel model;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(BookmarkModelTest.this);
    model = new BookmarkModel(bookmarksAdapter, recentPageModel);
  }

  @Test
  public void testUpdateTag() {
    when(bookmarksAdapter.updateTag(anyLong(), anyString())).thenReturn(true);

    Tag tag = new Tag(1, "First Tag");
    TestObserver<Void> testObserver = new TestObserver<>();
    model.updateTag(tag)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertNoErrors();
    testObserver.assertComplete();

    verify(bookmarksAdapter, times(1)).updateTag(tag.id, tag.name);
  }

  @Test
  public void testUpdateBookmarkTags() {
    when(bookmarksAdapter.tagBookmarks(
        any(long[].class), anySetOf(long.class), anyBoolean())).thenReturn(true);

    TestObserver<Boolean> testObserver = new TestObserver<>();
    model.updateBookmarkTags(new long[] { }, new HashSet<Long>(), false)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertNoErrors();
    testObserver.assertComplete();
  }

  @Test
  public void testBookmarkedAyahsOnPage() {
    List<Bookmark> bookmarks = new ArrayList<>(2);
    bookmarks.add(
        new Bookmark(42, 46, 1, 502, System.currentTimeMillis(), Collections.singletonList(2L)));
    bookmarks.add(new Bookmark(2, 2, 4, 2, System.currentTimeMillis() - 60000));
    when(bookmarksAdapter.getBookmarkedAyahsOnPage(anyInt())).thenReturn(bookmarks);

    Integer[][] inputs = new Integer[][] { new Integer[] { 42 }, new Integer[] { 42, 43 } };

    int total = 0;
    for (Integer[] input : inputs) {
      TestObserver<List<Bookmark>> testObserver = new TestObserver<>();
      model.getBookmarkedAyahsOnPageObservable(input)
          .subscribe(testObserver);
      testObserver.awaitTerminalEvent();
      testObserver.assertNoErrors();
      testObserver.assertValueCount(input.length);
      verify(bookmarksAdapter, times(input.length + total)).getBookmarkedAyahsOnPage(anyInt());
      total += input.length;
    }
  }

  @Test
  public void testIsPageBookmarked() {
    when(bookmarksAdapter.getBookmarkId(null, null, 42)).thenReturn(1L);
    when(bookmarksAdapter.getBookmarkId(null, null, 43)).thenReturn(-1L);

    TestObserver<Pair<Integer, Boolean>> testObserver = new TestObserver<>();
    model.getIsBookmarkedObservable(42, 43)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertNoErrors();
    testObserver.assertValueCount(2);

    List<Pair<Integer, Boolean>> results = testObserver.values();
    for (int i = 0; i < results.size(); i++) {
      Pair<Integer, Boolean> result = results.get(i);
      assertThat(result.first).isAnyOf(42, 43);
      if (result.first == 42) {
        assertThat(result.second).isTrue();
      } else if (result.first == 43) {
        assertThat(result.second).isFalse();
      }
    }
  }
}
