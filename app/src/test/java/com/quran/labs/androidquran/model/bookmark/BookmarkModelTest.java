package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.observers.TestSubscriber;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BookmarkModelTest {

  @Mock BookmarksDBAdapter bookmarksAdapter;
  private BookmarkModel model;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(BookmarkModelTest.this);
    model = new BookmarkModel(bookmarksAdapter);
  }

  @Test
  public void testUpdateTag() {
    when(bookmarksAdapter.updateTag(anyLong(), anyString())).thenReturn(true);

    Tag tag = new Tag(1, "First Tag");
    TestSubscriber<Boolean> testSubscriber = new TestSubscriber<>();
    model.updateTag(tag)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValueCount(1);
    testSubscriber.assertValue(true);

    verify(bookmarksAdapter, times(1)).updateTag(tag.id, tag.name);
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
      TestSubscriber<List<Bookmark>> testSubscriber = new TestSubscriber<>();
      model.getBookmarkedAyahsOnPageObservable(input)
          .subscribe(testSubscriber);
      testSubscriber.awaitTerminalEvent();
      testSubscriber.assertCompleted();
      testSubscriber.assertNoErrors();
      testSubscriber.assertValueCount(input.length);
      verify(bookmarksAdapter, times(input.length + total)).getBookmarkedAyahsOnPage(anyInt());
      total += input.length;
    }
  }

  @Test
  public void testIsPageBookmarked() {
    when(bookmarksAdapter.getBookmarkId(null, null, 42)).thenReturn(1L);
    when(bookmarksAdapter.getBookmarkId(null, null, 43)).thenReturn(-1L);

    TestSubscriber<Pair<Integer, Boolean>> testSubscriber = new TestSubscriber<>();
    model.getIsBookmarkedObservable(42, 43)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValueCount(2);

    List<Pair<Integer, Boolean>> results = testSubscriber.getOnNextEvents();
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