package com.quran.labs.androidquran.presenter.bookmark;

import com.f2prateek.rx.preferences.Preference;
import com.f2prateek.rx.preferences.RxSharedPreferences;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.bookmark.BookmarkResult;
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.ui.helpers.QuranRowFactory;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final BookmarkModel mBookmarkModel;
  private final QuranSettings mQuranSettings;

  private int mSortOrder;
  private boolean mGroupByTags;
  private BookmarkResult mCachedData;
  private BookmarksFragment mFragment;
  private ArabicDatabaseUtils mArabicDatabaseUtils;

  private boolean mIsRtl;
  private Subscription mPendingRemoval;
  private List<QuranRow> mItemsToRemove;

  public synchronized static BookmarkPresenter getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new BookmarkPresenter(context);
    }
    return sInstance;
  }

  private BookmarkPresenter(Context context) {
    mAppContext = context.getApplicationContext();
    mQuranSettings = QuranSettings.getInstance(context);
    mBookmarkModel = BookmarkModel.getInstance(context);
    mSortOrder = mQuranSettings.getBookmarksSortOrder();
    mGroupByTags = mQuranSettings.getBookmarksGroupedByTags();
    try {
      mArabicDatabaseUtils = ArabicDatabaseUtils.getInstance(context);
    } catch (Exception e) {
      mArabicDatabaseUtils = null;
    }
    subscribeToChanges();
  }

  @VisibleForTesting
  BookmarkPresenter(Context context, QuranSettings settings,
      BookmarkModel bookmarkModel, boolean subscribeToChanges) {
    mAppContext = context.getApplicationContext();
    mQuranSettings = settings;
    mBookmarkModel = bookmarkModel;
    mSortOrder = mQuranSettings.getBookmarksSortOrder();
    mGroupByTags = mQuranSettings.getBookmarksGroupedByTags();
    if (subscribeToChanges) {
      subscribeToChanges();
    }
  }

  private void subscribeToChanges() {
    RxSharedPreferences prefs = RxSharedPreferences.create(
        PreferenceManager.getDefaultSharedPreferences(mAppContext));
    Preference<Integer> lastPage = prefs.getInteger(Constants.PREF_LAST_PAGE);
    Observable.merge(mBookmarkModel.tagsObservable(),
        mBookmarkModel.bookmarksObservable(), lastPage.asObservable())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Object>() {
          @Override
          public void call(Object o) {
            if (mFragment != null) {
              requestData(false);
            } else {
              mCachedData = null;
            }
          }
        });
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

  public boolean shouldShowInlineTags() {
    return !mGroupByTags;
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
        mFragment.onNewData(mCachedData);
      }
    } else {
      Timber.d("requesting bookmark data from the database");
      getBookmarks(mSortOrder, mGroupByTags);
    }
  }

  public BookmarkResult predictQuranListAfterDeletion(List<QuranRow> remove) {
    if (mCachedData != null) {
      List<QuranRow> placeholder = new ArrayList<>(mCachedData.rows.size() - remove.size());
      List<QuranRow> rows = mCachedData.rows;
      List<Long> removedTags = new ArrayList<>();
      for (int i = 0, rowsSize = rows.size(); i < rowsSize; i++) {
        QuranRow row = rows.get(i);
        if (!remove.contains(row)) {
          placeholder.add(row);
        }
      }

      for (int i = 0, removedSize = remove.size(); i < removedSize; i++) {
        QuranRow row = remove.get(i);
        if (row.isHeader() && row.tagId > 0){
          removedTags.add(row.tagId);
        }
      }

      Map<Long, Tag> tagMap;
      if (removedTags.isEmpty()) {
        tagMap = mCachedData.tagMap;
      } else {
        tagMap = new HashMap<>(mCachedData.tagMap);
        for (int i = 0, removedTagsSize = removedTags.size(); i < removedTagsSize; i++) {
          Long tagId = removedTags.get(i);
          tagMap.remove(tagId);
        }
      }
      return new BookmarkResult(placeholder, tagMap);
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
              mFragment.onNewData(result);
            }
          }
        });
  }

  private Observable<BookmarkResult> removeItemsObservable() {
    return mBookmarkModel.removeItemsObservable(new ArrayList<>(mItemsToRemove))
        .flatMap(new Func1<Void, Observable<BookmarkResult>>() {
          @Override
          public Observable<BookmarkResult> call(Void aVoid) {
            return getBookmarksListObservable(mSortOrder, mGroupByTags);
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

  private Observable<BookmarkData> getBookmarksWithAyatObservable(int sortOrder) {
    return mBookmarkModel.getBookmarkDataObservable(sortOrder)
        .map(new Func1<BookmarkData, BookmarkData>() {
          @Override
          public BookmarkData call(BookmarkData bookmarkData) {
            try {
              return new BookmarkData(bookmarkData.getTags(),
                  mArabicDatabaseUtils.hydrateAyahText(bookmarkData.getBookmarks()));
            } catch (Exception e) {
              return bookmarkData;
            }
          }
        });
  }

  @VisibleForTesting
  Observable<BookmarkResult> getBookmarksListObservable(
      int sortOrder, final boolean groupByTags) {
    return getBookmarksWithAyatObservable(sortOrder)
        .map(new Func1<BookmarkData, BookmarkResult>() {
          @Override
          public BookmarkResult call(BookmarkData bookmarkData) {
            List<QuranRow> rows = getBookmarkRows(bookmarkData, groupByTags);
            Map<Long, Tag> tagMap = generateTagMap(bookmarkData.getTags());
            return new BookmarkResult(rows, tagMap);
          }
        })
        .subscribeOn(Schedulers.io());
  }

  private void getBookmarks(final int sortOrder, final boolean groupByTags) {
    getBookmarksListObservable(sortOrder, groupByTags)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<BookmarkResult>() {
          @Override
          public void call(BookmarkResult result) {
            // notify the ui if we're attached
            mCachedData = result;
            if (mFragment != null) {
              if (mPendingRemoval != null && mItemsToRemove != null) {
                mFragment.onNewData(predictQuranListAfterDeletion(mItemsToRemove));
              } else {
                mFragment.onNewData(result);
              }
            }
          }
        });
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
      rows.add(0, QuranRowFactory.fromCurrentPageHeader(mAppContext));
      rows.add(1, QuranRowFactory.fromCurrentPage(mAppContext, lastPage));
    }

    return rows;
  }

  private List<QuranRow> getRowsSortedByTags(List<Tag> tags, List<Bookmark> bookmarks) {
    List<QuranRow> rows = new ArrayList<>();
    // sort by tags, alphabetical
    Map<Long, List<Bookmark>> tagsMapping = generateTagsMapping(tags, bookmarks);
    for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
      Tag tag = tags.get(i);
      rows.add(QuranRowFactory.fromTag(tag));
      List<Bookmark> tagBookmarks = tagsMapping.get(tag.id);
      for (int j = 0, tagBookmarksSize = tagBookmarks.size(); j < tagBookmarksSize; j++) {
        rows.add(QuranRowFactory.fromBookmark(mAppContext, tagBookmarks.get(j), tag.id));
      }
    }

    // add untagged bookmarks
    List<Bookmark> untagged = tagsMapping.get(BOOKMARKS_WITHOUT_TAGS_ID);
    if (untagged.size() > 0) {
      rows.add(QuranRowFactory.fromNotTaggedHeader(mAppContext));
      for (int i = 0, untaggedSize = untagged.size(); i < untaggedSize; i++) {
        rows.add(QuranRowFactory.fromBookmark(mAppContext, untagged.get(i)));
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
        rows.add(QuranRowFactory.fromBookmark(mAppContext, bookmark));
      } else {
        ayahBookmarks.add(bookmark);
      }
    }

    // add page bookmarks header if needed
    if (rows.size() > 0) {
      rows.add(0, QuranRowFactory.fromPageBookmarksHeader(mAppContext));
    }

    // add ayah bookmarks if any
    if (ayahBookmarks.size() > 0) {
      rows.add(QuranRowFactory.fromAyahBookmarksHeader(mAppContext));
      for (int i = 0, ayahBookmarksSize = ayahBookmarks.size(); i < ayahBookmarksSize; i++) {
        rows.add(QuranRowFactory.fromBookmark(mAppContext, ayahBookmarks.get(i)));
      }
    }

    return rows;
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


  @Override
  public void bind(BookmarksFragment fragment) {
    mFragment = fragment;
    boolean isRtl = mQuranSettings.isArabicNames() || QuranUtils.isRtl();
    if (isRtl == mIsRtl) {
      requestData(true);
    } else {
      // don't use the cache if rtl changed
      mIsRtl = isRtl;
      requestData(false);
    }
  }

  @Override
  public void unbind(BookmarksFragment fragment) {
    if (fragment == mFragment) {
      mFragment = null;
    }
  }
}
