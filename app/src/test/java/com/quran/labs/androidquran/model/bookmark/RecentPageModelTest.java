package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.RecentPage;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.plugins.RxAndroidPlugins;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecentPageModelTest {
  private static final List<RecentPage> SAMPLE_RECENT_PAGES = new ArrayList<>();

  @Mock BookmarksDBAdapter bookmarksAdapter;

  @BeforeClass
  public static void setup() {
    RxAndroidPlugins.setInitMainThreadSchedulerHandler(schedulerCallable -> Schedulers.io());

    // sample recent pages
    long timestamp = System.currentTimeMillis();
    SAMPLE_RECENT_PAGES.add(new RecentPage(49, timestamp));
    SAMPLE_RECENT_PAGES.add(new RecentPage(100, timestamp - 10000));
  }

  @Before
  public void setupTest() {
    MockitoAnnotations.initMocks(RecentPageModelTest.this);
  }

  @Test
  public void testEmptyRecentPages() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(new ArrayList<>());

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .firstOrError()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValue(Constants.NO_PAGE);
  }

  @Test
  public void testNormalRecentPages() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .firstOrError()
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();
    testObserver.assertValue(49);
  }

  @Test
  public void testUpdateLatestPage() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    // make sure the mock data is received and onNext happens
    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .take(1)
        .subscribe(testObserver);
    testObserver.awaitTerminalEvent();

    // after that, let's resubscribe and update with 2 other pages
    testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .take(3)
        .subscribe(testObserver);
    recentPageModel.updateLatestPage(51);
    recentPageModel.updateLatestPage(23);

    testObserver.awaitTerminalEvent();
    testObserver.assertValues(49, 51, 23);
  }

  @Test
  public void testUpdateLatestPageWithSlowRecents() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(SAMPLE_RECENT_PAGES);

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .take(2)
        .subscribe(testObserver);

    // we immediately write 2 pages before the mock data has returned - in this case, we
    // should not get the test data (because otherwise, it would be out of order!)
    recentPageModel.updateLatestPage(51);
    recentPageModel.updateLatestPage(23);

    testObserver.awaitTerminalEvent();
    testObserver.assertValues(51, 23);
  }

  @Test
  public void testRecentPagesUpdated() {
    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Boolean> testObserver = new TestObserver<>();
    recentPageModel.getRecentPagesUpdatedObservable()
        .firstOrError()
        .subscribe(testObserver);

    recentPageModel.persistLatestPage(200, 200, 200);
    testObserver.awaitTerminalEvent();

    verify(bookmarksAdapter, times(1)).addRecentPage(200);
    verify(bookmarksAdapter, times(0)).replaceRecentRangeWithPage(anyInt(), anyInt(), anyInt());
  }

  @Test
  public void testRecentPagesUpdatedWithRange() {
    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Boolean> testObserver = new TestObserver<>();
    recentPageModel.getRecentPagesUpdatedObservable()
        .firstOrError()
        .subscribe(testObserver);

    recentPageModel.persistLatestPage(100, 300, 200);
    testObserver.awaitTerminalEvent();

    verify(bookmarksAdapter, times(1)).replaceRecentRangeWithPage(100, 300, 200);
  }
}
