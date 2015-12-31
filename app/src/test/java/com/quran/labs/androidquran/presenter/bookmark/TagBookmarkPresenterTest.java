package com.quran.labs.androidquran.presenter.bookmark;

import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import rx.Observable;
import rx.Scheduler;
import rx.android.plugins.RxAndroidPlugins;
import rx.android.plugins.RxAndroidSchedulersHook;
import rx.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TagBookmarkPresenterTest {

  @Mock BookmarkModel bookmarkModel;

  @BeforeClass
  public static void setupMainThread() {
    RxAndroidPlugins.getInstance().registerSchedulersHook(new RxAndroidSchedulersHook() {
      @Override
      public Scheduler getMainThreadScheduler() {
        return Schedulers.immediate();
      }
    });
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(TagBookmarkPresenterTest.this);

    List<Tag> tags = new ArrayList<>();
    tags.add(new Tag(1, "Test"));
    when(bookmarkModel.getTagsObservable()).thenReturn(Observable.just(tags));
    when(bookmarkModel.getBookmarkTagIds(Matchers.<Observable<Long>>any()))
        .thenReturn(Observable.<List<Long>>empty());
  }

  @Test
  public void testTagRefresh() {
    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel));
    presenter.setBookmarksMode(new long[] { 1 });
    presenter.setAyahBookmarkMode(6, 76, 137);

    // make sure we called refresh twice
    verify(presenter, times(2)).refresh();
    // but make sure we only queried tags from the database once
    verify(bookmarkModel, times(1)).getTagsObservable();
  }

  @Test
  public void testChangeShouldOnlySaveExplicitlyForBookmarkIds() {
    when(bookmarkModel.updateBookmarkTags(
        any(long[].class), Matchers.<Set<Long>>any(), anyBoolean()))
        .thenReturn(Observable.just(true));

    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel));
    presenter.setBookmarksMode(new long[] { 1 });
    assertThat(presenter.toggleTag(1)).isTrue();

    verify(presenter, times(0)).saveChanges();
    presenter.saveChanges();
    verify(presenter, times(1)).saveChanges();
  }

  @Test
  public void testChangeShouldSaveImmediatelyForAyahBookmarks() {
    when(bookmarkModel.updateBookmarkTags(
        any(long[].class), Matchers.<Set<Long>>any(), anyBoolean()))
        .thenReturn(Observable.just(true));
    when(bookmarkModel.safeAddBookmark(anyInt(), anyInt(), anyInt()))
        .thenReturn(Observable.just(2L));

    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel));
    presenter.setAyahBookmarkMode(6, 76, 137);

    assertThat(presenter.toggleTag(1)).isTrue();
    verify(presenter, times(1)).saveChanges();

    presenter.saveChanges();
    verify(bookmarkModel, times(1))
        .updateBookmarkTags(any(long[].class), Matchers.<Set<Long>>any(), anyBoolean());
  }

  @Test
  public void testAddDialogCall() {
    TagBookmarkDialog bookmarkDialog = mock(TagBookmarkDialog.class);

    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel));
    presenter.bind(bookmarkDialog);
    assertThat(presenter.toggleTag(-1)).isFalse();
    verify(bookmarkDialog, times(1)).showAddTagDialog();
    presenter.unbind(bookmarkDialog);
  }

  @Test
  public void testAddDialogCallUnbound() {
    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel));
    assertThat(presenter.toggleTag(-1)).isFalse();
    verify(presenter, times(0)).setMadeChanges();
  }
}