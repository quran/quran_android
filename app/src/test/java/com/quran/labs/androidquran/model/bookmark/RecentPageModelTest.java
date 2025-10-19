package com.quran.labs.androidquran.model.bookmark;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;

import com.quran.data.model.bookmark.RecentPage;
import com.quran.labs.BaseTestExtension;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.TestScheduler;

public class RecentPageModelTest {
  private static final List<RecentPage> SAMPLE_RECENT_PAGES = new ArrayList<>();

  @Mock private BookmarksDBAdapter bookmarksAdapter;

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
    MockitoAnnotations.openMocks(RecentPageModelTest.this);
  }

  @Test
  public void testEmptyRecentPages() {
    when(bookmarksAdapter.getRecentPages()).thenReturn(new ArrayList<>());

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter);

    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .firstOrError()
        .subscribe(testObserver);
    BaseTestExtension.awaitTerminalEvent(testObserver);
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
    BaseTestExtension.awaitTerminalEvent(testObserver);
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
    BaseTestExtension.awaitTerminalEvent(testObserver);

    // after that, let's resubscribe and update with 2 other pages
    testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .take(3)
        .subscribe(testObserver);
    recentPageModel.updateLatestPage(51);
    recentPageModel.updateLatestPage(23);

    BaseTestExtension.awaitTerminalEvent(testObserver);
    testObserver.assertValues(49, 51, 23);
  }

  @Test
  public void testUpdateLatestPageWithSlowRecents() {
    final TestScheduler testScheduler = new TestScheduler();

    RecentPageModel recentPageModel = new RecentPageModel(bookmarksAdapter) {

      @NonNull
      @Override
      public Single<List<RecentPage>> getRecentPagesObservable() {
        // use an implementation of getRecentPagesObservable that delays the results until
        // testScheduler simulates the passing of 5 seconds (the timer time).
        return Single.timer(5, TimeUnit.SECONDS, testScheduler)
            .map(aLong -> SAMPLE_RECENT_PAGES)
            .subscribeOn(Schedulers.trampoline());
      }
    };

    TestObserver<Integer> testObserver = new TestObserver<>();
    recentPageModel.getLatestPageObservable()
        .take(2)
        .subscribe(testObserver);

    // write before the mock data from getRecentPagesObservable comes back
    recentPageModel.updateLatestPage(51);

    // now let's pretend the recent page came back - we expect that this page should be ignored.
    testScheduler.advanceTimeBy(5, TimeUnit.SECONDS);

    // and that another page afterwards was written
    recentPageModel.updateLatestPage(23);

    BaseTestExtension.awaitTerminalEvent(testObserver);
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
    BaseTestExtension.awaitTerminalEvent(testObserver);

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
    BaseTestExtension.awaitTerminalEvent(testObserver);

    verify(bookmarksAdapter, times(1)).replaceRecentRangeWithPage(100, 300, 200);
  }
}
