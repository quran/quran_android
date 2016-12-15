package com.quran.labs.androidquran.presenter.bookmark;

import android.support.v4.util.Pair;

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
import java.util.concurrent.CountDownLatch;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.schedulers.Schedulers;

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

  @Mock private BookmarkModel bookmarkModel;

  @BeforeClass
  public static void setup() {
    RxAndroidPlugins.setInitMainThreadSchedulerHandler(schedulerCallable -> Schedulers.io());
  }

  @Before
  public void setupTest() {
    MockitoAnnotations.initMocks(TagBookmarkPresenterTest.this);

    List<Tag> tags = new ArrayList<>();
    tags.add(new Tag(1, "Test"));
    when(bookmarkModel.tagsObservable()).thenReturn(Observable.empty());
    when(bookmarkModel.getTagsObservable()).thenReturn(Single.just(tags));
    when(bookmarkModel.getBookmarkTagIds(Matchers.any()))
        .thenReturn(Maybe.empty());
  }

  @Test
  public void testTagRefresh() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch secondLatch = new CountDownLatch(2);

    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel) {

      @Override
      void onRefreshedData(Pair<List<Tag>, List<Long>> data) {
        super.onRefreshedData(data);
        latch.countDown();
        secondLatch.countDown();
      }
    });

    presenter.setBookmarksMode(new long[] { 1 });
    latch.await();

    presenter.setAyahBookmarkMode(6, 76, 137);
    secondLatch.await();

    // make sure we called refresh twice
    verify(presenter, times(2)).refresh();

    // but make sure we only queried tags from the database once
    verify(bookmarkModel, times(1)).getTagsObservable();
  }

  @Test
  public void testChangeShouldOnlySaveExplicitlyForBookmarkIds() throws InterruptedException {
    when(bookmarkModel.updateBookmarkTags(
        any(long[].class), Matchers.any(), anyBoolean()))
        .thenReturn(Observable.just(true));

    CountDownLatch saveLatch = new CountDownLatch(1);
    CountDownLatch refreshLatch = new CountDownLatch(1);
    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel) {
      @Override
      void onRefreshedData(Pair<List<Tag>, List<Long>> data) {
        super.onRefreshedData(data);
        refreshLatch.countDown();
      }

      @Override
      public void saveChanges() {
        // override because we aren't testing the save process here, just that save is called
        saveLatch.countDown();
      }
    });

    presenter.setBookmarksMode(new long[] { 1 });
    refreshLatch.await();

    // try to modify a tag - save shouldn't be called
    assertThat(presenter.toggleTag(1)).isTrue();
    verify(presenter, times(0)).saveChanges();

    // explicitly call save
    presenter.saveChanges();
    saveLatch.countDown();
    verify(presenter, times(1)).saveChanges();
  }

  @Test
  public void testChangeShouldSaveImmediatelyForAyahBookmarks() throws InterruptedException {
    when(bookmarkModel.updateBookmarkTags(
        any(long[].class), Matchers.any(), anyBoolean()))
        .thenReturn(Observable.just(true));
    when(bookmarkModel.safeAddBookmark(anyInt(), anyInt(), anyInt()))
        .thenReturn(Observable.just(2L));

    // when refresh is done
    CountDownLatch refreshLatch = new CountDownLatch(1);

    // save latches
    CountDownLatch latch = new CountDownLatch(1);
    CountDownLatch secondLatch = new CountDownLatch(2);

    TagBookmarkPresenter presenter = spy(new TagBookmarkPresenter(bookmarkModel) {

      @Override
      void onRefreshedData(Pair<List<Tag>, List<Long>> data) {
        super.onRefreshedData(data);
        refreshLatch.countDown();
      }

      @Override
      void onSaveChangesDone() {
        super.onSaveChangesDone();
        latch.countDown();
        secondLatch.countDown();
      }
    });

    presenter.setAyahBookmarkMode(6, 76, 137);
    // make sure refresh is done first
    refreshLatch.await();

    // switch tag and wait for the save
    assertThat(presenter.toggleTag(1)).isTrue();
    latch.await();

    verify(presenter, times(1)).saveChanges();

    // try to save again (should do nothing)
    presenter.saveChanges();
    secondLatch.await();

    verify(bookmarkModel, times(1))
        .updateBookmarkTags(any(long[].class), Matchers.any(), anyBoolean());
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
