package com.quran.labs.androidquran.presenter.bookmark;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.bookmark.BookmarkResult;
import com.quran.labs.androidquran.util.QuranSettings;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.content.Context;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import rx.observers.TestSubscriber;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.when;

public class BookmarkPresenterTest {
  private static final List<Tag> TAG_LIST;
  private static final List<Bookmark> AYAH_BOOKMARKS_LIST;
  private static final List<Bookmark> MIXED_BOOKMARKS_LIST;
  private static final String[] RESOURCE_ARRAY;
  private static final int AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG;
  private static final int MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG;

  static {
    // a list of two tags
    TAG_LIST = new ArrayList<>(2);
    TAG_LIST.add(new Tag(1, "First Tag"));
    TAG_LIST.add(new Tag(2, "Second Tag"));

    // two ayah bookmarks
    AYAH_BOOKMARKS_LIST = new ArrayList<>(2);
    AYAH_BOOKMARKS_LIST.add(
        new Bookmark(42, 46, 1, 502, System.currentTimeMillis(), Collections.singletonList(2L)));
    AYAH_BOOKMARKS_LIST.add(new Bookmark(2, 2, 4, 2, System.currentTimeMillis() - 60000));

    // two ayah bookmarks and one page bookmark
    MIXED_BOOKMARKS_LIST = new ArrayList<>(AYAH_BOOKMARKS_LIST);
    MIXED_BOOKMARKS_LIST.add(0,
        new Bookmark(23, null, null, 400, System.currentTimeMillis() + 1, Arrays.asList(1L, 2L)));

    // we return this fake array when getStringArray is called
    RESOURCE_ARRAY = new String[114];
    for (int i = 0; i < 114; i++) {
      RESOURCE_ARRAY[i] = String.valueOf(i);
    }

    // figure out how many rows the bookmarks would occupy if grouped by tags - this is really
    // the max between number of tags and 1 for each bookmark.
    int total = 0;
    for (Bookmark bookmark : AYAH_BOOKMARKS_LIST) {
      int tags = bookmark.tags.size();
      total += Math.max(tags, 1);
    }
    AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG = total;

    total = 0;
    for (Bookmark bookmark : MIXED_BOOKMARKS_LIST) {
      int tags = bookmark.tags.size();
      total += Math.max(tags, 1);
    }
    MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG = total;
  }

  @Mock Context appContext;
  @Mock Resources resources;
  @Mock QuranSettings settings;
  @Mock BookmarksDBAdapter bookmarksAdapter;
  private BookmarkPresenter presenter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(BookmarkPresenterTest.this);

    QuranSettings.setInstance(settings);
    when(appContext.getString(anyInt())).thenReturn("Test");
    when(appContext.getResources()).thenReturn(resources);
    when(resources.getStringArray(anyInt())).thenReturn(RESOURCE_ARRAY);
    when(appContext.getApplicationContext()).thenReturn(appContext);

    BookmarkModel model = new BookmarkModel(bookmarksAdapter);
    presenter = new BookmarkPresenter(appContext, settings, model, false);
  }

  @Test
  public void testBookmarkObservableAyahBookmarksByDate() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(AYAH_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(new ArrayList<Tag>());

    BookmarkResult result = getBookmarkResultByDateAndValidate(false);
    assertThat(result.tagMap).isEmpty();
    // 1 for the header, plus one row per item
    assertThat(result.rows).hasSize(AYAH_BOOKMARKS_LIST.size() + 1);
  }

  @Test
  public void testBookmarkObservableMixedBookmarksByDate() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(MIXED_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(new ArrayList<Tag>());

    BookmarkResult result = getBookmarkResultByDateAndValidate(false);
    assertThat(result.tagMap).isEmpty();
    // 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(result.rows).hasSize(MIXED_BOOKMARKS_LIST.size() + 2);
  }

  @Test
  public void testBookmarkObservableMixedBookmarksByDateWithRecentPage() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(MIXED_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(TAG_LIST);
    when(settings.getLastPage()).thenReturn(42);

    BookmarkResult result = getBookmarkResultByDateAndValidate(false);
    assertThat(result.tagMap).hasSize(2);
    // 2 for "current page", 1 for "page bookmarks" and 1 for "ayah bookmarks"
    assertThat(result.rows).hasSize(MIXED_BOOKMARKS_LIST.size() + 4);
  }

  @Test
  public void testBookmarkObservableAyahBookmarksGroupedByTag() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(AYAH_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(TAG_LIST);

    BookmarkResult result = getBookmarkResultByDateAndValidate(true);
    assertThat(result.tagMap).hasSize(2);

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(result.rows).hasSize(
        AYAH_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size() + 1);
  }

  @Test
  public void testBookmarkObservableMixedBookmarksGroupedByTag() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(MIXED_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(TAG_LIST);

    BookmarkResult result = getBookmarkResultByDateAndValidate(true);
    assertThat(result.tagMap).hasSize(2);

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted
    assertThat(result.rows).hasSize(
        MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size() + 1);
  }

  @Test
  public void testBookmarkObservableMixedBookmarksGroupedByTagWithRecentPage() {
    when(bookmarksAdapter.getBookmarks(BookmarksDBAdapter.SORT_DATE_ADDED))
        .thenReturn(MIXED_BOOKMARKS_LIST);
    when(bookmarksAdapter.getTags()).thenReturn(TAG_LIST);
    when(settings.getLastPage()).thenReturn(42);

    BookmarkResult result = getBookmarkResultByDateAndValidate(true);
    assertThat(result.tagMap).hasSize(2);

    // number of tags (or 1) for each bookmark, plus number of tags (headers), plus unsorted, plus
    // current page header, plus current page
    assertThat(result.rows).hasSize(
        MIXED_BOOKMARKS_ROW_COUNT_WHEN_GROUPED_BY_TAG + TAG_LIST.size() + 1 + 2);
  }

  private BookmarkResult getBookmarkResultByDateAndValidate(boolean groupByTags) {
    TestSubscriber<BookmarkResult> testSubscriber = new TestSubscriber<>();
    presenter.getBookmarksListObservable(BookmarksDBAdapter.SORT_DATE_ADDED, groupByTags)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValueCount(1);
    return testSubscriber.getOnNextEvents().get(0);
  }
}