package com.quran.labs.androidquran.model.bookmark;

import android.support.annotation.UiThread;
import android.support.v4.util.Pair;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.RecentPage;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func3;
import rx.internal.operators.UnicastSubject;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

@Singleton
public class BookmarkModel {
  private final BookmarksDBAdapter bookmarksDBAdapter;
  private final Subject<Tag, Tag> tagPublishSubject;
  private final Subject<Void, Void> bookmarksPublishSubject;
  private final Subject<Void, Void> recentPagesPublishSubject;

  private final Subject<Pair<Integer, Integer>, Pair<Integer, Integer>> recentUpdatesSubject;

  @Inject
  public BookmarkModel(BookmarksDBAdapter adapter) {
    bookmarksDBAdapter = adapter;
    tagPublishSubject = PublishSubject.<Tag>create().toSerialized();
    recentPagesPublishSubject = PublishSubject.<Void>create().toSerialized();
    bookmarksPublishSubject = PublishSubject.<Void>create().toSerialized();

    // doesn't need to be serialized since onNext will only be called from the ui thread
    recentUpdatesSubject = UnicastSubject.create();
    recentUpdatesSubject
        .subscribeOn(Schedulers.io())
        .subscribe(new Action1<Pair<Integer, Integer>>() {
          @Override
          public void call(Pair<Integer, Integer> updates) {
            bookmarksDBAdapter.addRecentPage(updates.first, updates.second);
            recentPagesPublishSubject.onNext(null);
          }
        });
  }

  public Observable<Tag> tagsObservable() {
    return tagPublishSubject.asObservable();
  }

  public Observable<Void> recentPagesObservable() {
    return recentPagesPublishSubject.asObservable();
  }

  public Observable<Void> bookmarksObservable() {
    return bookmarksPublishSubject.asObservable();
  }

  public Observable<BookmarkData> getBookmarkDataObservable(final int sortOrder) {
    return Observable
        .zip(getTagsObservable(), getBookmarksObservable(sortOrder), getRecentPagesObservable(),
            new Func3<List<Tag>, List<Bookmark>, List<RecentPage>, BookmarkData>() {
              @Override
              public BookmarkData call(List<Tag> tags,
                                       List<Bookmark> bookmarks,
                                       List<RecentPage> recentPages) {
                return new BookmarkData(tags, bookmarks, recentPages);
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
        bookmarksDBAdapter.bulkDelete(tagsToDelete, bookmarksToDelete, untag);
        return null;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> addTagObservable(final String title) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        Long result = bookmarksDBAdapter.addTag(title);
        tagPublishSubject.onNext(new Tag(result, title));
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> updateTag(final Tag tag) {
    return Observable.fromCallable(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Boolean result = bookmarksDBAdapter.updateTag(tag.id, tag.name);
        if (result) {
          tagPublishSubject.onNext(new Tag(tag.id, tag.name));
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
        Boolean result = bookmarksDBAdapter.tagBookmarks(bookmarkIds, tagIds, deleteNonTagged);
        if (result) {
          bookmarksPublishSubject.onNext(null);
        }
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> safeAddBookmark(final Integer sura, final Integer ayah, final int page) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        long result = bookmarksDBAdapter.addBookmarkIfNotExists(sura, ayah, page);
        bookmarksPublishSubject.onNext(null);
        return result;
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<List<Tag>> getTagsObservable() {
    return Observable.fromCallable(new Callable<List<Tag>>() {
      @Override
      public List<Tag> call() throws Exception {
        return bookmarksDBAdapter.getTags();
      }
    }).subscribeOn(Schedulers.io());
  }

  public void addRecentPage(final int lastPage) {
    recentUpdatesSubject.onNext(new Pair<Integer, Integer>(null, lastPage));
  }

  @UiThread
  public void updateLastPage(final int fromPage, final int lastPage) {
    recentUpdatesSubject.onNext(new Pair<>(fromPage, lastPage));
  }

  public Observable<List<RecentPage>> getRecentPagesObservable() {
    return Observable.fromCallable(new Callable<List<RecentPage>>() {
      @Override
      public List<RecentPage> call() throws Exception {
        return bookmarksDBAdapter.getRecentPages();
      }
    }).subscribeOn(Schedulers.io());
  }

  private Observable<List<Bookmark>> getBookmarksObservable(final int sortOrder) {
    return Observable.fromCallable(new Callable<List<Bookmark>>() {
      @Override
      public List<Bookmark> call() throws Exception {
        return bookmarksDBAdapter.getBookmarks(sortOrder);
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
        return bookmarksDBAdapter.getBookmarkTagIds(bookmarkId);
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> getBookmarkId(final Integer sura, final Integer ayah, final int page) {
    return Observable.fromCallable(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        return bookmarksDBAdapter.getBookmarkId(sura, ayah, page);
      }
    }).subscribeOn(Schedulers.io());
  }

  public Observable<List<Bookmark>> getBookmarkedAyahsOnPageObservable(Integer... pages) {
    return Observable.from(pages)
        .map(new Func1<Integer, List<Bookmark>>() {
          @Override
          public List<Bookmark> call(Integer page) {
            return bookmarksDBAdapter.getBookmarkedAyahsOnPage(page);
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
            return new Pair<>(page, bookmarksDBAdapter.getBookmarkId(null, null, page) > 0);
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
              bookmarksDBAdapter.removeBookmark(bookmarkId);
              result = false;
            } else {
              bookmarksDBAdapter.addBookmark(sura, ayah, page);
              result = true;
            }
            bookmarksPublishSubject.onNext(null);
            return result;
          }
        }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> importBookmarksObservable(final BookmarkData data) {
    return Observable.fromCallable(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        Boolean result = bookmarksDBAdapter.importBookmarks(data);
        if (result) {
          bookmarksPublishSubject.onNext(null);
        }
        return result;
      }
    }).subscribeOn(Schedulers.io()).cache();
  }
}
