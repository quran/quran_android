package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.RecentPage;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import rx.Scheduler;
import rx.android.plugins.RxAndroidPlugins;
import rx.android.plugins.RxAndroidSchedulersHook;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecentPageModelTest {
  private static final List<RecentPage> SAMPLE_RECENT_PAGES = new ArrayList<>();
  static {
    long timestamp = System.currentTimeMillis();
    SAMPLE_RECENT_PAGES.add(new RecentPage(49, timestamp));
    SAMPLE_RECENT_PAGES.add(new RecentPage(100, timestamp - 10000));

    // AndroidSchedulers.mainThread should be Schedulers.io in tests
    RxAndroidPlugins rxAndroidPlugins = RxAndroidPlugins.getInstance();
    rxAndroidPlugins.reset();
    rxAndroidPlugins.registerSchedulersHook(new RxAndroidSchedulersHook() {
      @Override
      public Scheduler getMainThreadScheduler() {
        return Schedulers.io();
      }
    });
  }

  @Mock BookmarksDBAdapter bookmarksAdapter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(RecentPageModelTest.this);
  }

  @Test
  public void testEmptyRecentPages() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(new ArrayList<RecentPage>());

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
    recentPageModel.getLatestPageObservable()
        .first()
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValue(Constants.NO_PAGE);
  }

  @Test
  public void testNormalRecentPages() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
    recentPageModel.getLatestPageObservable()
        .first()
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValue(49);
  }

  @Test
  public void testUpdateLatestPage() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    // make sure the mock data is received and onNext happens
    TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
    recentPageModel.getLatestPageObservable()
        .take(1)
        .subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    // after that, let's resubscribe and update with 2 other pages
    testSubscriber = new TestSubscriber<>();
    recentPageModel.getLatestPageObservable()
        .take(3)
        .subscribe(testSubscriber);
    recentPageModel.updateLatestPage(51);
    recentPageModel.updateLatestPage(23);

    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValues(49, 51, 23);
  }

  @Test
  public void testUpdateLatestPageWithSlowRecents() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<>();
    recentPageModel.getLatestPageObservable()
        .take(2)
        .subscribe(testSubscriber);

    // we immediately write 2 pages before the mock data has returned - in this case, we
    // should not get the test data (because otherwise, it would be out of order!)
    recentPageModel.updateLatestPage(51);
    recentPageModel.updateLatestPage(23);

    testSubscriber.awaitTerminalEvent();
    testSubscriber.assertValues(51, 23);
  }

  @Test
  public void testRecentPagesUpdated() {
    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);
    recentPageModel.persistLatestPage(200, 200, 200);

    ArgumentCaptor<Integer> argumentCaptor = ArgumentCaptor.forClass(Integer.class);

    // implicitly wait for addRecentPage to be called due to forcing argument capture
    verify(bookmarksAdapter, times(1)).addRecentPage(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(200);
    verify(bookmarksAdapter, times(0)).replaceRecentRangeWithPage(anyInt(), anyInt(), anyInt());
  }

  @Test
  public void testRecentPagesUpdatedWithSubscription() {
    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestSubscriber<Void> testSubscriber = new TestSubscriber<>();
    recentPageModel.getRecentPagesUpdatedObservable()
        .first()
        .subscribe(testSubscriber);

    recentPageModel.persistLatestPage(100, 300, 200);
    testSubscriber.awaitTerminalEvent();

    verify(bookmarksAdapter, times(1)).replaceRecentRangeWithPage(100, 300, 200);
  }
}
