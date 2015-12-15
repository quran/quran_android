package com.quran.labs.androidquran.ui.bookmark;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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
  private static final long BOOKMARKS_WITHOUT_TAGS_ID = -1;

  private static BookmarkPresenter sInstance;

  private final Context mAppContext;
  private final QuranSettings mQuranSettings;
  private final BookmarksDBAdapter mBookmarksDBAdapter;

  private int mSortOrder;
  private boolean mGroupByTags;
  private ResultHolder mCachedData;
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
    mAppContext = context;
    mQuranSettings = QuranSettings.getInstance(context);
    mBookmarksDBAdapter = new BookmarksDBAdapter(context);
    mSortOrder = mQuranSettings.getBookmarksSortOrder();
    mGroupByTags = mQuranSettings.getBookmarksGroupedByTags();
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
        .flatMap(new Func1<Long, Observable<ResultHolder>>() {
          @Override
          public Observable<ResultHolder> call(Long aLong) {
            return removeItemsObservable();
          }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<ResultHolder>() {
          @Override
          public void call(ResultHolder result) {
            mPendingRemoval = null;
            mCachedData = result;
            if (mFragment != null) {
              mFragment.onNewData(result.rows);
            }
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

  // TODO: fix this - use a dedicated method in mBookmarksDbAdapter that does removals in a tx
  private Observable<ResultHolder> removeItemsObservable() {
    return Observable.from(mItemsToRemove)
        .flatMap(new Func1<QuranRow, Observable<Boolean>>() {
          @Override
          public Observable<Boolean> call(QuranRow quranRow) {
            if (quranRow.isBookmarkHeader() && quranRow.tagId >= 0) {
              mBookmarksDBAdapter.removeTag(quranRow.tagId);
            } else if (quranRow.isBookmark() && quranRow.bookmarkId >= 0) {
              if (quranRow.tagId >= 0) {
                mBookmarksDBAdapter.untagBookmark(quranRow.bookmarkId, quranRow.tagId);
              } else {
                mBookmarksDBAdapter.removeBookmark(quranRow.bookmarkId);
              }
            }
            return Observable.just(true);
          }
        })
        .toList()
        .flatMap(new Func1<List<Boolean>, Observable<ResultHolder>>() {
          @Override
          public Observable<ResultHolder> call(List<Boolean> booleen) {
            return getBookmarkObservable(mSortOrder, mGroupByTags);
          }
        });
  }

  private Observable<ResultHolder> getBookmarkObservable(
      final int sortOrder, final boolean groupByTags) {
    return Observable
        .fromCallable(new Callable<BookmarkData>() {
          @Override
          public BookmarkData call() throws Exception {
            return fetchBookmarkData(sortOrder);
          }
        })
        .map(new Func1<BookmarkData, ResultHolder>() {
          @Override
          public ResultHolder call(BookmarkData bookmarkData) {
            List<QuranRow> rows = getBookmarkRows(bookmarkData, groupByTags);
            Map<Long, Tag> tagMap = generateTagMap(bookmarkData.getTags());
            return new ResultHolder(rows, tagMap);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  private void getBookmarks(final int sortOrder, final boolean groupByTags) {
    getBookmarkObservable(sortOrder, groupByTags)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<ResultHolder>() {
          @Override
          public void call(ResultHolder result) {
            // notify the ui if we're attached
            mCachedData = result;
            if (mFragment != null) {
              mFragment.onNewData(result.rows);
            }
          }
        });
  }

  private BookmarkData fetchBookmarkData(int sortOrder) {
    List<Bookmark> bookmarks = mBookmarksDBAdapter.getBookmarks(sortOrder);
    List<Tag> tags = mBookmarksDBAdapter.getTags();
    return new BookmarkData(tags, bookmarks);
  }

  private Map<Long, List<Bookmark>> generateTagsMapping(
      List<Tag> tags, List<Bookmark> bookmarks) {
    Set<Long> seenBookmarks = new HashSet<>();
    Map<Long, List<Bookmark>> tagMappings = new HashMap<>();
    for (int i = 0, tagSize = tags.size(); i < tagSize; i++) {
      long id = tags.get(i).id;
      List<Bookmark> matchingBookmarks = new ArrayList<>();
      for (int j = 0, bookmarkSize = bookmarks.size(); j < bookmarkSize; j++) {
        Bookmark bookmark = bookmarks.get(j);
        if (bookmark.tags.contains(id)) {
          matchingBookmarks.add(bookmark);
          seenBookmarks.add(bookmark.id);
        }
      }
      tagMappings.put(id, matchingBookmarks);
    }

    List<Bookmark> untaggedBookmarks = new ArrayList<>();
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (!seenBookmarks.contains(bookmark.id)) {
        untaggedBookmarks.add(bookmark);
      }
    }
    tagMappings.put(BOOKMARKS_WITHOUT_TAGS_ID, untaggedBookmarks);

    return tagMappings;
  }

  private Map<Long, Tag> generateTagMap(List<Tag> tags) {
    Map<Long, Tag> tagMap = new HashMap<>(tags.size());
    for (int i = 0, size = tags.size(); i < size; i++) {
      Tag tag = tags.get(i);
      tagMap.put(tag.id, tag);
    }
    return tagMap;
  }

  private List<QuranRow> getRowsSortedByTags(List<Tag> tags, List<Bookmark> bookmarks) {
    List<QuranRow> rows = new ArrayList<>();
    // sort by tags, alphabetical
    Map<Long, List<Bookmark>> tagsMapping = generateTagsMapping(tags, bookmarks);
    for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
      Tag tag = tags.get(i);
      rows.add(QuranRow.fromTag(tag));
      List<Bookmark> tagBookmarks = tagsMapping.get(tag.id);
      for (int j = 0, tagBookmarksSize = tagBookmarks.size(); j < tagBookmarksSize; j++) {
        rows.add(QuranRow.fromBookmark(mAppContext, tagBookmarks.get(j), tag.id));
      }
    }

    // add untagged bookmarks
    List<Bookmark> untagged = tagsMapping.get(BOOKMARKS_WITHOUT_TAGS_ID);
    if (untagged.size() > 0) {
      rows.add(QuranRow.fromNotTaggedHeader(mAppContext));
      for (int i = 0, untaggedSize = untagged.size(); i < untaggedSize; i++) {
        rows.add(QuranRow.fromBookmark(mAppContext, untagged.get(i)));
      }
    }
    return rows;
  }

  private List<QuranRow> getSortedRows(List<Bookmark> bookmarks) {
    List<QuranRow> rows = new ArrayList<>(bookmarks.size());
    List<Bookmark> ayahBookmarks = new ArrayList<>();

    // add the page bookmarks directly, save ayah bookmarks for later
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (bookmark.isPageBookmark()) {
        rows.add(QuranRow.fromBookmark(mAppContext, bookmark));
      } else {
        ayahBookmarks.add(bookmark);
      }
    }

    // add page bookmarks header if needed
    if (rows.size() > 0) {
      rows.add(0, QuranRow.fromPageBookmarksHeader(mAppContext));
    }

    // add ayah bookmarks if any
    if (ayahBookmarks.size() > 0) {
      rows.add(QuranRow.fromAyahBookmarksHeader(mAppContext));
      for (int i = 0, ayahBookmarksSize = ayahBookmarks.size(); i < ayahBookmarksSize; i++) {
        rows.add(QuranRow.fromBookmark(mAppContext, ayahBookmarks.get(i)));
      }
    }

    return rows;
  }

  private List<QuranRow> getBookmarkRows(BookmarkData data, boolean groupByTags) {
    List<QuranRow> rows;

    List<Tag> tags = data.getTags();
    List<Bookmark> bookmarks = data.getBookmarks();

    if (groupByTags) {
      rows = getRowsSortedByTags(tags, bookmarks);
    } else {
      rows = getSortedRows(bookmarks);
    }

    int lastPage = mQuranSettings.getLastPage();
    boolean showLastPage = lastPage != Constants.NO_PAGE_SAVED;
    if (showLastPage && (lastPage > Constants.PAGES_LAST || lastPage < Constants.PAGES_FIRST)) {
      showLastPage = false;
      Timber.w("Got invalid last saved page as %d", lastPage);
    }

    if (showLastPage) {
      rows.add(0, QuranRow.fromCurrentPageHeader(mAppContext));
      rows.add(1, QuranRow.fromCurrentPage(mAppContext, lastPage));
    }

    return rows;
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

  static class ResultHolder {
    public final List<QuranRow> rows;
    public final Map<Long, Tag> tagMap;

    public ResultHolder(List<QuranRow> rows, Map<Long, Tag> tagMap) {
      this.rows = rows;
      this.tagMap = tagMap;
    }
  }
}
