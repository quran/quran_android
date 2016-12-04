package com.quran.labs.androidquran.model.bookmark;

import android.support.annotation.UiThread;

import com.quran.labs.androidquran.dao.RecentPage;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

@Singleton
public class RecentPageModel {

  private final BookmarksDBAdapter bookmarksDBAdapter;
  private final Subject<Integer, Integer> lastPageSubject;

  private Subscription initialDataSubscription;
  private final Observable<Void> recentPagesUpdatedObservable;
  private final Subject<PersistRecentPagesRequest, PersistRecentPagesRequest> recentWriterSubject;

  @Inject
  public RecentPageModel(BookmarksDBAdapter adapter) {
    this.bookmarksDBAdapter = adapter;
    this.lastPageSubject = BehaviorSubject.create();
    this.recentWriterSubject = PublishSubject.create();

    recentPagesUpdatedObservable = this.recentWriterSubject.asObservable()
        .observeOn(Schedulers.io())
        .map(new Func1<PersistRecentPagesRequest, Void>() {
          @Override
          public Void call(PersistRecentPagesRequest update) {
            if (update.deleteRangeStart != null) {
              bookmarksDBAdapter.replaceRecentRangeWithPage(
                  update.deleteRangeStart, update.deleteRangeEnd, update.page);
            } else {
              bookmarksDBAdapter.addRecentPage(update.page);
            }
            return null;
          }
        }).share();

    // there needs to always be one subscriber in order for us to properly be able
    // to write updates to the database (even when no one else is subscribed).
    recentPagesUpdatedObservable.subscribe();

    // fetch the first set of "recent pages"
    initialDataSubscription = getRecentPagesObservable()
        // this is only to avoid serializing lastPageSubject
        .observeOn(AndroidSchedulers.mainThread())
        .doOnUnsubscribe(new Action0() {
          @Override
          public void call() {
            initialDataSubscription = null;
          }
        })
        .subscribe(new Action1<List<RecentPage>>() {
          @Override
          public void call(List<RecentPage> recentPages) {
            int page = recentPages.size() > 0 ? recentPages.get(0).page : Constants.NO_PAGE;
            lastPageSubject.onNext(page);
          }
        });
  }

  @UiThread
  public void updateLatestPage(int page) {
    if (initialDataSubscription != null) {
      // it is possible (though unlikely) for a page update to come in while we're still waiting
      // for the initial query of recent pages from the database. if so, unsubscribe from the initial
      // query since its data will be stale relative to this data.
      initialDataSubscription.unsubscribe();
    }
    lastPageSubject.onNext(page);
  }

  @UiThread
  public void persistLatestPage(int minimumPage, int maximumPage, int lastPage) {
    Integer min = minimumPage == maximumPage ? null : minimumPage;
    Integer max = min == null ? null : maximumPage;
    recentWriterSubject.onNext(new PersistRecentPagesRequest(lastPage, min, max));
  }

  /**
   * Returns an observable of the very latest pages visited
   *
   * Note that this stream never terminates, and will return pages even when they have not yet
   * been persisted to the database. This is basically a stream of every page as it gets visited.
   *
   * @return an Observable of the latest pages visited
   */
  public Observable<Integer> getLatestPageObservable() {
    return lastPageSubject.asObservable();
  }

  /**
   * Returns an observable of observing when recent pages change in the database
   *
   * Note that this stream never terminates, and will emit onNext when the database has been
   * updated. Note that writes to the database are typically batched, and as thus, this observable
   * will fire a lot less than {@link #getLatestPageObservable()}.
   *
   * @return an observable that receives events whenever recent pages is persisted
   */
  Observable<Void> getRecentPagesUpdatedObservable() {
    return recentPagesUpdatedObservable;
  }

  Observable<List<RecentPage>> getRecentPagesObservable() {
    return Observable.fromCallable(new Callable<List<RecentPage>>() {
      @Override
      public List<RecentPage> call() throws Exception {
        return bookmarksDBAdapter.getRecentPages();
      }
    }).subscribeOn(Schedulers.io());
  }

  private static class PersistRecentPagesRequest {
    final int page;
    final Integer deleteRangeStart;
    final Integer deleteRangeEnd;

    PersistRecentPagesRequest(int page, Integer deleteRangeStart, Integer deleteRangeEnd) {
      this.page = page;
      this.deleteRangeStart = deleteRangeStart;
      this.deleteRangeEnd = deleteRangeEnd;
    }
  }
}
