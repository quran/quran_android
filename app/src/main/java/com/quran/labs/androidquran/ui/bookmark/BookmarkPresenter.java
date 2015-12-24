package com.quran.labs.androidquran.ui.bookmark;

import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class BookmarkPresenter implements Presenter<BookmarksFragment> {
  public static final int DELAY_DELETION_DURATION_IN_MS = 4 * 1000; // 4 seconds

  private static BookmarkPresenter sInstance;

  private final BookmarkModel mBookmarkModel;
  private final QuranSettings mQuranSettings;

  private int mSortOrder;
  private boolean mGroupByTags;
  private BookmarkResult mCachedData;
  private BookmarksFragment mFragment;

  private Subscription mPendingRemoval;
  private List<QuranRow> mItemsToRemove;

  public synchronized static BookmarkPresenter getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new BookmarkPresenter(context);
    }
    return sInstance;
  }

  private BookmarkPresenter(Context context) {
    mQuranSettings = QuranSettings.getInstance(context);
    mBookmarkModel = BookmarkModel.getInstance(context);
    mSortOrder = mQuranSettings.getBookmarksSortOrder();
    mGroupByTags = mQuranSettings.getBookmarksGroupedByTags();
  }

  public int getSortOrder() {
    return mSortOrder;
  }

  public void setSortOrder(int sortOrder) {
    mSortOrder = sortOrder;
    mQuranSettings.setBookmarksSortOrder(mSortOrder);
    requestData();
  }

  public void toggleGroupByTags() {
    mGroupByTags = !mGroupByTags;
    mQuranSettings.setBookmarksGroupedByTags(mGroupByTags);
    requestData();
  }

  public boolean isGroupedByTags() {
    return mGroupByTags;
  }

  public boolean[] getContextualOperationsForItems(List<QuranRow> rows) {
    boolean[] result = new boolean[3];

    int headers = 0;
    int bookmarks = 0;
    for (int i = 0, rowsSize = rows.size(); i < rowsSize; i++) {
      QuranRow row = rows.get(i);
      if (row.isBookmarkHeader()) {
        headers++;
      } else if (row.isBookmark()) {
        bookmarks++;
      }
    }

    result[0] = headers == 1 && bookmarks == 0;
    result[1] = (headers + bookmarks) > 0;
    result[2] = headers == 0 && bookmarks > 0;
    return result;
  }

  public void requestData() {
    requestData(false);
  }

  public void requestData(boolean canCache) {
    if (canCache && mCachedData != null) {
      if (mFragment != null) {
        Timber.d("sending cached bookmark data");
        mFragment.onNewData(mCachedData.rows);
      }
    } else {
      Timber.d("requesting bookmark data from the database");
      getBookmarks(mSortOrder, mGroupByTags);
    }
  }

  public List<QuranRow> predictQuranListAfterDeletion(List<QuranRow> remove) {
    if (mCachedData != null) {
      List<QuranRow> placeholder = new ArrayList<>(mCachedData.rows.size() - remove.size());
      List<QuranRow> rows = mCachedData.rows;
      for (int i = 0, rowsSize = rows.size(); i < rowsSize; i++) {
        QuranRow row = rows.get(i);
        if (!remove.contains(row)) {
          placeholder.add(row);
        }
      }
      return placeholder;
    }
    return null;
  }

  public void deleteAfterSomeTime(List<QuranRow> remove) {
    if (mPendingRemoval != null) {
      // handle a new delete request when one is already happening by adding those items to delete
      // now and un-subscribing from the old request.
      if (mItemsToRemove != null) {
        remove.addAll(mItemsToRemove);
      }
      cancelDeletion();
    }

    mItemsToRemove = remove;
    mPendingRemoval = Observable.timer(DELAY_DELETION_DURATION_IN_MS, TimeUnit.MILLISECONDS)
        .flatMap(new Func1<Long, Observable<BookmarkResult>>() {
          @Override
          public Observable<BookmarkResult> call(Long aLong) {
            return removeItemsObservable();
          }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<BookmarkResult>() {
          @Override
          public void call(BookmarkResult result) {
            mPendingRemoval = null;
            mCachedData = result;
            if (mFragment != null) {
              mFragment.onNewData(result.rows);
            }
          }
        });
  }

  private Observable<BookmarkResult> removeItemsObservable() {
    return mBookmarkModel.removeItemsObservable(new ArrayList<>(mItemsToRemove))
        .flatMap(new Func1<Void, Observable<BookmarkResult>>() {
          @Override
          public Observable<BookmarkResult> call(Void aVoid) {
            return mBookmarkModel.getBookmarksListObservable(mSortOrder, mGroupByTags);
          }
        });
  }

  public void cancelDeletion() {
    if (mPendingRemoval != null) {
      mPendingRemoval.unsubscribe();
      mPendingRemoval = null;
      mItemsToRemove = null;
    }
  }

  private void getBookmarks(final int sortOrder, final boolean groupByTags) {
    mBookmarkModel.getBookmarksListObservable(sortOrder, groupByTags)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<BookmarkResult>() {
          @Override
          public void call(BookmarkResult result) {
            // notify the ui if we're attached
            mCachedData = result;
            if (mFragment != null) {
              mFragment.onNewData(result.rows);
            }
          }
        });
  }


  @Override
  public void bind(BookmarksFragment fragment) {
    mFragment = fragment;

    /* TODO: this should ideally be true in order for us to use the cached data if we have it.
     * disabled this for correctness, since a person using the app could easily go to a page,
     * mark a bookmark (or unmark one), and return, only to find the previous cached content.
     *
     * This will be fixed when BookmarkPresenter moves its bookmark specific logic into a separate
     * BookmarkModel class of sorts, which should also handle adding/tagging/etc. with that in
     * place, requestData(true) will be safe here, since if the data is stale, we'll get from the
     * database anyway.
     */
    requestData(false);
  }

  @Override
  public void unbind() {
    mFragment = null;
  }
}
