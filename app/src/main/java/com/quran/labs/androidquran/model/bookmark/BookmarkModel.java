package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

public class BookmarkModel {
  private static BookmarkModel sInstance;
  private final BookmarksDBAdapter mBookmarksDBAdapter;
  private final Subject<Tag, Tag> mTagPublishSubject;
  private final Subject<Void, Void> mBookmarksPublishSubject;

  public static synchronized BookmarkModel getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new BookmarkModel(context);
    }
    return sInstance;
  }

  private BookmarkModel(Context context) {
    mBookmarksDBAdapter = new BookmarksDBAdapter(context);
    mTagPublishSubject = PublishSubject.<Tag>create().toSerialized();
    mBookmarksPublishSubject = PublishSubject.<Void>create().toSerialized();
  }

  @VisibleForTesting
  public BookmarkModel(BookmarksDBAdapter adapter) {
    mBookmarksDBAdapter = adapter;
    mTagPublishSubject = PublishSubject.<Tag>create().toSerialized();
    mBookmarksPublishSubject = PublishSubject.<Void>create().toSerialized();
  }

  public Observable<Tag> tagsObservable() {
    return mTagPublishSubject.asObservable();
  }

  public Observable<Void> bookmarksObservable() {
    return mBookmarksPublishSubject.asObservable();
  }

  public Observable<BookmarkData> getBookmarkDataObservable(final int sortOrder) {
    return Observable
        .zip(getTagsObservable(), getBookmarksObservable(sortOrder),
            new Func2<List<Tag>, List<Bookmark>, BookmarkData>() {
              @Override
              public BookmarkData call(List<Tag> tags, List<Bookmark> bookmarks) {
                return new BookmarkData(tags, bookmarks);
              }
            })
            .subscribeOn(Schedulers.io());
  }

  public Observable<Void> removeItemsObservable(
      final List<QuranRow> itemsToRemoveRef) {
    return Observable.fromCallable(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        List<Long> tagsToDelete = new ArrayList<>();
        List<Long> bookmarksToDelete = new ArrayList<>();
        List<Pair<Long, Long>> untag = new ArrayList<>();
        for (int i = 0, size = itemsToRemoveRef.size(); i < size; i++) {
          QuranRow row = itemsToRemoveRef.get(i);
          if (row.isBookmarkHeader() && row.tagId > 0) {
            tagsToDelete.add(row.tagId);
          } else if (row.isBookmark() && row.bookmarkId > 0) {
            if (row.tagId > 0) {
              untag.add(new Pair<>(row.bookmarkId, row.tagId));
            } else {
              bookmarksToDelete.add(row.bookmarkId);
            }
          }
        }
        mBookmarksDBAdapter.bulkDelete(tagsToDelete, bookmarksToDelete, untag);
        return null;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> addTagObservable(final String title) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        Long result = mBookmarksDBAdapter.addTag(title);
        mTagPublishSubject.onNext(new Tag(result, title));
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> updateTag(final Tag tag) {
    return Observable.fromCallable(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Boolean result = mBookmarksDBAdapter.updateTag(tag.id, tag.name);
        if (result) {
          mTagPublishSubject.onNext(new Tag(tag.id, tag.name));
        }
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> updateBookmarkTags(final long[] bookmarkIds,
      final Set<Long> tagIds, final boolean deleteNonTagged) {
    return Observable.fromCallable(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Boolean result = mBookmarksDBAdapter.tagBookmarks(bookmarkIds, tagIds, deleteNonTagged);
        if (result) {
          mBookmarksPublishSubject.onNext(null);
        }
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> safeAddBookmark(final Integer sura, final Integer ayah, final int page) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        long result = mBookmarksDBAdapter.addBookmarkIfNotExists(sura, ayah, page);
        mBookmarksPublishSubject.onNext(null);
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<List<Tag>> getTagsObservable() {
    return Observable.fromCallable(new Callable<List<Tag>>() {
      @Override
      public List<Tag> call() throws Exception {
        return mBookmarksDBAdapter.getTags();
      }
    }).subscribeOn(Schedulers.io());
  }

  private Observable<List<Bookmark>> getBookmarksObservable(final int sortOrder) {
    return Observable.fromCallable(new Callable<List<Bookmark>>() {
      @Override
      public List<Bookmark> call() throws Exception {
        return mBookmarksDBAdapter.getBookmarks(sortOrder);
      }
    });
  }

  public Observable<List<Long>> getBookmarkTagIds(Observable<Long> bookmarkId) {
    return bookmarkId.filter(new Func1<Long, Boolean>() {
      @Override
      public Boolean call(Long bookmarkId) {
        return bookmarkId > 0;
      }
    }).map(new Func1<Long, List<Long>>() {
      @Override
      public List<Long> call(Long bookmarkId) {
        return mBookmarksDBAdapter.getBookmarkTagIds(bookmarkId);
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> getBookmarkId(final Integer sura, final Integer ayah, final int page) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        return mBookmarksDBAdapter.getBookmarkId(sura, ayah, page);
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<List<Bookmark>> getBookmarkedAyahsOnPageObservable(Integer... pages) {
    return Observable.from(pages)
        .map(new Func1<Integer, List<Bookmark>>() {
          @Override
          public List<Bookmark> call(Integer page) {
            return mBookmarksDBAdapter.getBookmarkedAyahsOnPage(page);
          }
        })
        .filter(new Func1<List<Bookmark>, Boolean>() {
          @Override
          public Boolean call(List<Bookmark> bookmarks) {
            return !bookmarks.isEmpty();
          }
        })
        .subscribeOn(Schedulers.io());
  }

  public Observable<Pair<Integer, Boolean>> getIsBookmarkedObservable(Integer... pages) {
    return Observable.from(pages)
        .map(new Func1<Integer, Pair<Integer, Boolean>>() {
          @Override
          public Pair<Integer, Boolean> call(Integer page) {
            return new Pair<>(page, mBookmarksDBAdapter.getBookmarkId(null, null, page) > 0);
          }
        }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> getIsBookmarkedObservable(
      final Integer sura, final Integer ayah, final int page) {
    return getBookmarkId(sura, ayah, page)
        .map(new Func1<Long, Boolean>() {
          @Override
          public Boolean call(Long bookmarkId) {
            return bookmarkId > 0;
          }
        }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> toggleBookmarkObservable(
      final Integer sura, final Integer ayah, final int page) {
    return getBookmarkId(sura, ayah, page)
        .map(new Func1<Long, Boolean>() {
          @Override
          public Boolean call(Long bookmarkId) {
            boolean result;
            if (bookmarkId > 0) {
              mBookmarksDBAdapter.removeBookmark(bookmarkId);
              result = false;
            } else {
              mBookmarksDBAdapter.addBookmark(sura, ayah, page);
              result = true;
            }
            mBookmarksPublishSubject.onNext(null);
            return result;
          }
        }).subscribeOn(Schedulers.io());
  }
}
